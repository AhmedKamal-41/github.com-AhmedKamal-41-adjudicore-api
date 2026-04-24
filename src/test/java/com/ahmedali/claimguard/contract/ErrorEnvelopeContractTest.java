package com.ahmedali.claimguard.contract;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every 4xx response funnels through {@link
 * com.ahmedali.claimguard.exception.GlobalExceptionHandler}. These tests pin
 * the shared envelope shape so individual handlers can't drift apart.
 */
class ErrorEnvelopeContractTest extends AbstractContractTest {

    private static final java.util.regex.Pattern ISO_UTC = java.util.regex.Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");

    @Test
    void validation400_envelopeHasAllFiveKeys() {
        String body = """
                {
                  "providerNpi": "1234567890",
                  "serviceDate": "%s",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """.formatted(LocalDate.now().minusDays(5));

        Response response = given().body(body).when().post("/api/v1/claims");

        response.then().statusCode(400);
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertThat(envelope.keySet()).isEqualTo(
                Set.of("timestamp", "status", "error", "message", "fieldErrors"));
    }

    @Test
    void notFound404_envelopeHasFourKeys_noFieldErrors() {
        Response response = given().when().get("/api/v1/claims/{id}", "CLM-99999999-ZZZZZZ");

        response.then().statusCode(404);
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertThat(envelope.keySet()).isEqualTo(
                Set.of("timestamp", "status", "error", "message"));
        assertThat(envelope).doesNotContainKey("fieldErrors");
    }

    @Test
    void conflict409_envelopeHasFourKeys() {
        String id = submitAndValidateClaim();

        Response response = given().when().post("/api/v1/claims/{id}/validate", id);

        response.then().statusCode(409);
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertThat(envelope.keySet()).isEqualTo(
                Set.of("timestamp", "status", "error", "message"));
    }

    @Test
    void malformedJson400_envelopeSaysMalformedJsonRequest_noFieldErrors() {
        Response response = given().body("not valid json").when().post("/api/v1/claims");

        response.then().statusCode(400)
                .body("message", org.hamcrest.Matchers.equalTo("Malformed JSON request"));

        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertThat(envelope.keySet()).isEqualTo(
                Set.of("timestamp", "status", "error", "message"));
        assertThat(envelope).doesNotContainKey("fieldErrors");
    }

    @Test
    void everyErrorEnvelope_hasIsoUtcTimestampWithinLast60Seconds() {
        List<String> timestamps = new ArrayList<>();

        // 400 validation
        String badBody = """
                {
                  "providerNpi": "1234567890",
                  "serviceDate": "%s",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """.formatted(LocalDate.now().minusDays(5));
        timestamps.add(given().body(badBody).when().post("/api/v1/claims")
                .then().statusCode(400).extract().path("timestamp"));

        // 404
        timestamps.add(given().when().get("/api/v1/claims/{id}", "CLM-99999999-ZZZZZZ")
                .then().statusCode(404).extract().path("timestamp"));

        // 409
        String vId = submitAndValidateClaim();
        timestamps.add(given().when().post("/api/v1/claims/{id}/validate", vId)
                .then().statusCode(409).extract().path("timestamp"));

        // 400 malformed JSON
        timestamps.add(given().body("not valid json").when().post("/api/v1/claims")
                .then().statusCode(400).extract().path("timestamp"));

        Instant now = Instant.now();
        for (String ts : timestamps) {
            assertThat(ts).matches(ISO_UTC);
            Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(ts));
            assertThat(parsed).isAfter(now.minusSeconds(60));
            assertThat(parsed).isBeforeOrEqualTo(now.plusSeconds(1));
        }
    }
}
