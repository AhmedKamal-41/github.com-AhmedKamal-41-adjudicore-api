package com.ahmedali.claimguard.service;

import com.ahmedali.claimguard.adjudication.AdjudicationEngine;
import com.ahmedali.claimguard.adjudication.AllowedAmountCalculator;
import com.ahmedali.claimguard.adjudication.AllowedAmountRule;
import com.ahmedali.claimguard.adjudication.CoverageLimitRule;
import com.ahmedali.claimguard.adjudication.DuplicateClaimRule;
import com.ahmedali.claimguard.adjudication.PriorAuthRule;
import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimAuditLog;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.exception.InvalidClaimStateException;
import com.ahmedali.claimguard.repository.ClaimAuditLogRepository;
import com.ahmedali.claimguard.repository.ClaimRepository;
import com.ahmedali.claimguard.validation.AmountValidator;
import com.ahmedali.claimguard.validation.ClaimValidationPipeline;
import com.ahmedali.claimguard.validation.CodeFormatValidator;
import com.ahmedali.claimguard.validation.MemberEligibilityValidator;
import com.ahmedali.claimguard.validation.ProviderNetworkValidator;
import com.ahmedali.claimguard.validation.ServiceDateValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClaimService.class,
        ClaimValidationPipeline.class,
        MemberEligibilityValidator.class,
        ProviderNetworkValidator.class,
        ServiceDateValidator.class,
        AmountValidator.class,
        CodeFormatValidator.class,
        AdjudicationEngine.class,
        AllowedAmountCalculator.class,
        DuplicateClaimRule.class,
        PriorAuthRule.class,
        CoverageLimitRule.class,
        AllowedAmountRule.class
})
class ClaimServiceAdjudicationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimAuditLogRepository auditLogRepository;

    // V5 seeds M001..M005 and NPIs 1234567890..5678901234

    private Claim persistClaim(String claimId, String memberId, String npi, LocalDate serviceDate,
                               BigDecimal billed, BigDecimal allowed, ClaimStatus status,
                               String cpt) {
        return em.persistAndFlush(Claim.builder()
                .claimId(claimId)
                .memberId(memberId)
                .providerNpi(npi)
                .serviceDate(serviceDate)
                .submissionDate(serviceDate.plusDays(1))
                .procedureCode(cpt)
                .diagnosisCode("J45.909")
                .billedAmount(billed)
                .allowedAmount(allowed)
                .status(status)
                .build());
    }

    @Test
    void adjudicate_cleanApprove_inNetwork_cpt99213_setsAllowed125() {
        persistClaim("CLM-A1", "M001", "1234567890", LocalDate.of(2025, 6, 15),
                new BigDecimal("150.00"), null, ClaimStatus.VALIDATED, "99213");

        ClaimResponse response = claimService.adjudicateClaim("CLM-A1");

        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.allowedAmount()).isEqualByComparingTo("125.00");

        Claim reloaded = claimRepository.findByClaimId("CLM-A1").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(reloaded.getAllowedAmount()).isEqualByComparingTo("125.00");

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-A1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getPreviousStatus()).isEqualTo("VALIDATED");
        assertThat(logs.get(0).getNewStatus()).isEqualTo("APPROVED");
        assertThat(logs.get(0).getNotes()).isEqualTo("Approved at fee schedule rate");
    }

    @Test
    void adjudicate_outOfNetworkPpoProvider_cpt99213_setsAllowed75() {
        // PPO_GOLD (M001) + out-of-network provider (NPI 4567890123) reaches adjudication
        // because PPO allows out-of-network at validation. 99213 base $125 * 0.60 = $75.
        persistClaim("CLM-A2", "M001", "4567890123", LocalDate.of(2025, 6, 15),
                new BigDecimal("150.00"), null, ClaimStatus.VALIDATED, "99213");

        ClaimResponse response = claimService.adjudicateClaim("CLM-A2");

        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.allowedAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void adjudicate_duplicateDenial_priorApprovedClaim_returnsDenyCarc18() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        persistClaim("CLM-PRIOR", "M001", "1234567890", sd,
                new BigDecimal("150.00"), new BigDecimal("125.00"),
                ClaimStatus.APPROVED, "99213");
        persistClaim("CLM-DUP", "M001", "1234567890", sd,
                new BigDecimal("150.00"), null,
                ClaimStatus.VALIDATED, "99213");

        ClaimResponse response = claimService.adjudicateClaim("CLM-DUP");

        assertThat(response.status()).isEqualTo(ClaimStatus.DENIED);

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-DUP");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNewStatus()).isEqualTo("DENIED");
        assertThat(logs.get(0).getReasonCodes()).isEqualTo("CARC-18");
    }

    @Test
    void adjudicate_priorAuthCpt70553_pends_statusStaysValidated() {
        persistClaim("CLM-PA", "M001", "1234567890", LocalDate.of(2025, 6, 15),
                new BigDecimal("2500.00"), null, ClaimStatus.VALIDATED, "70553");

        ClaimResponse response = claimService.adjudicateClaim("CLM-PA");

        assertThat(response.status()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(response.message()).contains("70553");

        Claim reloaded = claimRepository.findByClaimId("CLM-PA").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.VALIDATED);

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-PA");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getPreviousStatus()).isEqualTo("VALIDATED");
        assertThat(logs.get(0).getNewStatus()).isEqualTo("VALIDATED");
        assertThat(logs.get(0).getReasonCodes()).isEqualTo("CARC-197");
    }

    @Test
    void adjudicate_coverageLimitBoundary_hmoSilverAt49999Plus1_approvesExactlyAtCap() {
        // M002 is HMO_SILVER (cap $50,000). Seed prior approved = $49,999.
        // New claim billed $1.00 → allowed capped at billed = $1.00 → total $50,000.00 at cap.
        LocalDate year = LocalDate.of(2025, 3, 1);
        persistClaim("CLM-HMO-PRIOR", "M002", "1234567890", year,
                new BigDecimal("50000.00"), new BigDecimal("49999.00"),
                ClaimStatus.APPROVED, "99213");
        persistClaim("CLM-HMO-NEW", "M002", "1234567890", LocalDate.of(2025, 6, 15),
                new BigDecimal("1.00"), null, ClaimStatus.VALIDATED, "99213");

        ClaimResponse response = claimService.adjudicateClaim("CLM-HMO-NEW");

        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.allowedAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    void adjudicate_coverageLimitExceeded_hmoSilverAt49999Plus101_deniesCarc119() {
        // Prior $49,999 + estimate $1.01 billed (allowed capped at billed) → $50,000.01 > cap
        LocalDate year = LocalDate.of(2025, 3, 1);
        persistClaim("CLM-HMO-P2", "M002", "1234567890", year,
                new BigDecimal("50000.00"), new BigDecimal("49999.00"),
                ClaimStatus.APPROVED, "99213");
        persistClaim("CLM-HMO-N2", "M002", "1234567890", LocalDate.of(2025, 6, 15),
                new BigDecimal("1.01"), null, ClaimStatus.VALIDATED, "99213");

        ClaimResponse response = claimService.adjudicateClaim("CLM-HMO-N2");

        assertThat(response.status()).isEqualTo(ClaimStatus.DENIED);
        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-HMO-N2");
        assertThat(logs.get(0).getReasonCodes()).isEqualTo("CARC-119");
    }

    @Test
    void adjudicate_shortCircuitsOnFirstDeny_duplicateBeatsPriorAuth() {
        // Seed an APPROVED prior claim with CPT 70553 (prior-auth code) and same date.
        // New claim matches → DuplicateClaimRule (order 10) denies before PriorAuthRule (20) runs.
        // Expected: CARC-18 only, NOT CARC-197.
        LocalDate sd = LocalDate.of(2025, 6, 15);
        persistClaim("CLM-PRIOR-PA", "M001", "1234567890", sd,
                new BigDecimal("2500.00"), new BigDecimal("1800.00"),
                ClaimStatus.APPROVED, "70553");
        persistClaim("CLM-DUP-PA", "M001", "1234567890", sd,
                new BigDecimal("2500.00"), null,
                ClaimStatus.VALIDATED, "70553");

        ClaimResponse response = claimService.adjudicateClaim("CLM-DUP-PA");

        assertThat(response.status()).isEqualTo(ClaimStatus.DENIED);

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-DUP-PA");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getReasonCodes()).isEqualTo("CARC-18");
        assertThat(logs.get(0).getReasonCodes()).doesNotContain("CARC-197");
    }

    @Test
    void adjudicate_claimInSubmittedStatus_throwsInvalidClaimStateException() {
        persistClaim("CLM-WRONG", "M001", "1234567890", LocalDate.of(2025, 6, 15),
                new BigDecimal("150.00"), null, ClaimStatus.SUBMITTED, "99213");

        assertThatThrownBy(() -> claimService.adjudicateClaim("CLM-WRONG"))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("VALIDATED");
    }
}
