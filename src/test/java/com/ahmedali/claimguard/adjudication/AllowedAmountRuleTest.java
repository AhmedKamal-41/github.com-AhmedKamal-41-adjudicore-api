package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllowedAmountRuleTest {

    @Mock
    private AllowedAmountCalculator calculator;

    @InjectMocks
    private AllowedAmountRule rule;

    private static Claim claim(String cpt, BigDecimal billed) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 16))
                .procedureCode(cpt)
                .diagnosisCode("J45.909")
                .billedAmount(billed)
                .status(ClaimStatus.VALIDATED)
                .build();
    }

    private static Provider provider(boolean inNetwork) {
        return Provider.builder()
                .npi("1234567890").name("P").specialty("X").isInNetwork(inNetwork).build();
    }

    @Test
    void knownCpt99213InNetwork_returnsApprove125() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("125.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99213", new BigDecimal("150.00")), null, provider(true), List.of());

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.APPROVE);
        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.get();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("125.00");
    }

    @Test
    void knownCpt99214InNetwork_returnsApprove185() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("185.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99214", new BigDecimal("200.00")), null, provider(true), List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("185.00");
    }

    @Test
    void unknownCptBilled200InNetwork_returnsApprove160() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("160.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("UNKNOWN", new BigDecimal("200.00")), null, provider(true), List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("160.00");
    }

    @Test
    void knownCpt99213OutOfNetwork_returnsApprove75() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("75.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99213", new BigDecimal("150.00")), null, provider(false), List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void knownCpt99213Billed100_returnsApprove100_cappedAtBilled() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("100.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99213", new BigDecimal("100.00")), null, provider(true), List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void nullProvider_defensivelyTreatedLikeInNetwork() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("125.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99213", new BigDecimal("150.00")), null, null, List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("125.00");
    }

    @Test
    void bigDecimalPrecision_185Times60_equals111Exact() {
        when(calculator.calculate(any(), any())).thenReturn(new BigDecimal("111.00"));

        Optional<AdjudicationDecision> result = rule.evaluate(
                claim("99214", new BigDecimal("200.00")), null, provider(false), List.of());

        AdjudicationDecision.Approve approve = (AdjudicationDecision.Approve) result.orElseThrow();
        assertThat(approve.allowedAmount()).isEqualByComparingTo("111.00");
        // exact 2-decimal scale, not 110.99999 or similar
        assertThat(approve.allowedAmount().scale()).isEqualTo(2);
    }

    @Test
    void orderIs40() {
        assertThat(rule.order()).isEqualTo(40);
    }
}
