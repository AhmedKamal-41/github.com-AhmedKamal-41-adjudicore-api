package com.ahmedali.claimguard.contract;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

class ClaimRetrievalContractTest extends AbstractContractTest {

    private static final Set<String> EXPECTED_KEYS_WITHOUT_REASON_CODES = Set.of(
            "claimId", "memberId", "providerNpi", "serviceDate", "procedureCode",
            "diagnosisCode", "billedAmount", "allowedAmount", "status",
            "submissionDate", "message"
    );

    @Test
    void getExistingClaim_200_responseShapeMatchesContract() {
        String id = submitValidClaim();

        Response response = given().when().get("/api/v1/claims/{id}", id);

        response.then()
                .statusCode(200)
                .header("Content-Type", matchesPattern("application/json.*"))
                .body("claimId", equalTo(id));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body.keySet()).isEqualTo(EXPECTED_KEYS_WITHOUT_REASON_CODES);
    }

    @Test
    void getUnknownClaim_404_errorEnvelopeShape() {
        Response response = given().when().get("/api/v1/claims/{id}", "CLM-99999999-ZZZZZZ");

        response.then()
                .statusCode(404)
                .body("timestamp", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$"))
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", equalTo("Claim not found: CLM-99999999-ZZZZZZ"));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body.keySet()).isEqualTo(Set.of("timestamp", "status", "error", "message"));
        assertThat(body).doesNotContainKey("fieldErrors");
    }

    @Test
    void getUnknownClaim_404_doesNotLeakStackTrace() {
        String raw = given().when().get("/api/v1/claims/{id}", "CLM-99999999-ZZZZZZ")
                .then().statusCode(404)
                .extract().body().asString();

        assertThat(raw).doesNotContain("trace", "stackTrace", "\"cause\"", "\"exception\"");
        assertThat(raw).doesNotContain("java.", "com.ahmedali.", "\tat ");
    }

    /**
     * GET /api/v1/claims/ (trailing slash with no id) does not match
     * {@code /{claimId}} because Spring Boot 3 disables trailing-slash
     * matching by default. The request falls through to {@link
     * org.springframework.web.servlet.resource.NoResourceFoundException},
     * which our handler maps to a 404 ErrorResponse rather than Spring's
     * default whitelabel HTML.
     */
    @Test
    void getTrailingSlashNoId_404_errorEnvelopeShape() {
        Response response = given().when().get("/api/v1/claims/");

        response.then()
                .statusCode(404)
                .header("Content-Type", matchesPattern("application/json.*"))
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body).containsKeys("timestamp", "status", "error", "message");
    }

    @Test
    void getWithAcceptXml_returns406NotAcceptable() {
        String id = submitValidClaim();

        given()
                .accept("application/xml")
                .when().get("/api/v1/claims/{id}", id)
                .then().statusCode(406);
    }
}
