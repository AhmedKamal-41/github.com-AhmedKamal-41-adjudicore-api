package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;

import java.util.List;
import java.util.Optional;

public interface AdjudicationRule {

    Optional<AdjudicationDecision> evaluate(
            Claim claim,
            Member member,
            Provider provider,
            List<Claim> memberHistory);

    int order();
}
