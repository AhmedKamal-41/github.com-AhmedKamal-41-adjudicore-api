package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PriorAuthRuleTest {

    private final PriorAuthRule rule = new PriorAuthRule();

    private static Claim claim(String cpt) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 16))
                .procedureCode(cpt)
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("1000.00"))
                .status(ClaimStatus.VALIDATED)
                .build();
    }

    @Test
    void commonCpt99213_returnsEmpty() {
        Optional<AdjudicationDecision> result = rule.evaluate(claim("99213"), null, null, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void priorAuthCpt70553_mriBrain_returnsPendCarc197() {
        Optional<AdjudicationDecision> result = rule.evaluate(claim("70553"), null, null, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.PEND);
        assertThat(result.get().reasonCodes()).containsExactly("CARC-197");
        assertThat(result.get().notes()).contains("70553");
    }

    @Test
    void priorAuthCpt27447_kneeReplacement_returnsPendCarc197() {
        Optional<AdjudicationDecision> result = rule.evaluate(claim("27447"), null, null, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(AdjudicationDecision.Outcome.PEND);
        assertThat(result.get().reasonCodes()).containsExactly("CARC-197");
    }

    @Test
    void unknownCptNotInPriorAuthSet_returnsEmpty() {
        Optional<AdjudicationDecision> result = rule.evaluate(claim("99999"), null, null, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void whitespaceAroundCpt_doesNotMatch() {
        Optional<AdjudicationDecision> result = rule.evaluate(claim("70553 "), null, null, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void orderIs20() {
        assertThat(rule.order()).isEqualTo(20);
    }

    // --- Mutation-driven coverage of null branch ---

    @Test
    void nullProcedureCode_returnsEmpty_doesNotThrow() {
        // Without the `cpt != null` short-circuit the rule would call
        // PRIOR_AUTH_REQUIRED.contains(null) which throws NPE on the
        // immutable Set.of(...) instance.
        Optional<AdjudicationDecision> result = rule.evaluate(
                claim(null), null, null, List.of());
        assertThat(result).isEmpty();
    }
}
