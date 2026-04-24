package com.ahmedali.claimguard.contract;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

/**
 * Pins the shape of the auto-generated OpenAPI spec so a future refactor
 * that accidentally drops an endpoint, renames a DTO, or disables springdoc
 * fails the build loudly instead of silently shipping a stale schema.
 */
class ContractOpenApiTest extends AbstractContractTest {

    /**
     * First hit to /v3/api-docs triggers Springdoc's one-time spec generation
     * (~1-2s including JIT). Warm the endpoint without response-time assertions
     * so the timed tests below measure steady-state, not cold-start, latency.
     */
    @BeforeEach
    void warmOpenApiEndpoint() {
        io.restassured.RestAssured.responseSpecification = null;
        given().when().get("/v3/api-docs").then().statusCode(200);
    }

    @Test
    void apiDocsEndpoint_returns200_jsonContentType() {
        given()
                .when().get("/v3/api-docs")
                .then()
                .statusCode(200)
                .header("Content-Type", matchesPattern("application/json.*"));
    }

    @Test
    void spec_declaresAllFourClaimEndpoints() {
        Response response = given().when().get("/v3/api-docs");
        response.then().statusCode(200);
        JsonPath json = response.jsonPath();

        Map<String, ?> paths = json.getMap("paths");
        assertThat(paths.keySet()).contains(
                "/api/v1/claims",
                "/api/v1/claims/{claimId}",
                "/api/v1/claims/{claimId}/validate",
                "/api/v1/claims/{claimId}/adjudicate"
        );

        // Verify the HTTP methods on each path match the controller mappings.
        Map<String, ?> submitOps = (Map<String, ?>) paths.get("/api/v1/claims");
        Map<String, ?> getOps = (Map<String, ?>) paths.get("/api/v1/claims/{claimId}");
        Map<String, ?> validateOps = (Map<String, ?>) paths.get("/api/v1/claims/{claimId}/validate");
        Map<String, ?> adjudicateOps = (Map<String, ?>) paths.get("/api/v1/claims/{claimId}/adjudicate");

        assertThat(submitOps.keySet()).contains("post");
        assertThat(getOps.keySet()).contains("get");
        assertThat(validateOps.keySet()).contains("post");
        assertThat(adjudicateOps.keySet()).contains("post");
    }

    @Test
    void spec_declaresRequestResponseAndErrorSchemas() {
        Response response = given().when().get("/v3/api-docs");
        response.then().statusCode(200);

        Map<String, ?> schemas = response.jsonPath().getMap("components.schemas");
        assertThat(schemas.keySet()).contains(
                "ClaimSubmissionRequest",
                "ClaimResponse",
                "ErrorResponse"
        );
    }
}
