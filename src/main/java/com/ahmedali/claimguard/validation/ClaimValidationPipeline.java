package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ClaimValidationPipeline {

    private final List<ClaimValidator> validators;

    public ClaimValidationPipeline(List<ClaimValidator> validators) {
        List<ClaimValidator> sorted = new ArrayList<>(validators);
        sorted.sort(Comparator.comparingInt(ClaimValidator::order));
        this.validators = List.copyOf(sorted);
    }

    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        List<String> rejectCodes = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        for (ClaimValidator validator : validators) {
            ValidationResult result = validator.validate(claim, member, provider);
            if (!result.isValid()) {
                rejectCodes.addAll(result.rejectCodes());
                messages.addAll(result.messages());
            }
        }

        if (rejectCodes.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(rejectCodes, messages);
    }
}
