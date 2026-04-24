package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Defense-in-depth re-validation of CPT and ICD-10 format. The DTO-layer
 * Bean Validation catches bad API inputs, but entities can be constructed
 * through other paths (seed data loads, future admin endpoints), so we
 * re-check here before the claim moves past SUBMITTED.
 */
@Component
public class CodeFormatValidator implements ClaimValidator {

    private static final Pattern CPT_PATTERN = Pattern.compile("^\\d{5}$");
    private static final Pattern ICD10_PATTERN = Pattern.compile("^[A-Z]\\d{2}(\\.\\d{1,4})?$");

    @Override
    public ValidationResult validate(Claim claim, Member member, Provider provider) {
        String cpt = claim.getProcedureCode();
        String icd = claim.getDiagnosisCode();

        if (cpt == null || !CPT_PATTERN.matcher(cpt).matches()) {
            return ValidationResult.invalid("CARC-181", "Procedure code format invalid");
        }
        if (icd == null || !ICD10_PATTERN.matcher(icd).matches()) {
            return ValidationResult.invalid("CARC-181", "Diagnosis code format invalid");
        }
        return ValidationResult.valid();
    }

    @Override
    public int order() {
        return 50;
    }
}
