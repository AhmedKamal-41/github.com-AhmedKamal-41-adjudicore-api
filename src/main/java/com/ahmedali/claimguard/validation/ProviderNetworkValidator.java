package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

/**
 * HMO and EPO plans enforce strict network: out-of-network care is rejected
 * at validation. PPO plans accept out-of-network claims at this stage and
 * only reduce {@code allowed_amount} during adjudication. This mirrors real
 * payer behavior.
 */
@Component
public class ProviderNetworkValidator implements ClaimValidator {

    @Override
    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        if (provider == null) {
            return ValidationResult.invalid("CARC-B7", "Provider not certified / not eligible");
        }
        if (Boolean.FALSE.equals(provider.getIsInNetwork())
                && member != null
                && enforcesStrictNetwork(member.getPlanCode())) {
            return ValidationResult.invalid(
                    "CARC-242", "Services not provided by network provider");
        }
        return ValidationResult.valid();
    }

    private boolean enforcesStrictNetwork(String planCode) {
        if (planCode == null) {
            return false;
        }
        return planCode.startsWith("HMO") || planCode.startsWith("EPO");
    }

    @Override
    public int order() {
        return 20;
    }
}
