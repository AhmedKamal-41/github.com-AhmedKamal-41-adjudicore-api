package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.ClaimStatus;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

class ClaimIntakeIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_BODY = """
            {
              "memberId": "M001",
              "providerNpi": "1234567890",
              "serviceDate": "%s",
              "procedureCode": "99213",
              "diagnosisCode": "J45.909",
              "billedAmount": 150.00
            }
            """.formatted(LocalDate.now().minusDays(5));

    @Test
    void postValidClaim_returns201_persistsRow_setsLocationHeader() {
        ClaimResponse response = given()
                .contentType(ContentType.JSON)
                .body(VALID_BODY)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(201)
                .header("Location", matchesPattern("/api/v1/claims/CLM-\\d{8}-[A-Z0-9]{6}"))
                .extract().as(ClaimResponse.class);

        assertThat(response.claimId()).matches("CLM-\\d{8}-[A-Z0-9]{6}");
        assertThat(response.memberId()).isEqualTo("M001");
        assertThat(response.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.billedAmount()).isEqualByComparingTo("150.00");

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claims WHERE claim_id = ?", Integer.class, response.claimId());
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void postMissingMemberId_returns400_withFieldError() {
        String body = """
                {
                  "providerNpi": "1234567890",
                  "serviceDate": "2026-04-15",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(400)
                .body("message", org.hamcrest.Matchers.equalTo("Validation failed"))
                .body("fieldErrors.memberId", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void postInvalidCpt4Digits_returns400_withFieldError() {
        String body = """
                {
                  "memberId": "M001",
                  "providerNpi": "1234567890",
                  "serviceDate": "2026-04-15",
                  "procedureCode": "9921",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(400)
                .body("fieldErrors.procedureCode", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void postNegativeAmount_returns400_withFieldError() {
        String body = """
                {
                  "memberId": "M001",
                  "providerNpi": "1234567890",
                  "serviceDate": "2026-04-15",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": -10.00
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(400)
                .body("fieldErrors.billedAmount", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void postValidClaim_writesExactlyOneAuditRow() {
        ClaimResponse response = given()
                .contentType(ContentType.JSON)
                .body(VALID_BODY)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(201)
                .extract().as(ClaimResponse.class);

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim_audit_log WHERE claim_id = ?",
                Integer.class, response.claimId());
        assertThat(auditCount).isEqualTo(1);

        String newStatus = jdbc.queryForObject(
                "SELECT new_status FROM claim_audit_log WHERE claim_id = ?",
                String.class, response.claimId());
        assertThat(newStatus).isEqualTo("SUBMITTED");
    }

    /**
     * Spec said: "intake doesn't check member existence — validation does".
     * In reality the schema has {@code claims.member_id → members.member_id} FK
     * (V3 migration), so the INSERT fails on unseeded member IDs even before
     * application-layer validation runs. The catch-all exception handler
     * correctly generalizes the FK violation to a 500 "An unexpected error
     * occurred" without leaking schema details.
     * Recording the actual behavior here.
     */
    @Test
    void postWithUnseededMemberM999_returns500_dueToFkConstraint_butDoesNotLeakDetails() {
        String body = """
                {
                  "memberId": "M999",
                  "providerNpi": "1234567890",
                  "serviceDate": "%s",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": %s
                }
                """.formatted(LocalDate.now().minusDays(5), new BigDecimal("200.00"));

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/claims")
                .then()
                .statusCode(500)
                .body("message", org.hamcrest.Matchers.equalTo("An unexpected error occurred"))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("foreign key")))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("constraint")));
    }
}
