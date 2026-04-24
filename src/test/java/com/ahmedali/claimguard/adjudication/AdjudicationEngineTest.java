package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdjudicationEngineTest {

    @Test
    void allRulesPassThrough_returnsRule4Approve() {
        AdjudicationRule r1 = stub(10, Optional.empty());
        AdjudicationRule r2 = stub(20, Optional.empty());
        AdjudicationRule r3 = stub(30, Optional.empty());
        AdjudicationRule r4 = stub(40, Optional.of(
                AdjudicationDecision.approve(new BigDecimal("125.00"), "Approved")));
        AdjudicationEngine engine = new AdjudicationEngine(List.of(r1, r2, r3, r4));

        AdjudicationDecision decision = engine.adjudicate(
                mock(Claim.class), mock(Member.class), mock(Provider.class), List.of());

        assertThat(decision.outcome()).isEqualTo(AdjudicationDecision.Outcome.APPROVE);
    }

    @Test
    void rule1Denies_shortCircuits_rule2And3And4NeverCalled() {
        AdjudicationRule r1 = stub(10, Optional.of(AdjudicationDecision.deny("CARC-18", "dup")));
        AdjudicationRule r2 = stub(20, Optional.empty());
        AdjudicationRule r3 = stub(30, Optional.empty());
        AdjudicationRule r4 = stub(40, Optional.empty());
        AdjudicationEngine engine = new AdjudicationEngine(List.of(r1, r2, r3, r4));

        AdjudicationDecision decision = engine.adjudicate(
                mock(Claim.class), mock(Member.class), mock(Provider.class), List.of());

        assertThat(decision.outcome()).isEqualTo(AdjudicationDecision.Outcome.DENY);
        verify(r2, never()).evaluate(any(), any(), any(), any());
        verify(r3, never()).evaluate(any(), any(), any(), any());
        verify(r4, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void rule2Pends_shortCircuits_rule3And4NeverCalled() {
        AdjudicationRule r1 = stub(10, Optional.empty());
        AdjudicationRule r2 = stub(20, Optional.of(AdjudicationDecision.pend("CARC-197", "auth")));
        AdjudicationRule r3 = stub(30, Optional.empty());
        AdjudicationRule r4 = stub(40, Optional.empty());
        AdjudicationEngine engine = new AdjudicationEngine(List.of(r1, r2, r3, r4));

        AdjudicationDecision decision = engine.adjudicate(
                mock(Claim.class), mock(Member.class), mock(Provider.class), List.of());

        assertThat(decision.outcome()).isEqualTo(AdjudicationDecision.Outcome.PEND);
        verify(r3, never()).evaluate(any(), any(), any(), any());
        verify(r4, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void rule3Denies_shortCircuits_rule4NeverCalled() {
        AdjudicationRule r1 = stub(10, Optional.empty());
        AdjudicationRule r2 = stub(20, Optional.empty());
        AdjudicationRule r3 = stub(30, Optional.of(AdjudicationDecision.deny("CARC-119", "cap")));
        AdjudicationRule r4 = stub(40, Optional.empty());
        AdjudicationEngine engine = new AdjudicationEngine(List.of(r1, r2, r3, r4));

        AdjudicationDecision decision = engine.adjudicate(
                mock(Claim.class), mock(Member.class), mock(Provider.class), List.of());

        assertThat(decision.outcome()).isEqualTo(AdjudicationDecision.Outcome.DENY);
        assertThat(decision.reasonCodes()).containsExactly("CARC-119");
        verify(r4, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void rulesExecuteInOrderSequence_regardlessOfInjectionOrder() {
        List<Integer> calls = new ArrayList<>();
        AdjudicationRule r1 = recording(10, calls);
        AdjudicationRule r2 = recording(20, calls);
        AdjudicationRule r3 = recording(30, calls);
        AdjudicationRule r4 = recordingTerminal(40, calls);

        AdjudicationEngine engine = new AdjudicationEngine(List.of(r4, r2, r3, r1));

        engine.adjudicate(mock(Claim.class), mock(Member.class), mock(Provider.class), List.of());

        assertThat(calls).containsExactly(10, 20, 30, 40);

        InOrder order = inOrder(r1, r2, r3, r4);
        order.verify(r1).evaluate(any(), any(), any(), any());
        order.verify(r2).evaluate(any(), any(), any(), any());
        order.verify(r3).evaluate(any(), any(), any(), any());
        order.verify(r4).evaluate(any(), any(), any(), any());
    }

    @Test
    void emptyRuleList_throwsIllegalStateException() {
        AdjudicationEngine engine = new AdjudicationEngine(List.of());

        assertThatThrownBy(() -> engine.adjudicate(
                mock(Claim.class), mock(Member.class), mock(Provider.class), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No adjudication rules configured");
    }

    private static AdjudicationRule stub(int order, Optional<AdjudicationDecision> result) {
        AdjudicationRule rule = mock(AdjudicationRule.class);
        when(rule.order()).thenReturn(order);
        when(rule.evaluate(any(), any(), any(), any())).thenReturn(result);
        return rule;
    }

    private static AdjudicationRule recording(int order, List<Integer> calls) {
        AdjudicationRule rule = mock(AdjudicationRule.class);
        when(rule.order()).thenReturn(order);
        when(rule.evaluate(any(), any(), any(), any())).thenAnswer(inv -> {
            calls.add(order);
            return Optional.empty();
        });
        return rule;
    }

    private static AdjudicationRule recordingTerminal(int order, List<Integer> calls) {
        AdjudicationRule rule = mock(AdjudicationRule.class);
        when(rule.order()).thenReturn(order);
        when(rule.evaluate(any(), any(), any(), any())).thenAnswer(inv -> {
            calls.add(order);
            return Optional.of(AdjudicationDecision.approve(new BigDecimal("125.00"), "ok"));
        });
        return rule;
    }
}
