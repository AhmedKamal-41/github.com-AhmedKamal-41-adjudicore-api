package com.ahmedali.claimguard.service;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.api.dto.ClaimSubmissionRequest;
import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimAuditLog;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.exception.ClaimNotFoundException;
import com.ahmedali.claimguard.repository.ClaimAuditLogRepository;
import com.ahmedali.claimguard.repository.ClaimRepository;
import com.ahmedali.claimguard.adjudication.AdjudicationEngine;
import com.ahmedali.claimguard.repository.MemberRepository;
import com.ahmedali.claimguard.repository.ProviderRepository;
import com.ahmedali.claimguard.validation.ClaimValidationPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimAuditLogRepository claimAuditLogRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ClaimValidationPipeline validationPipeline;

    @Mock
    private AdjudicationEngine adjudicationEngine;

    @InjectMocks
    private ClaimService claimService;

    private static ClaimSubmissionRequest validRequest() {
        return new ClaimSubmissionRequest(
                "M001",
                "1234567890",
                LocalDate.now().minusDays(5),
                "99213",
                "J45.909",
                new BigDecimal("150.00")
        );
    }

    @Test
    void submitClaim_persistsClaimWithStatusSubmitted() {
        ClaimSubmissionRequest request = validRequest();
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        claimService.submitClaim(request);

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        Claim saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(saved.getMemberId()).isEqualTo("M001");
        assertThat(saved.getProviderNpi()).isEqualTo("1234567890");
        assertThat(saved.getBilledAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void submitClaim_writesInitialAuditLogRow() {
        ClaimSubmissionRequest request = validRequest();
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimResponse response = claimService.submitClaim(request);

        ArgumentCaptor<ClaimAuditLog> captor = ArgumentCaptor.forClass(ClaimAuditLog.class);
        verify(claimAuditLogRepository).save(captor.capture());
        ClaimAuditLog log = captor.getValue();
        assertThat(log.getClaimId()).isEqualTo(response.claimId());
        assertThat(log.getPreviousStatus()).isNull();
        assertThat(log.getNewStatus()).isEqualTo("SUBMITTED");
        assertThat(log.getChangedBy()).isEqualTo("SYSTEM");
        assertThat(log.getReasonCodes()).isNull();
        assertThat(log.getNotes()).isEqualTo("Claim submitted via API");
    }

    @Test
    void submitClaim_generatesClaimIdInExpectedFormat() {
        ClaimSubmissionRequest request = validRequest();
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        ClaimResponse response = claimService.submitClaim(request);

        assertThat(response.claimId()).matches("^CLM-" + today + "-[A-Z0-9]{6}$");
    }

    @Test
    void submitClaim_setsSubmissionDateToToday() {
        ClaimSubmissionRequest request = validRequest();
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimResponse response = claimService.submitClaim(request);

        assertThat(response.submissionDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void getClaim_returnsClaimResponseWhenClaimExists() {
        Claim claim = Claim.builder()
                .claimId("CLM-20260423-ABC123")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2026, 4, 15))
                .submissionDate(LocalDate.of(2026, 4, 16))
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
        when(claimRepository.findByClaimId("CLM-20260423-ABC123")).thenReturn(Optional.of(claim));

        ClaimResponse response = claimService.getClaim("CLM-20260423-ABC123");

        assertThat(response.claimId()).isEqualTo("CLM-20260423-ABC123");
        assertThat(response.memberId()).isEqualTo("M001");
        assertThat(response.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.billedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void getClaim_throwsClaimNotFoundExceptionWhenMissing() {
        when(claimRepository.findByClaimId("CLM-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getClaim("CLM-MISSING"))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessage("Claim not found: CLM-MISSING");
    }
}
