package com.ahmedali.claimguard.service;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimAuditLog;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.exception.ClaimNotFoundException;
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
        com.ahmedali.claimguard.adjudication.AdjudicationEngine.class,
        com.ahmedali.claimguard.adjudication.AllowedAmountCalculator.class,
        com.ahmedali.claimguard.adjudication.DuplicateClaimRule.class,
        com.ahmedali.claimguard.adjudication.PriorAuthRule.class,
        com.ahmedali.claimguard.adjudication.CoverageLimitRule.class,
        com.ahmedali.claimguard.adjudication.AllowedAmountRule.class
})
class ClaimServiceValidationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimAuditLogRepository auditLogRepository;

    // Members M001..M005 and providers 1234567890..5678901234 come from V5 seed.

    private Claim persistClaim(String claimId, String memberId, String npi, LocalDate serviceDate,
                               BigDecimal amount, ClaimStatus status,
                               String cpt, String icd) {
        return em.persistAndFlush(Claim.builder()
                .claimId(claimId)
                .memberId(memberId)
                .providerNpi(npi)
                .serviceDate(serviceDate)
                .submissionDate(LocalDate.now())
                .procedureCode(cpt)
                .diagnosisCode(icd)
                .billedAmount(amount)
                .status(status)
                .build());
    }

    @Test
    void validate_validClaim_transitionsToValidatedAndWritesAuditRow() {
        persistClaim("CLM-V1", "M001", "1234567890", LocalDate.now().minusDays(5),
                new BigDecimal("150.00"), ClaimStatus.SUBMITTED, "99213", "J45.909");

        ClaimResponse response = claimService.validateClaim("CLM-V1");

        assertThat(response.status()).isEqualTo(ClaimStatus.VALIDATED);
        Claim reloaded = claimRepository.findByClaimId("CLM-V1").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.VALIDATED);

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-V1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getPreviousStatus()).isEqualTo("SUBMITTED");
        assertThat(logs.get(0).getNewStatus()).isEqualTo("VALIDATED");
        assertThat(logs.get(0).getChangedBy()).isEqualTo("SYSTEM");
        assertThat(logs.get(0).getNotes()).isEqualTo("Validation passed");
    }

    @Test
    void validate_expiredMemberM005_rejectsWithCarc27() {
        persistClaim("CLM-V2", "M005", "1234567890", LocalDate.now().minusDays(5),
                new BigDecimal("150.00"), ClaimStatus.SUBMITTED, "99213", "J45.909");

        ClaimResponse response = claimService.validateClaim("CLM-V2");

        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(response.reasonCodes()).contains("CARC-27");
        assertThat(response.message()).contains("Expenses incurred after coverage terminated");

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-V2");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNewStatus()).isEqualTo("REJECTED");
        assertThat(logs.get(0).getReasonCodes()).contains("CARC-27");
    }

    @Test
    void validate_claimInRejectedStatus_throwsInvalidClaimStateException() {
        persistClaim("CLM-V3", "M001", "1234567890", LocalDate.now().minusDays(5),
                new BigDecimal("150.00"), ClaimStatus.REJECTED, "99213", "J45.909");

        assertThatThrownBy(() -> claimService.validateClaim("CLM-V3"))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("REJECTED")
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    void validate_claimInApprovedStatus_throwsInvalidClaimStateException() {
        persistClaim("CLM-V4", "M001", "1234567890", LocalDate.now().minusDays(5),
                new BigDecimal("150.00"), ClaimStatus.APPROVED, "99213", "J45.909");

        assertThatThrownBy(() -> claimService.validateClaim("CLM-V4"))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void validate_nonexistentClaim_throwsClaimNotFoundException() {
        assertThatThrownBy(() -> claimService.validateClaim("CLM-MISSING"))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void validate_multiFailureClaim_auditRowShowsAllReasonCodesCommaSeparated() {
        // M002 is HMO_SILVER (strict network) + out-of-network NPI 4567890123 → CARC-242
        // billedAmount > HMO cap 50000 → CARC-45
        // serviceDate > 365 days before submission → CARC-29
        persistClaim("CLM-V6", "M002", "4567890123",
                LocalDate.now().minusDays(400),
                new BigDecimal("60000.00"),
                ClaimStatus.SUBMITTED, "99213", "J45.909");

        ClaimResponse response = claimService.validateClaim("CLM-V6");

        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-V6");
        assertThat(logs).hasSize(1);
        String reasonCodes = logs.get(0).getReasonCodes();
        assertThat(reasonCodes).contains("CARC-242");
        assertThat(reasonCodes).contains("CARC-45");
        assertThat(reasonCodes).contains("CARC-29");
        // confirm the joiner produced a comma-separated list (no spaces)
        assertThat(reasonCodes).matches("^CARC-[A-Z0-9]+(,CARC-[A-Z0-9]+)+$");
    }
}
