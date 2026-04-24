package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DuplicateClaimRule implements AdjudicationRule {

    @Override
    public Optional<AdjudicationDecision> evaluate(Claim claim, Member member, Provider provider,
                                                   List<Claim> memberHistory) {
        for (Claim prior : memberHistory) {
            if (Objects.equals(prior.getClaimId(), claim.getClaimId())) {
                continue;
            }
            if (prior.getStatus() != ClaimStatus.APPROVED) {
                continue;
            }
            if (!Objects.equals(prior.getProcedureCode(), claim.getProcedureCode())) {
                continue;
            }
            if (!Objects.equals(prior.getServiceDate(), claim.getServiceDate())) {
                continue;
            }
            String notes = "Duplicate of claim " + prior.getClaimId()
                    + " approved on " + prior.getSubmissionDate();
            return Optional.of(AdjudicationDecision.deny("CARC-18", notes));
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 10;
    }
}
