package com.ahmedali.claimguard.contract;

import com.ahmedali.claimguard.integration.AbstractIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThan;

/**
 * Shared base for API-contract tests. Extends the integration base so it
 * reuses the singleton Postgres container and truncation logic; layers a
 * REST Assured request/response specification on top so every contract
 * test gets JSON content negotiation and a 2-second response budget.
 *
 * Contract tests assert the HTTP contract — field names, types, status
 * codes, envelope shape — NOT behavior. For behavior coverage see
 * {@code com.ahmedali.claimguard.integration}.
 */
public abstract class AbstractContractTest extends AbstractIntegrationTest {

    protected static final String CONTRACT_MEMBER_ID = "M001";
    protected static final String CONTRACT_NPI = "1234567890";
    protected static final String CONTRACT_CPT = "99213";
    protected static final String CONTRACT_ICD = "J45.909";
    protected static final BigDecimal CONTRACT_AMOUNT = new BigDecimal("150.00");

    @BeforeEach
    void configureRestAssuredSpecs() {
        // Log requests/responses only on validation failure — keeps green runs quiet.
        io.restassured.RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        RequestSpecification requestSpec = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        ResponseSpecification responseSpec = new ResponseSpecBuilder()
                .expectResponseTime(lessThan(2000L))
                .build();

        RestAssured.requestSpecification = requestSpec.config(RestAssured.config());
        RestAssured.responseSpecification = responseSpec;
    }

    @AfterEach
    void resetRestAssuredSpecs() {
        RestAssured.requestSpecification = null;
        RestAssured.responseSpecification = null;
    }

    /**
     * POSTs a known-good claim; returns the generated claim id. Used by tests
     * that need a pre-existing SUBMITTED claim without caring about the body.
     */
    protected String submitValidClaim() {
        String body = validBodyJson(LocalDate.now().minusDays(5));
        return given()
                .body(body)
                .when().post("/api/v1/claims")
                .then().statusCode(201)
                .extract().path("claimId");
    }

    /**
     * POSTs then validates; returns a claim id in VALIDATED status.
     */
    protected String submitAndValidateClaim() {
        String id = submitValidClaim();
        given().when().post("/api/v1/claims/{id}/validate", id)
                .then().statusCode(200);
        return id;
    }

    protected String validBodyJson(LocalDate serviceDate) {
        return """
                {
                  "memberId": "%s",
                  "providerNpi": "%s",
                  "serviceDate": "%s",
                  "procedureCode": "%s",
                  "diagnosisCode": "%s",
                  "billedAmount": %s
                }
                """.formatted(CONTRACT_MEMBER_ID, CONTRACT_NPI, serviceDate,
                CONTRACT_CPT, CONTRACT_ICD, CONTRACT_AMOUNT.toPlainString());
    }
}
