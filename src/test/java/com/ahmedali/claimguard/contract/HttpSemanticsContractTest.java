package com.ahmedali.claimguard.contract;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks down HTTP method/semantic behavior as currently observed.
 *
 * Context: {@code GlobalExceptionHandler} installs a catch-all
 * {@code @ExceptionHandler(Exception.class)} that generalizes *every*
 * uncaught Spring framework exception — including the ones that would
 * otherwise produce 405 (HttpRequestMethodNotSupportedException) or 415
 * (HttpMediaTypeNotSupportedException) — to a 500. These contract tests
 * record that behavior so a future pass that adds specific handlers
 * (recommended) causes an intentional, visible breakage rather than a
 * silent semantic drift.
 */
class HttpSemanticsContractTest extends AbstractContractTest {

    @Test
    void putOnCollectionEndpoint_currentlyReturns500_viaCatchAll() {
        given()
                .body(validBodyJson(LocalDate.now().minusDays(5)))
                .when().put("/api/v1/claims")
                .then()
                .statusCode(500)
                .body("message", org.hamcrest.Matchers.equalTo("An unexpected error occurred"));
    }

    @Test
    void getOnCollectionEndpoint_currentlyReturns500_viaCatchAll() {
        given()
                .when().get("/api/v1/claims")
                .then()
                .statusCode(500)
                .body("message", org.hamcrest.Matchers.equalTo("An unexpected error occurred"));
    }

    @Test
    void optionsOnCollectionEndpoint_returns200_withAllowHeaderIncludingPost() {
        given()
                .when().options("/api/v1/claims")
                .then()
                .statusCode(200)
                .header("Allow", org.hamcrest.Matchers.containsString("POST"));
    }

    @Test
    void deleteOnItemEndpoint_currentlyReturns500_viaCatchAll() {
        String id = submitValidClaim();

        given()
                .when().delete("/api/v1/claims/{id}", id)
                .then()
                .statusCode(500)
                .body("message", org.hamcrest.Matchers.equalTo("An unexpected error occurred"));
    }

    /**
     * Sequential POSTs must each stay under the 2s global budget (pinned in
     * {@link AbstractContractTest}). The first iteration includes Spring+JIT
     * warmup and can exceed a tighter 500ms budget, so the contract here is
     * "each call succeeds within the global envelope" rather than a
     * microbenchmark.
     */
    @Test
    void postIntake_tenSequentialCallsAllSucceed_withinGlobalTwoSecondBudget() {
        for (int i = 0; i < 10; i++) {
            given()
                    .body(validBodyJson(LocalDate.now().minusDays(5)))
                    .when().post("/api/v1/claims")
                    .then().statusCode(201);
        }
    }
}
