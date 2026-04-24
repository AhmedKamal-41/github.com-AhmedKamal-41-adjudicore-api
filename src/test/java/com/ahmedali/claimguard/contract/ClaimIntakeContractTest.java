package com.ahmedali.claimguard.contract;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class ClaimIntakeContractTest extends AbstractContractTest {

    private static final Set<String> EXPECTED_KEYS_WITHOUT_REASON_CODES = Set.of(
            "claimId", "memberId", "providerNpi", "serviceDate", "procedureCode",
            "diagnosisCode", "billedAmount", "allowedAmount", "status",
            "submissionDate", "message"
    );

    @Test
    void postValidClaim_201_responseShapeMatchesContract() {
        Response response = given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims");

        response.then()
                .statusCode(201)
                .header("Location", matchesPattern("^/api/v1/claims/CLM-\\d{8}-[A-Z0-9]{6}$"))
                .header("Content-Type", matchesPattern("application/json.*"));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body.keySet()).isEqualTo(EXPECTED_KEYS_WITHOUT_REASON_CODES);
    }

    @Test
    void postValidClaim_claimIdFormat_prefixDateSuffix() {
        String claimId = given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().path("claimId");

        assertThat(claimId).matches("^CLM-\\d{8}-[A-Z0-9]{6}$");
        String datePortion = claimId.substring(4, 12);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(datePortion).isEqualTo(today);
    }

    @Test
    void postValidClaim_statusIsEnumNameString() {
        given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .body("status", equalTo("SUBMITTED"))
                .body("status", instanceOf(String.class));
    }

    @Test
    void postValidClaim_billedAmountSerializesAsJsonNumberNotString() {
        Response response = given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims");

        response.then().statusCode(201);

        // The JSON body, parsed loosely, must surface billedAmount as a number
        // (Jackson would stringify BigDecimal only if misconfigured).
        Object rawBilled = response.jsonPath().get("billedAmount");
        assertThat(rawBilled).isInstanceOf(Number.class);

        BigDecimal extracted = new BigDecimal(response.jsonPath().getString("billedAmount"));
        assertThat(extracted).isEqualByComparingTo(new BigDecimal("150.00"));

        // And double-check the raw JSON has no quotes around the amount.
        String rawBody = response.getBody().asString();
        assertThat(rawBody).matches("(?s).*\"billedAmount\"\\s*:\\s*150(\\.0+)?[,}\\s].*");
        assertThat(rawBody).doesNotContain("\"billedAmount\":\"150");
    }

    @Test
    void postValidClaim_allowedAmountIsJsonNull_notAbsent_onSubmittedState() {
        Response response = given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims");

        response.then()
                .statusCode(201)
                .body("allowedAmount", nullValue());

        // Contract: the key is present with JSON null (ClaimResponse has no
        // class-level @JsonInclude, so explicit nulls round-trip).
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body).containsKey("allowedAmount");
        assertThat(body.get("allowedAmount")).isNull();
    }

    @Test
    void postInvalidBody_400_errorEnvelopeShape() {
        String bodyMissingMember = """
                {
                  "providerNpi": "1234567890",
                  "serviceDate": "%s",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """.formatted(LocalDate.now().minusDays(5));

        Response response = given()
                .body(bodyMissingMember)
                .when().post("/api/v1/claims");

        response.then()
                .statusCode(400)
                .body("timestamp", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$"))
                .body("status", equalTo(400))
                .body("error", equalTo("Bad Request"))
                .body("message", equalTo("Validation failed"))
                .body("fieldErrors.memberId", notNullValue())
                .body("fieldErrors.memberId", instanceOf(String.class));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body.keySet()).isEqualTo(
                Set.of("timestamp", "status", "error", "message", "fieldErrors"));
        assertThat(body.get("fieldErrors")).isInstanceOf(Map.class);
        String memberErr = (String) ((Map<?, ?>) body.get("fieldErrors")).get("memberId");
        assertThat(memberErr).isNotBlank();
    }

    /**
     * Current behavior: text/plain on the JSON intake endpoint is swallowed by
     * the catch-all {@code @ExceptionHandler(Exception.class)} in
     * {@code GlobalExceptionHandler}, surfacing as 500 rather than Spring's
     * default 415. Pinned here so adding a specific
     * {@code HttpMediaTypeNotSupportedException} handler later (recommended)
     * fails this test loudly instead of regressing the contract silently.
     */
    @Test
    void postWithTextPlainContentType_currentlyReturns500_viaCatchAll() {
        given()
                .contentType(ContentType.TEXT)
                .accept(ContentType.JSON)
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().post("/api/v1/claims")
                .then()
                .statusCode(500)
                .body("message", equalTo("An unexpected error occurred"));
    }
}
