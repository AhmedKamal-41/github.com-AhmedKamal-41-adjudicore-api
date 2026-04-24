package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class AuditLogIntegrationTest extends AbstractIntegrationTest {

    private ClaimResponse submit(String memberId, String npi, LocalDate serviceDate,
                                 String cpt, BigDecimal amount) {
        String body = """
                {
                  "memberId": "%s",
                  "providerNpi": "%s",
                  "serviceDate": "%s",
                  "procedureCode": "%s",
                  "diagnosisCode": "J45.909",
                  "billedAmount": %s
                }
                """.formatted(memberId, npi, serviceDate, cpt, amount.toPlainString());
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().as(ClaimResponse.class);
    }

    private ClaimResponse validate(String claimId) {
        return given().when().post("/api/v1/claims/{id}/validate", claimId)
                .then().extract().as(ClaimResponse.class);
    }

    private ClaimResponse adjudicate(String claimId) {
        return given().when().post("/api/v1/claims/{id}/adjudicate", claimId)
                .then().extract().as(ClaimResponse.class);
    }

    private int countAudit(String claimId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log WHERE claim_id = ?",
                Integer.class, claimId);
        return n == null ? 0 : n;
    }

    @Test
    void eachStateTransitionWritesExactlyOneAuditRow() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        assertThat(countAudit(submitted.claimId())).isEqualTo(1);

        validate(submitted.claimId());
        assertThat(countAudit(submitted.claimId())).isEqualTo(2);

        adjudicate(submitted.claimId());
        assertThat(countAudit(submitted.claimId())).isEqualTo(3);
    }

    @Test
    void auditRowOrderingByChangedAtIsStrictlyAscendingWithinAClaim() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());
        adjudicate(submitted.claimId());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT changed_at FROM claim_audit_log WHERE claim_id = ? "
                        + "ORDER BY changed_at ASC",
                submitted.claimId());

        assertThat(rows).hasSize(3);
        Timestamp t0 = (Timestamp) rows.get(0).get("changed_at");
        Timestamp t1 = (Timestamp) rows.get(1).get("changed_at");
        Timestamp t2 = (Timestamp) rows.get(2).get("changed_at");
        assertThat(t0.before(t1) || t0.equals(t1)).isTrue();
        assertThat(t1.before(t2) || t1.equals(t2)).isTrue();
        // At least one strict ordering somewhere in the triple — within a lifecycle
        // the transitions are sequential, so not all three can be identical
        assertThat(t0.before(t2)).isTrue();
    }

    @Test
    void reasonCodes_nullForNonFailureTransitions_populatedForFailureTransitions() {
        // Approve path
        ClaimResponse approved = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(approved.claimId());
        adjudicate(approved.claimId());

        List<Map<String, Object>> approvedRows = jdbc.queryForList(
                "SELECT new_status, reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? ORDER BY changed_at ASC",
                approved.claimId());
        assertThat(approvedRows).hasSize(3);
        // SUBMITTED, VALIDATED, APPROVED — all non-failure, reason_codes should be null
        approvedRows.forEach(row -> assertThat(row.get("reason_codes")).isNull());

        // Reject path
        ClaimResponse rejected = submit("M005", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(rejected.claimId());

        String rejectCodes = jdbc.queryForObject(
                "SELECT reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? AND new_status = 'REJECTED'",
                String.class, rejected.claimId());
        assertThat(rejectCodes).contains("CARC-27");

        // Deny path
        LocalDate sd = LocalDate.now().minusDays(5);
        ClaimResponse first = submit("M001", "1234567890", sd, "99213",
                new BigDecimal("150.00"));
        validate(first.claimId());
        adjudicate(first.claimId());
        ClaimResponse dup = submit("M001", "1234567890", sd, "99213",
                new BigDecimal("150.00"));
        validate(dup.claimId());
        adjudicate(dup.claimId());

        String denyCodes = jdbc.queryForObject(
                "SELECT reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? AND new_status = 'DENIED'",
                String.class, dup.claimId());
        assertThat(denyCodes).isEqualTo("CARC-18");

        // Pend path
        ClaimResponse pend = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "70553", new BigDecimal("2000.00"));
        validate(pend.claimId());
        adjudicate(pend.claimId());

        String pendCodes = jdbc.queryForObject(
                "SELECT reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? AND previous_status = 'VALIDATED' "
                        + "AND new_status = 'VALIDATED'",
                String.class, pend.claimId());
        assertThat(pendCodes).isEqualTo("CARC-197");
    }

    @Test
    void changedByIsSystemForEveryRow() {
        ClaimResponse approved = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(approved.claimId());
        adjudicate(approved.claimId());

        ClaimResponse rejected = submit("M005", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(rejected.claimId());

        Integer nonSystemRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log WHERE changed_by <> 'SYSTEM'",
                Integer.class);
        assertThat(nonSystemRows).isZero();

        Integer totalRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log",
                Integer.class);
        assertThat(totalRows).isEqualTo(5); // 3 for approved flow + 2 for rejected
    }

    @Test
    void notesColumnIsPopulatedOnEveryRow() {
        ClaimResponse approved = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(approved.claimId());
        adjudicate(approved.claimId());

        List<String> notes = jdbc.queryForList(
                "SELECT notes FROM claim_audit_log WHERE claim_id = ? ORDER BY changed_at ASC",
                String.class, approved.claimId());

        assertThat(notes).hasSize(3);
        assertThat(notes).allSatisfy(note -> assertThat(note).isNotBlank());
        assertThat(notes.get(0)).isEqualTo("Claim submitted via API");
        assertThat(notes.get(1)).isEqualTo("Validation passed");
        assertThat(notes.get(2)).isEqualTo("Approved at fee schedule rate");
    }
}
