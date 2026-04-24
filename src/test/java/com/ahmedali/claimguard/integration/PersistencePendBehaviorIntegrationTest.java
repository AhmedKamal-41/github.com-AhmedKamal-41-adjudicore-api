package com.ahmedali.claimguard.integration;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class PersistencePendBehaviorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;

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

    private ClaimResponse validate(String claimId) {
        return given().when().post("/api/v1/claims/{id}/validate", claimId)
                .then().extract().as(ClaimResponse.class);
    }

    private ClaimResponse adjudicate(String claimId) {
        return given().when().post("/api/v1/claims/{id}/adjudicate", claimId)
                .then().extract().as(ClaimResponse.class);
    }

    private Statistics hibernateStats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void pendDecision_doesNotIssueUpdateToClaimsTable() {
        // submit + validate first
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "70553", new BigDecimal("2000.00"));
        validate(submitted.claimId());

        Statistics stats = hibernateStats();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Adjudicate — this should PEND (CPT 70553)
        ClaimResponse pended = adjudicate(submitted.claimId());

        assertThat(pended.reasonCodes()).contains("CARC-197");

        // Exactly one insert (the audit row), zero updates to claims
        assertThat(stats.getEntityInsertCount()).isEqualTo(1L);
        assertThat(stats.getEntityUpdateCount()).isEqualTo(0L);
    }

    @Test
    void approveDecision_issuesExactlyOneUpdateOnClaims_andOneInsertOnAuditLog() {
        ClaimResponse submitted = submit("M001", "1234567890",
                LocalDate.now().minusDays(5), "99213", new BigDecimal("150.00"));
        validate(submitted.claimId());

        Statistics stats = hibernateStats();
        stats.setStatisticsEnabled(true);
        stats.clear();

        ClaimResponse approved = adjudicate(submitted.claimId());

        assertThat(approved.status().name()).isEqualTo("APPROVED");

        assertThat(stats.getEntityUpdateCount()).isEqualTo(1L); // claim row
        assertThat(stats.getEntityInsertCount()).isEqualTo(1L); // audit row
    }
}
