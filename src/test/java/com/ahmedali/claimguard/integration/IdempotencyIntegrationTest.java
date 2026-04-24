package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.ClaimStatus;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void validateCalledTwiceOnSameClaim_secondCallReturns409() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));

        ClaimResponse first = given()
                .when().post("/api/v1/claims/{id}/validate", submitted.claimId())
                .then().statusCode(200)
                .extract().as(ClaimResponse.class);
        assertThat(first.status()).isEqualTo(ClaimStatus.VALIDATED);

        given()
                .when().post("/api/v1/claims/{id}/validate", submitted.claimId())
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Conflict"))
                .body("message", org.hamcrest.Matchers.containsString("SUBMITTED"))
                .body("message", org.hamcrest.Matchers.containsString("VALIDATED"));
    }

    @Test
    void adjudicateCalledTwiceOnSameClaim_secondCallReturns409() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));

        given().when().post("/api/v1/claims/{id}/validate", submitted.claimId())
                .then().statusCode(200);

        ClaimResponse first = given()
                .when().post("/api/v1/claims/{id}/adjudicate", submitted.claimId())
                .then().statusCode(200)
                .extract().as(ClaimResponse.class);
        assertThat(first.status()).isEqualTo(ClaimStatus.APPROVED);

        given()
                .when().post("/api/v1/claims/{id}/adjudicate", submitted.claimId())
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Conflict"))
                .body("message", org.hamcrest.Matchers.containsString("VALIDATED"))
                .body("message", org.hamcrest.Matchers.containsString("APPROVED"));
    }
}
