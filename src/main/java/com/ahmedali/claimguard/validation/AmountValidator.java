package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AmountValidator implements ClaimValidator {

    private static final Map<String, BigDecimal> PLAN_CAPS = Map.of(
            "HMO_SILVER", new BigDecimal("50000.00"),
            "EPO_BRONZE", new BigDecimal("25000.00")
    );

    @Override
    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        BigDecimal amount = claim.getBilledAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.invalid("CARC-45", "Charge exceeds fee schedule");
        }
        if (member != null) {
            BigDecimal cap = PLAN_CAPS.get(member.getPlanCode());
            if (cap != null && amount.compareTo(cap) > 0) {
                return ValidationResult.invalid("CARC-45", "Charge exceeds fee schedule");
            }
        }
        return ValidationResult.valid();
    }

    @Override
    public int order() {
        return 40;
    }
}
