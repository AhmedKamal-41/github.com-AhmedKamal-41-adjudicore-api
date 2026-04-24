package com.ahmedali.claimguard.contract;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

class ClaimAdjudicationContractTest extends AbstractContractTest {

    private String submitCustom(String cpt, BigDecimal amount, LocalDate serviceDate) {
        String body = """
                {
                  "memberId": "%s",
                  "providerNpi": "%s",
                  "serviceDate": "%s",
                  "procedureCode": "%s",
                  "diagnosisCode": "%s",
                  "billedAmount": %s
                }
                """.formatted(CONTRACT_MEMBER_ID, CONTRACT_NPI, serviceDate, cpt,
                CONTRACT_ICD, amount.toPlainString());
        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().path("claimId");
    }

    private void validate(String id) {
        given().when().post("/api/v1/claims/{id}/validate", id).then().statusCode(200);
    }

    @Test
    void adjudicateApprove_200_responseShapeMatchesContract() {
        String id = submitAndValidateClaim();

        Response response = given().when().post("/api/v1/claims/{id}/adjudicate", id);

        response.then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
                .body("allowedAmount", greaterThan(0.0f))
                .body("message", equalTo("Approved at fee schedule rate"));

        assertThat(response.jsonPath().getMap("$")).doesNotContainKey("reasonCodes");
    }

    @Test
    void adjudicateDeny_duplicate_200_reasonCodesIsSingletonArray() {
        LocalDate sd = LocalDate.now().minusDays(5);
        // First claim: full pipeline → APPROVED
        String first = submitCustom("99213", new BigDecimal("150.00"), sd);
        validate(first);
        given().when().post("/api/v1/claims/{id}/adjudicate", first)
                .then().statusCode(200);

        // Duplicate: should be DENIED
        String second = submitCustom("99213", new BigDecimal("150.00"), sd);
        validate(second);

        Response response = given().when().post("/api/v1/claims/{id}/adjudicate", second);

        response.then()
                .statusCode(200)
                .body("status", equalTo("DENIED"))
                .body("allowedAmount", nullValue())
                .body("reasonCodes", equalTo(List.of("CARC-18")))
                .body("message", startsWith("Duplicate of claim CLM-"));

        List<String> codes = response.jsonPath().getList("reasonCodes");
        assertThat(codes).containsExactly("CARC-18");
    }

    @Test
    void adjudicatePend_priorAuth_200_statusStaysValidatedWithReasonCodes() {
        String id = submitCustom("70553", new BigDecimal("2000.00"),
                LocalDate.now().minusDays(5));
        validate(id);

        Response response = given().when().post("/api/v1/claims/{id}/adjudicate", id);

        response.then()
                .statusCode(200)
                .body("status", equalTo("VALIDATED"))
                .body("allowedAmount", nullValue())
                .body("reasonCodes", equalTo(List.of("CARC-197")))
                .body("message", containsString("requires prior authorization"));
    }

    @Test
    void adjudicateOnSubmitted_409_errorEnvelopeMentionsValidated() {
        String id = submitValidClaim();

        given().when().post("/api/v1/claims/{id}/adjudicate", id)
                .then()
                .statusCode(409)
                .body("status", equalTo(409))
                .body("error", equalTo("Conflict"))
                .body("message", containsString("SUBMITTED"))
                .body("message", containsString("VALIDATED"));
    }

    @Test
    void adjudicateOnRejected_409_errorEnvelopeShape() {
        // M005 eligibility ended 2023-12-31, so validate takes the claim to REJECTED.
        String body = """
                {
                  "memberId": "M005",
                  "providerNpi": "%s",
                  "serviceDate": "%s",
                  "procedureCode": "%s",
                  "diagnosisCode": "%s",
                  "billedAmount": %s
                }
                """.formatted(CONTRACT_NPI, LocalDate.now().minusDays(5),
                CONTRACT_CPT, CONTRACT_ICD, "150.00");
        String rejectedId = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().path("claimId");
        given().when().post("/api/v1/claims/{id}/validate", rejectedId)
                .then().statusCode(200).body("status", equalTo("REJECTED"));

        given().when().post("/api/v1/claims/{id}/adjudicate", rejectedId)
                .then()
                .statusCode(409)
                .body("error", equalTo("Conflict"))
                .body("message", containsString("REJECTED"));
    }

    @Test
    void adjudicateUnknownClaim_404_errorEnvelopeShape() {
        given().when().post("/api/v1/claims/{id}/adjudicate", "CLM-99999999-ZZZZZZ")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", containsString("Claim not found"));
    }
}
