package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.ClaimStatus;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimAdjudicationIntegrationTest extends AbstractIntegrationTest {

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

    private ClaimResponse submitValidateAdjudicate(String memberId, String npi,
                                                    LocalDate serviceDate, String cpt,
                                                    BigDecimal amount) {
        ClaimResponse submitted = submit(memberId, npi, serviceDate, cpt, amount);
        validate(submitted.claimId());
        return adjudicate(submitted.claimId());
    }

    @Test
    void adjudicate_cleanApprove_inNetwork_cpt99213_setsAllowed125() {
        ClaimResponse result = submitValidateAdjudicate("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));

        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.allowedAmount()).isEqualByComparingTo("125.00");
    }

    @Test
    void adjudicate_outOfNetworkPpoGold_cpt99213_setsAllowed75() {
        // M001 is PPO_GOLD (accepts OON at validation), NPI 5678901234 is out-of-network.
        // 99213 base $125 * 0.60 = $75.00.
        ClaimResponse result = submitValidateAdjudicate("M001", "5678901234",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));

        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.allowedAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void adjudicate_unknownCpt_appliesEightyPercentOfBilled() {
        ClaimResponse result = submitValidateAdjudicate("M001", "1234567890",
                LocalDate.now().minusDays(5), "99999", new BigDecimal("200.00"));

        assertThat(result.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.allowedAmount()).isEqualByComparingTo("160.00");
    }

    @Test
    void adjudicate_duplicateDenial_priorApproved_deniesWithCarc18() {
        LocalDate sd = LocalDate.now().minusDays(5);
        submitValidateAdjudicate("M001", "1234567890", sd, "99213", new BigDecimal("150.00"));

        ClaimResponse duplicate = submitValidateAdjudicate("M001", "1234567890", sd,
                "99213", new BigDecimal("150.00"));

        assertThat(duplicate.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(duplicate.reasonCodes()).contains("CARC-18");
    }

    @Test
    void adjudicate_priorAuthCpt70553_pendsStatusStaysValidated_auditRowWithCarc197() {
        ClaimResponse result = submitValidateAdjudicate("M001", "1234567890",
                LocalDate.now().minusDays(5), "70553", new BigDecimal("2000.00"));

        assertThat(result.status()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(result.reasonCodes()).contains("CARC-197");
        assertThat(result.message()).contains("70553");

        String reasonCodes = jdbc.queryForObject(
                "SELECT reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? AND previous_status = 'VALIDATED' "
                        + "AND new_status = 'VALIDATED'",
                String.class, result.claimId());
        assertThat(reasonCodes).isEqualTo("CARC-197");
    }

    @Test
    void adjudicate_coverageLimitExceeded_hmoSilver_deniesCarc119() {
        // Seed an approved claim at $49,999 for M002 (HMO_SILVER, cap $50,000).
        LocalDate sd = LocalDate.now().minusDays(30);
        ClaimResponse seeded = submitValidateAdjudicate("M002", "1234567890", sd,
                "99215", new BigDecimal("49999.00"));
        assertThat(seeded.status()).isEqualTo(ClaimStatus.APPROVED);
        // Manually bump the allowed amount to $49,999 so the running-year sum reaches it.
        jdbc.update(
                "UPDATE claims SET allowed_amount = 49999.00 WHERE claim_id = ?",
                seeded.claimId());

        // New claim: 99215 base $245 in-network → estimate $245 adds to $49,999 = $50,244 > $50,000.
        ClaimResponse result = submitValidateAdjudicate("M002", "1234567890",
                LocalDate.now().minusDays(1), "99215", new BigDecimal("500.00"));

        assertThat(result.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(result.reasonCodes()).contains("CARC-119");
    }

    @Test
    void adjudicate_shortCircuit_duplicateBeatsPriorAuth_onlyCarc18InReasonCodes() {
        // Seed an APPROVED prior claim with prior-auth CPT 70553.
        // Need to force it to APPROVED — 70553 would PEND on the happy path,
        // so we write directly to the DB to get it into APPROVED state.
        LocalDate sd = LocalDate.now().minusDays(5);
        ClaimResponse submitted = submit("M001", "1234567890", sd, "70553",
                new BigDecimal("2000.00"));
        jdbc.update(
                "UPDATE claims SET status = 'APPROVED', allowed_amount = 1800.00 "
                        + "WHERE claim_id = ?", submitted.claimId());

        // New matching claim. DuplicateClaimRule (order 10) should fire before
        // PriorAuthRule (order 20), so the response has only CARC-18, not CARC-197.
        ClaimResponse duplicate = submitValidateAdjudicate("M001", "1234567890", sd,
                "70553", new BigDecimal("2000.00"));

        assertThat(duplicate.status()).isEqualTo(ClaimStatus.DENIED);
        assertThat(duplicate.reasonCodes()).containsExactly("CARC-18");
        assertThat(duplicate.reasonCodes()).doesNotContain("CARC-197");
    }

    @Test
    void adjudicate_onNonValidatedClaim_returns409() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        // status is SUBMITTED, not VALIDATED

        given()
                .when()
                .post("/api/v1/claims/{id}/adjudicate", submitted.claimId())
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Conflict"))
                .body("message", org.hamcrest.Matchers.containsString("SUBMITTED"));
    }
}
