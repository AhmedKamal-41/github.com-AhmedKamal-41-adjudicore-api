package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageLimitRuleTest {

    private CoverageLimitRule rule;

    @BeforeEach
    void setUp() {
        rule = new CoverageLimitRule(new AllowedAmountCalculator());
    }

    private static Member member(String planCode) {
        return Member.builder()
                .memberId("M001").firstName("T").lastName("M")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .planCode(planCode)
                .eligibilityStart(LocalDate.of(2024, 1, 1))
                .build();
    }

    private static Claim claim(String claimId, BigDecimal billed, BigDecimal allowed,
                               LocalDate serviceDate, ClaimStatus status) {
        return Claim.builder()
                .claimId(claimId)
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(serviceDate)
                .submissionDate(serviceDate.plusDays(1))
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(billed)
                .allowedAmount(allowed)
                .status(status)
                .build();
    }

    @Test
    void medicareA_anyAmount_returnsEmpty() {
        Claim current = claim("CLM-X", new BigDecimal("80000.00"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(current, member("MEDICARE_A"), null, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void ppoGold_50kCurrentYear_new10kClaim_returnsEmpty() {
        LocalDate year = LocalDate.of(2025, 3, 1);
        Claim prior = claim("CLM-1", new BigDecimal("50000.00"), new BigDecimal("50000.00"),
                year, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", new BigDecimal("10000.00"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("PPO_GOLD"), null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void hmoSilver_49999CurrentYear_new1Claim_reachesCapExactly_returnsEmpty() {
        // HMO_SILVER cap = $50,000. 99213 in-network = $125. Billed $1.00 → capped at billed = $1.00.
        // So projected = $49,999 + $1 = $50,000 → at cap, allowed.
        LocalDate year = LocalDate.of(2025, 3, 1);
        Claim prior = claim("CLM-1", new BigDecimal("49999.00"), new BigDecimal("49999.00"),
                year, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", new BigDecimal("1.00"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("HMO_SILVER"), null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void hmoSilver_49999CurrentYear_new101Claim_exceedsCap_returnsDenyCarc119() {
        // Prior $49,999 + estimate for $1.01 billed (which becomes $1.01 after cap-at-billed)
        // = $50,000.01 > $50,000 cap
        LocalDate year = LocalDate.of(2025, 3, 1);
        Claim prior = claim("CLM-1", new BigDecimal("49999.00"), new BigDecimal("49999.00"),
                year, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", new BigDecimal("1.01"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("HMO_SILVER"), null, List.of(prior));

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.DENY);
        assertThat(result.get().reasonCodes()).containsExactly("CARC-119");
    }

    @Test
    void epoBronze_24kPaid_new2kClaim_exceedsCap_returnsDenyCarc119() {
        // EPO_BRONZE cap = $25,000. 99213 billed $2000 in-network → capped at billed ($125).
        // Wait — $2000 billed, fee schedule $125, takes lower → $125. That wouldn't push over cap.
        // Fix: use a billed <= fee schedule rate so estimate = billed.
        // Actually: allowed = min(base, billed). base 99213 = $125. billed $2000 → $125.
        // $24,000 + $125 = $24,125 which is under $25,000 cap. Test description implies the
        // coverage rule uses estimate (the small $125), and the test name says "2k claim
        // exceeds". So use a higher-rate CPT or a seed approach closer to the cap.
        // Adjust: use $24,900 prior + $2000 billed (capped at $125 estimate). Still under.
        // Proper: use prior at $24,900 and use estimate equal to a higher fee schedule code 99215 = $245
        // prior $24,900 + $245 = $25,145 > $25,000 → DENY
        LocalDate year = LocalDate.of(2025, 3, 1);
        Claim prior = claim("CLM-1", new BigDecimal("24900.00"), new BigDecimal("24900.00"),
                year, ClaimStatus.APPROVED);
        Claim current = Claim.builder()
                .claimId("CLM-NEW").memberId("M001").providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 16))
                .procedureCode("99215") // $245 in fee schedule
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("500.00"))
                .status(ClaimStatus.VALIDATED)
                .build();

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("EPO_BRONZE"), null, List.of(prior));

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.DENY);
        assertThat(result.get().reasonCodes()).containsExactly("CARC-119");
    }

    @Test
    void priorYearClaims_doNotCountTowardCurrentYearSum() {
        LocalDate priorYear = LocalDate.of(2024, 3, 1);
        Claim prior = claim("CLM-1", new BigDecimal("49999.00"), new BigDecimal("49999.00"),
                priorYear, ClaimStatus.APPROVED);
        Claim current = claim("CLM-NEW", new BigDecimal("50.00"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("HMO_SILVER"), null, List.of(prior));

        assertThat(result).isEmpty();
    }

    @Test
    void onlyApprovedClaimsCount_rejectedExcluded() {
        LocalDate year = LocalDate.of(2025, 3, 1);
        Claim rejected = claim("CLM-R", new BigDecimal("49999.00"), new BigDecimal("49999.00"),
                year, ClaimStatus.REJECTED);
        Claim denied = claim("CLM-D", new BigDecimal("49999.00"), new BigDecimal("49999.00"),
                year, ClaimStatus.DENIED);
        Claim current = claim("CLM-NEW", new BigDecimal("1.00"), null,
                LocalDate.of(2025, 6, 15), ClaimStatus.VALIDATED);

        Optional<AdjudicationDecision> result = rule.evaluate(
                current, member("HMO_SILVER"), null, List.of(rejected, denied));

        assertThat(result).isEmpty();
    }

    @Test
    void orderIs30() {
        assertThat(rule.order()).isEqualTo(30);
    }
}
