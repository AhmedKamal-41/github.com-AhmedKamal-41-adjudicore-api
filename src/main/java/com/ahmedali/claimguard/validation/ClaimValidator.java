package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;

public interface ClaimValidator {

    ValidationResult validate(Claim claim, Member member, Provider provider);

    int order();
}
