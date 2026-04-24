package com.ahmedali.claimguard.service;

import com.ahmedali.claimguard.adjudication.AdjudicationDecision;
import com.ahmedali.claimguard.adjudication.AdjudicationEngine;
import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.api.dto.ClaimSubmissionRequest;
import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimAuditLog;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import com.ahmedali.claimguard.exception.ClaimNotFoundException;
import com.ahmedali.claimguard.exception.InvalidClaimStateException;
import com.ahmedali.claimguard.repository.ClaimAuditLogRepository;
import com.ahmedali.claimguard.repository.ClaimRepository;
import com.ahmedali.claimguard.repository.MemberRepository;
import com.ahmedali.claimguard.repository.ProviderRepository;
import com.ahmedali.claimguard.validation.ClaimValidationPipeline;
import com.ahmedali.claimguard.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ClaimService {

    private static final String CLAIM_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CLAIM_ID_SUFFIX_LENGTH = 6;
    private static final DateTimeFormatter CLAIM_ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ClaimRepository claimRepository;
    private final ClaimAuditLogRepository claimAuditLogRepository;
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final ClaimValidationPipeline validationPipeline;
    private final AdjudicationEngine adjudicationEngine;
    private final SecureRandom random = new SecureRandom();

    public ClaimService(ClaimRepository claimRepository,
                        ClaimAuditLogRepository claimAuditLogRepository,
                        MemberRepository memberRepository,
                        ProviderRepository providerRepository,
                        ClaimValidationPipeline validationPipeline,
                        AdjudicationEngine adjudicationEngine) {
        this.claimRepository = claimRepository;
        this.claimAuditLogRepository = claimAuditLogRepository;
        this.memberRepository = memberRepository;
        this.providerRepository = providerRepository;
        this.validationPipeline = validationPipeline;
        this.adjudicationEngine = adjudicationEngine;
    }

    @Transactional
    public ClaimResponse submitClaim(ClaimSubmissionRequest request) {
        LocalDate today = LocalDate.now();
        String claimId = generateClaimId(today);

        Claim claim = Claim.builder()
                .claimId(claimId)
                .memberId(request.memberId())
                .providerNpi(request.providerNpi())
                .serviceDate(request.serviceDate())
                .submissionDate(today)
                .procedureCode(request.procedureCode())
                .diagnosisCode(request.diagnosisCode())
                .billedAmount(request.billedAmount())
                .status(ClaimStatus.SUBMITTED)
                .build();

        Claim saved = claimRepository.save(claim);

        // TODO: Replace "SYSTEM" with authenticated principal once auth is introduced.
        //       Audit log is append-only, so historical SYSTEM entries remain correct.
        ClaimAuditLog auditLog = ClaimAuditLog.builder()
                .claimId(saved.getClaimId())
                .previousStatus(null)
                .newStatus(ClaimStatus.SUBMITTED.name())
                .changedBy("SYSTEM")
                .reasonCodes(null)
                .notes("Claim submitted via API")
                .build();
        claimAuditLogRepository.save(auditLog);

        return ClaimResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaim(String claimId) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
        return ClaimResponse.from(claim);
    }

    @Transactional
    public ClaimResponse validateClaim(String claimId) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new InvalidClaimStateException(
                    "validate", claim.getStatus(), ClaimStatus.SUBMITTED);
        }

        Member member = memberRepository.findByMemberId(claim.getMemberId()).orElse(null);
        Provider provider = providerRepository.findByNpi(claim.getProviderNpi()).orElse(null);

        ValidationResult result = validationPipeline.validate(claim, member, provider);

        if (result.isValid()) {
            claim.setStatus(ClaimStatus.VALIDATED);
            Claim saved = claimRepository.save(claim);

            ClaimAuditLog auditLog = ClaimAuditLog.builder()
                    .claimId(saved.getClaimId())
                    .previousStatus(ClaimStatus.SUBMITTED.name())
                    .newStatus(ClaimStatus.VALIDATED.name())
                    .changedBy("SYSTEM")
                    .reasonCodes(null)
                    .notes("Validation passed")
                    .build();
            claimAuditLogRepository.save(auditLog);

            return ClaimResponse.from(saved);
        }

        String joinedCodes = String.join(",", result.rejectCodes());
        String joinedMessages = String.join("; ", result.messages());

        claim.setStatus(ClaimStatus.REJECTED);
        Claim saved = claimRepository.save(claim);

        ClaimAuditLog auditLog = ClaimAuditLog.builder()
                .claimId(saved.getClaimId())
                .previousStatus(ClaimStatus.SUBMITTED.name())
                .newStatus(ClaimStatus.REJECTED.name())
                .changedBy("SYSTEM")
                .reasonCodes(joinedCodes)
                .notes(joinedMessages)
                .build();
        claimAuditLogRepository.save(auditLog);

        return ClaimResponse.from(saved, joinedMessages, result.rejectCodes());
    }

    @Transactional
    public ClaimResponse adjudicateClaim(String claimId) {
        Claim claim = claimRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        if (claim.getStatus() != ClaimStatus.VALIDATED) {
            throw new InvalidClaimStateException(
                    "adjudicate", claim.getStatus(), ClaimStatus.VALIDATED);
        }

        Member member = memberRepository.findByMemberId(claim.getMemberId()).orElse(null);
        Provider provider = providerRepository.findByNpi(claim.getProviderNpi()).orElse(null);

        List<Claim> memberHistory = claimRepository.findAllByMemberId(claim.getMemberId())
                .stream()
                .filter(c -> !c.getClaimId().equals(claim.getClaimId()))
                .toList();

        AdjudicationDecision decision = adjudicationEngine.adjudicate(
                claim, member, provider, memberHistory);

        if (decision instanceof AdjudicationDecision.Approve approve) {
            claim.setStatus(ClaimStatus.APPROVED);
            claim.setAllowedAmount(approve.allowedAmount());
            Claim saved = claimRepository.save(claim);
            writeAudit(saved.getClaimId(), ClaimStatus.VALIDATED, ClaimStatus.APPROVED,
                    null, approve.notes());
            return ClaimResponse.from(saved, approve.notes(), null);
        }
        if (decision instanceof AdjudicationDecision.Deny deny) {
            claim.setStatus(ClaimStatus.DENIED);
            claim.setAllowedAmount(null);
            Claim saved = claimRepository.save(claim);
            writeAudit(saved.getClaimId(), ClaimStatus.VALIDATED, ClaimStatus.DENIED,
                    String.join(",", deny.reasonCodes()), deny.notes());
            return ClaimResponse.from(saved, deny.notes(), deny.reasonCodes());
        }
        if (decision instanceof AdjudicationDecision.Pend pend) {
            writeAudit(claim.getClaimId(), ClaimStatus.VALIDATED, ClaimStatus.VALIDATED,
                    String.join(",", pend.reasonCodes()), pend.notes());
            return ClaimResponse.from(claim, pend.notes(), pend.reasonCodes());
        }
        throw new IllegalStateException(
                "Unknown AdjudicationDecision variant: " + decision.getClass().getName());
    }

    private void writeAudit(String claimId, ClaimStatus previous, ClaimStatus next,
                            String reasonCodes, String notes) {
        ClaimAuditLog auditLog = ClaimAuditLog.builder()
                .claimId(claimId)
                .previousStatus(previous.name())
                .newStatus(next.name())
                .changedBy("SYSTEM")
                .reasonCodes(reasonCodes)
                .notes(notes)
                .build();
        claimAuditLogRepository.save(auditLog);
    }

    private String generateClaimId(LocalDate date) {
        StringBuilder suffix = new StringBuilder(CLAIM_ID_SUFFIX_LENGTH);
        for (int i = 0; i < CLAIM_ID_SUFFIX_LENGTH; i++) {
            suffix.append(CLAIM_ID_ALPHABET.charAt(random.nextInt(CLAIM_ID_ALPHABET.length())));
        }
        return "CLM-" + date.format(CLAIM_ID_DATE_FORMAT) + "-" + suffix;
    }
}
