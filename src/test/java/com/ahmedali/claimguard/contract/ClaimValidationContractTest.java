package com.ahmedali.claimguard.contract;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

class ClaimValidationContractTest extends AbstractContractTest {

    private String submitForMember(String memberId, LocalDate serviceDate) {
        String body = """
                {
                  "memberId": "%s",
                  "providerNpi": "%s",
                  "serviceDate": "%s",
                  "procedureCode": "%s",
                  "diagnosisCode": "%s",
                  "billedAmount": %s
                }
                """.formatted(memberId, CONTRACT_NPI, serviceDate, CONTRACT_CPT,
                CONTRACT_ICD, new BigDecimal("150.00").toPlainString());
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().path("claimId");
    }

    @Test
    void validateOnSubmitted_200_statusBecomesValidated_reasonCodesAbsent() {
        String id = submitValidClaim();

        Response response = given().when().post("/api/v1/claims/{id}/validate", id);

        response.then()
                .statusCode(200)
                .body("status", equalTo("VALIDATED"));

        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body).doesNotContainKey("reasonCodes");
        assertThat(body.get("message")).isNull();
    }

    @Test
    void validateOnExpiredMember_200_rejectedWithReasonCodeArrayAndReadableMessage() {
        String id = submitForMember("M005", LocalDate.now().minusDays(5));

        Response response = given().when().post("/api/v1/claims/{id}/validate", id);

        response.then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"))
                .body("reasonCodes", equalTo(List.of("CARC-27")))
                .body("message", containsString("Expenses incurred after coverage terminated"));

        List<?> codes = response.jsonPath().getList("reasonCodes");
        assertThat(codes).hasSize(1);
        // Guard against a regression that serializes the list as a CSV string.
        assertThat(codes.get(0)).isInstanceOf(String.class);
    }

    @Test
    void validateMultiFailure_rejectedWithArrayOfAllReasonCodes() {
        // M005 eligibility ended 2023-12-31; 400-day-old service date also trips
        // the CARC-29 timely-filing rule.
        String id = submitForMember("M005", LocalDate.now().minusDays(400));

        Response response = given().when().post("/api/v1/claims/{id}/validate", id);

        response.then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"));

        List<String> codes = response.jsonPath().getList("reasonCodes");
        assertThat(codes).contains("CARC-27", "CARC-29");
        assertThat(codes.size()).isGreaterThanOrEqualTo(2);

        String message = response.jsonPath().getString("message");
        assertThat(message).contains("Expenses incurred after coverage terminated");
        assertThat(message).contains("Time limit for filing has expired");
        assertThat(message).contains("; ");
    }

    @Test
    void validateOnAlreadyValidated_409_errorEnvelopeMentionsBothStates() {
        String id = submitAndValidateClaim();

        Response response = given().when().post("/api/v1/claims/{id}/validate", id);

        response.then()
                .statusCode(409)
                .body("status", equalTo(409))
                .body("error", equalTo("Conflict"))
                .body("message", containsString("VALIDATED"))
                .body("message", containsString("SUBMITTED"));
    }

    @Test
    void validateUnknownClaim_404_errorEnvelopeShape() {
        given().when().post("/api/v1/claims/{id}/validate", "CLM-99999999-ZZZZZZ")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", containsString("Claim not found"));
    }
}
