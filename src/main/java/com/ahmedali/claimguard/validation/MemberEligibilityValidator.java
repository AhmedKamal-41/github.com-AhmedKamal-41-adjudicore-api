package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

@Component
public class MemberEligibilityValidator implements ClaimValidator {

    @Override
    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        if (member == null) {
            return ValidationResult.invalid("CARC-31", "Patient cannot be identified");
        }
        if (claim.getServiceDate().isBefore(member.getEligibilityStart())) {
            return ValidationResult.invalid("CARC-27", "Expenses incurred prior to coverage");
        }
        if (member.getEligibilityEnd() != null
                && claim.getServiceDate().isAfter(member.getEligibilityEnd())) {
            return ValidationResult.invalid("CARC-27", "Expenses incurred after coverage terminated");
        }
        return ValidationResult.valid();
    }

    @Override
    public int order() {
        return 10;
    }
}
