package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class PriorAuthRule implements AdjudicationRule {

    private static final Set<String> PRIOR_AUTH_REQUIRED = Set.of(
            "70553", // MRI Brain with and without contrast
            "27447", // Total knee arthroplasty
            "43644", // Laparoscopic gastric bypass
            "29827", // Arthroscopy shoulder with rotator cuff repair
            "22612"  // Arthrodesis posterior lumbar spine
    );

    @Override
    public Optional<AdjudicationDecision> evaluate(Claim claim, Member member, Provider provider,
                                                   List<Claim> memberHistory) {
        String cpt = claim.getProcedureCode();
        if (cpt != null && PRIOR_AUTH_REQUIRED.contains(cpt)) {
            String notes = "Procedure " + cpt + " requires prior authorization";
            return Optional.of(AdjudicationDecision.pend("CARC-197", notes));
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 20;
    }
}
