package com.ahmedali.claimguard.api.dto;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClaimResponse(
        String claimId,
        String memberId,
        String providerNpi,
        LocalDate serviceDate,
        String procedureCode,
        String diagnosisCode,
        BigDecimal billedAmount,
        BigDecimal allowedAmount,
        ClaimStatus status,
        LocalDate submissionDate,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> reasonCodes
) {

    public static ClaimResponse from(Claim entity) {
        return from(entity, null, null);
    }

    public static ClaimResponse from(Claim entity, String message, List<String> reasonCodes) {
        return new ClaimResponse(
                entity.getClaimId(),
                entity.getMemberId(),
                entity.getProviderNpi(),
                entity.getServiceDate(),
                entity.getProcedureCode(),
                entity.getDiagnosisCode(),
                entity.getBilledAmount(),
                entity.getAllowedAmount(),
                entity.getStatus(),
                entity.getSubmissionDate(),
                message,
                reasonCodes == null ? null : List.copyOf(reasonCodes)
        );
    }
}
