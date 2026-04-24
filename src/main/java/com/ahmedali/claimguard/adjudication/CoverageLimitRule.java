package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CoverageLimitRule implements AdjudicationRule {

    private static final Map<String, BigDecimal> ANNUAL_CAPS = Map.of(
            "PPO_GOLD", new BigDecimal("100000.00"),
            "HMO_SILVER", new BigDecimal("50000.00"),
            "EPO_BRONZE", new BigDecimal("25000.00")
            // MEDICARE_A intentionally absent → unlimited
    );

    private final AllowedAmountCalculator calculator;

    public CoverageLimitRule(AllowedAmountCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public Optional<AdjudicationDecision> evaluate(Claim claim, Member member, Provider provider,
                                                   List<Claim> memberHistory) {
        if (member == null) {
            return Optional.empty();
        }
        BigDecimal cap = ANNUAL_CAPS.get(member.getPlanCode());
        if (cap == null) {
            return Optional.empty();
        }

        int currentYear = claim.getServiceDate().getYear();
        BigDecimal currentYearSum = BigDecimal.ZERO;
        for (Claim prior : memberHistory) {
            if (prior.getStatus() != ClaimStatus.APPROVED) {
                continue;
            }
            if (prior.getServiceDate() == null || prior.getServiceDate().getYear() != currentYear) {
                continue;
            }
            if (prior.getAllowedAmount() != null) {
                currentYearSum = currentYearSum.add(prior.getAllowedAmount());
            }
        }

        BigDecimal estimate = calculator.calculate(claim, provider);
        BigDecimal projected = currentYearSum.add(estimate);

        if (projected.compareTo(cap) > 0) {
            String notes = "Annual coverage limit of $" + cap.toPlainString()
                    + " exceeded; current year paid: $" + currentYearSum.toPlainString()
                    + ", this claim estimated: $" + estimate.toPlainString();
            return Optional.of(AdjudicationDecision.deny("CARC-119", notes));
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 30;
    }
}
