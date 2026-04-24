package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ServiceDateValidator implements ClaimValidator {

    private static final long MAX_FILING_DAYS = 365;

    @Override
    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        LocalDate serviceDate = claim.getServiceDate();
        LocalDate submissionDate = claim.getSubmissionDate();

        if (serviceDate.isAfter(LocalDate.now())) {
            return ValidationResult.invalid(
                    "CARC-181", "Procedure code was invalid on the date of service");
        }
        if (submissionDate != null
                && ChronoUnit.DAYS.between(serviceDate, submissionDate) > MAX_FILING_DAYS) {
            return ValidationResult.invalid("CARC-29", "Time limit for filing has expired");
        }
        return ValidationResult.valid();
    }

    @Override
    public int order() {
        return 30;
    }
}
