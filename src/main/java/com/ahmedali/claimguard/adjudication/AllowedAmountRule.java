package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class AllowedAmountRule implements AdjudicationRule {

    private final AllowedAmountCalculator calculator;

    public AllowedAmountRule(AllowedAmountCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public Optional<AdjudicationDecision> evaluate(Claim claim, Member member, Provider provider,
                                                   List<Claim> memberHistory) {
        BigDecimal allowed = calculator.calculate(claim, provider);
        return Optional.of(AdjudicationDecision.approve(allowed, "Approved at fee schedule rate"));
    }

    @Override
    public int order() {
        return 40;
    }
}
