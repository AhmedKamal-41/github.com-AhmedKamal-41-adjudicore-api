package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateClaimRuleTest {

    private final DuplicateClaimRule rule = new DuplicateClaimRule();

    private static Claim claim(String claimId, String cpt, LocalDate serviceDate, ClaimStatus status) {
        return Claim.builder()
                .claimId(claimId)
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(serviceDate)
                .submissionDate(serviceDate.plusDays(1))
                .procedureCode(cpt)
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("150.00"))
                .status(status)
                .build();
    }

    @Test
    void noDuplicatesInHistory_returnsEmpty() {
        Claim current = claim("CLM-NEW", "99213", LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void duplicateFound_sameCptDateAndApproved_returnsDenyCarc18() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        Claim prior = claim("CLM-PRIOR", "99213", sd, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", "99213", sd, ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of(prior));

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.DENY);
        assertThat(result.get().reasonCodes()).containsExactly("CARC-18");
        assertThat(result.get().notes()).contains("CLM-PRIOR");
    }

    @Test
    void sameCptDifferentDate_returnsEmpty() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        Claim prior = claim("CLM-PRIOR", "99213", sd.minusDays(1), ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", "99213", sd, ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void sameDateDifferentCpt_returnsEmpty() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        Claim prior = claim("CLM-PRIOR", "99214", sd, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", "99213", sd, ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void duplicateExistsButDenied_returnsEmpty() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        Claim prior = claim("CLM-PRIOR", "99213", sd, ClaimStatus.DENIED);
        Claim current = claim("CLM-NEW", "99213", sd, ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void duplicateIsSelf_returnsEmpty() {
        LocalDate sd = LocalDate.of(2025, 6, 15);
        Claim self = claim("CLM-NEW", "99213", sd, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", "99213", sd, ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, null, null, List.of(self));

        assertThat(result).isEmpty();
    }

    @Test
    void orderIs10() {
        assertThat(rule.order()).isEqualTo(10);
    }
}
