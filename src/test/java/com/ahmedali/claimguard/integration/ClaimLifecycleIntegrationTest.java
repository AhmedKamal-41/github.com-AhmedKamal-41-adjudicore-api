package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.ClaimStatus;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimLifecycleIntegrationTest extends AbstractIntegrationTest {

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

    private List<Map<String, Object>> auditRows(String claimId) {
        return jdbc.queryForList(
                "SELECT previous_status, new_status, reason_codes "
                        + "FROM claim_audit_log WHERE claim_id = ? "
                        + "ORDER BY changed_at ASC",
                claimId);
    }

    @Test
    void fullApprove_threeAuditRowsInCorrectSequence() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());
        ClaimResponse adjudicated = adjudicate(submitted.claimId());

        assertThat(adjudicated.status()).isEqualTo(ClaimStatus.APPROVED);
        List<Map<String, Object>> rows = auditRows(submitted.claimId());
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("previous_status", null);
        assertThat(rows.get(0)).containsEntry("new_status", "SUBMITTED");
        assertThat(rows.get(1)).containsEntry("previous_status", "SUBMITTED");
        assertThat(rows.get(1)).containsEntry("new_status", "VALIDATED");
        assertThat(rows.get(2)).containsEntry("previous_status", "VALIDATED");
        assertThat(rows.get(2)).containsEntry("new_status", "APPROVED");
    }

    @Test
    void fullDeny_duplicate_priorClaimHas3RowsPlusNewClaimHas3Rows() {
        LocalDate sd = LocalDate.now().minusDays(5);
        ClaimResponse first = submit("M001", "1234567890", sd, "99213",
                new BigDecimal("150.00"));
        validate(first.claimId());
        adjudicate(first.claimId());

        ClaimResponse second = submit("M001", "1234567890", sd, "99213",
                new BigDecimal("150.00"));
        validate(second.claimId());
        ClaimResponse secondAdj = adjudicate(second.claimId());

        assertThat(secondAdj.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(secondAdj.reasonCodes()).contains("CARC-18");

        List<Map<String, Object>> firstRows = auditRows(first.claimId());
        List<Map<String, Object>> secondRows = auditRows(second.claimId());

        assertThat(firstRows).hasSize(3);
        assertThat(firstRows.get(2)).containsEntry("new_status", "APPROVED");

        assertThat(secondRows).hasSize(3);
        assertThat(secondRows.get(2)).containsEntry("previous_status", "VALIDATED");
        assertThat(secondRows.get(2)).containsEntry("new_status", "DENIED");
        assertThat(secondRows.get(2)).containsEntry("reason_codes", "CARC-18");
    }

    @Test
    void fullPend_endsValidated_withPendAuditRow() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "70553", new BigDecimal("2000.00"));
        validate(submitted.claimId());
        ClaimResponse adjudicated = adjudicate(submitted.claimId());

        assertThat(adjudicated.status()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(adjudicated.reasonCodes()).contains("CARC-197");

        List<Map<String, Object>> rows = auditRows(submitted.claimId());
        assertThat(rows).hasSize(3);
        // PEND row: VALIDATED → VALIDATED (no status change, but audit recorded)
        assertThat(rows.get(2)).containsEntry("previous_status", "VALIDATED");
        assertThat(rows.get(2)).containsEntry("new_status", "VALIDATED");
        assertThat(rows.get(2)).containsEntry("reason_codes", "CARC-197");
    }

    @Test
    void fullReject_noAdjudicateAuditRowExists() {
        ClaimResponse submitted = submit("M005", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        ClaimResponse rejected = validate(submitted.claimId());

        assertThat(rejected.status()).isEqualTo(ClaimStatus.REJECTED);

        List<Map<String, Object>> rows = auditRows(submitted.claimId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsEntry("new_status", "REJECTED");

        Integer adjudicateRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log "
                        + "WHERE claim_id = ? AND previous_status = 'VALIDATED'",
                Integer.class, submitted.claimId());
        assertThat(adjudicateRows).isZero();
    }

    @Test
    void attemptedAdjudicateOnRejected_returns409_auditLogUnchanged() {
        ClaimResponse submitted = submit("M005", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());

        Integer beforeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log WHERE claim_id = ?",
                Integer.class, submitted.claimId());

        given()
                .when()
                .post("/api/v1/claims/{id}/adjudicate", submitted.claimId())
                .then()
                .statusCode(409);

        Integer afterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log WHERE claim_id = ?",
                Integer.class, submitted.claimId());

        assertThat(afterCount).isEqualTo(beforeCount);
    }
}
