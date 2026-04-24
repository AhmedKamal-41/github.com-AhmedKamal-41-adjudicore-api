package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class AdjudicationEngine {

    private final List<AdjudicationRule> rules;

    public AdjudicationEngine(List<AdjudicationRule> rules) {
        List<AdjudicationRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(AdjudicationRule::order));
        this.rules = List.copyOf(sorted);
    }

    public AdjudicationDecision adjudicate(Claim claim, Member member, Provider provider,
                                           List<Claim> memberHistory) {
        if (rules.isEmpty()) {
            throw new IllegalStateException("No adjudication rules configured");
        }
        for (AdjudicationRule rule : rules) {
            Optional<AdjudicationDecision> decision = rule.evaluate(claim, member, provider, memberHistory);
            if (decision.isPresent()) {
                return decision.get();
            }
        }
        throw new IllegalStateException(
                "Adjudication pipeline exhausted without a terminal decision; "
                        + "final rule must always return a decision");
    }
}
