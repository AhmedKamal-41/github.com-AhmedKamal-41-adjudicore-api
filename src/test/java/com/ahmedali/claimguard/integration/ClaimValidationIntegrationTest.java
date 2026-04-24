package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.ClaimStatus;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimValidationIntegrationTest extends AbstractIntegrationTest {

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
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(201)
                .extract().as(ClaimResponse.class);
    }

    private ClaimResponse validate(String claimId) {
        return given()
                .when()
                .post("/api/v1/claims/{id}/validate", claimId)
                .then()
                .extract().as(ClaimResponse.class);
    }

    @Test
    void validate_happyPath_m001_inNetwork_cpt99213_transitionsToValidated() {
        ClaimResponse submitted = submit("M001", "1234567890", LocalDate.now().minusDays(5),
                "99213", new BigDecimal("150.00"));

        ClaimResponse validated = validate(submitted.claimId());

        assertThat(validated.status()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(validated.reasonCodes()).isNullOrEmpty();
    }

    @Test
    void validate_expiredMemberM005_returnsRejectedWithCarc27AndReadableMessage() {
        ClaimResponse submitted = submit("M005", "1234567890", LocalDate.now().minusDays(5),
                "99213", new BigDecimal("150.00"));

        ClaimResponse rejected = validate(submitted.claimId());

        assertThat(rejected.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(rejected.reasonCodes()).contains("CARC-27");
        assertThat(rejected.message()).contains("Expenses incurred after coverage terminated");
    }

    @Test
    void validate_outOfNetworkProviderWithHmoSilverMember_rejectedWithCarc242() {
        // M002 is HMO_SILVER; NPI 4567890123 is out-of-network.
        ClaimResponse submitted = submit("M002", "4567890123", LocalDate.now().minusDays(5),
                "99213", new BigDecimal("150.00"));

        ClaimResponse rejected = validate(submitted.claimId());

        assertThat(rejected.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(rejected.reasonCodes()).contains("CARC-242");
    }

    @Test
    void validate_multipleSimultaneousFailures_aggregatesAllRejectCodes() {
        // M005 eligibility ended 2023-12-31. Service 400 days before today forces
        // CARC-29 (>365 day filing window) AND CARC-27 (post-eligibility) to fire.
        ClaimResponse submitted = submit("M005", "1234567890", LocalDate.now().minusDays(400),
                "99213", new BigDecimal("150.00"));

        ClaimResponse rejected = validate(submitted.claimId());

        assertThat(rejected.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(rejected.reasonCodes()).contains("CARC-27");
        assertThat(rejected.reasonCodes()).contains("CARC-29");
    }

    @Test
    void validate_onClaimNotInSubmittedState_returns409ErrorResponse() {
        ClaimResponse submitted = submit("M001", "1234567890", LocalDate.now().minusDays(5),
                "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());

        given()
                .when()
                .post("/api/v1/claims/{id}/validate", submitted.claimId())
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Conflict"))
                .body("message", org.hamcrest.Matchers.containsString("VALIDATED"));
    }

    @Test
    void validate_nonexistentClaim_returns404ErrorResponse() {
        given()
                .when()
                .post("/api/v1/claims/{id}/validate", "CLM-20990101-MISSING")
                .then()
                .statusCode(404)
                .body("message", org.hamcrest.Matchers.containsString("Claim not found"));
    }

    @Test
    void validate_rejectAuditRow_storesCommaJoinedReasonCodes() {
        ClaimResponse submitted = submit("M005", "1234567890", LocalDate.now().minusDays(400),
                "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());

        String reasonCodes = jdbc.queryForObject(
                "SELECT reason_codes FROM claim_audit_log "
                        + "WHERE claim_id = ? AND new_status = 'REJECTED'",
                String.class, submitted.claimId());

        assertThat(reasonCodes).matches("^CARC-[A-Z0-9]+(,CARC-[A-Z0-9]+)+$");
        assertThat(reasonCodes).contains("CARC-27");
        assertThat(reasonCodes).contains("CARC-29");
    }
}
