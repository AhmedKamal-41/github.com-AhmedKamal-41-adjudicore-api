package com.ahmedali.claimguard.api;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.api.dto.ClaimSubmissionRequest;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.exception.ClaimNotFoundException;
import com.ahmedali.claimguard.exception.GlobalExceptionHandler;
import com.ahmedali.claimguard.exception.InvalidClaimStateException;
import com.ahmedali.claimguard.service.ClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import(GlobalExceptionHandler.class)
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimService claimService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static ClaimSubmissionRequest validRequest() {
        return new ClaimSubmissionRequest(
                "M001",
                "1234567890",
                LocalDate.of(2026, 4, 15),
                "99213",
                "J45.909",
                new BigDecimal("150.00")
        );
    }

    private static ClaimResponse sampleResponse(String claimId) {
        return new ClaimResponse(
                claimId,
                "M001",
                "1234567890",
                LocalDate.of(2026, 4, 15),
                "99213",
                "J45.909",
                new BigDecimal("150.00"),
                null,
                ClaimStatus.SUBMITTED,
                LocalDate.of(2026, 4, 23),
                null,
                null
        );
    }

    @Test
    void postValidClaim_returns201WithClaimResponseBody() throws Exception {
        String claimId = "CLM-20260423-A7F3K2";
        when(claimService.submitClaim(any(ClaimSubmissionRequest.class)))
                .thenReturn(sampleResponse(claimId));

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.memberId").value("M001"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.billedAmount").value(150.00));
    }

    @Test
    void postValidClaim_setsLocationHeader() throws Exception {
        String claimId = "CLM-20260423-A7F3K2";
        when(claimService.submitClaim(any(ClaimSubmissionRequest.class)))
                .thenReturn(sampleResponse(claimId));

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/claims/" + claimId));
    }

    @Test
    void postMissingMemberId_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "providerNpi": "1234567890",
                  "serviceDate": "2026-04-15",
                  "procedureCode": "99213",
                  "diagnosisCode": "J45.909",
                  "billedAmount": 150.00
                }
                """;

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.memberId").exists());
    }

    @Test
    void postInvalidNpi_returns400WithFieldError() throws Exception {
        ClaimSubmissionRequest req = new ClaimSubmissionRequest(
                "M001",
                "123456789",
                LocalDate.of(2026, 4, 15),
                "99213",
                "J45.909",
                new BigDecimal("150.00")
        );

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.providerNpi").exists());
    }

    @Test
    void postNegativeBilledAmount_returns400WithFieldError() throws Exception {
        ClaimSubmissionRequest req = new ClaimSubmissionRequest(
                "M001",
                "1234567890",
                LocalDate.of(2026, 4, 15),
                "99213",
                "J45.909",
                new BigDecimal("-10.00")
        );

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.billedAmount").exists());
    }

    @Test
    void postMalformedJsonBody_returns400WithMalformedMessage() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void getExistingClaimId_returns200WithFullResponse() throws Exception {
        String claimId = "CLM-20260423-A7F3K2";
        when(claimService.getClaim(eq(claimId))).thenReturn(sampleResponse(claimId));

        mockMvc.perform(get("/api/v1/claims/" + claimId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.memberId").value("M001"))
                .andExpect(jsonPath("$.providerNpi").value("1234567890"))
                .andExpect(jsonPath("$.serviceDate").value("2026-04-15"))
                .andExpect(jsonPath("$.procedureCode").value("99213"))
                .andExpect(jsonPath("$.diagnosisCode").value("J45.909"))
                .andExpect(jsonPath("$.billedAmount").value(150.00))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionDate").value("2026-04-23"));
    }

    @Test
    void getUnknownClaimId_returns404WithErrorResponseShape() throws Exception {
        when(claimService.getClaim(eq("CLM-MISSING")))
                .thenThrow(new ClaimNotFoundException("CLM-MISSING"));

        mockMvc.perform(get("/api/v1/claims/CLM-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Claim not found: CLM-MISSING"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void postValidate_onValidClaim_returns200WithValidatedStatus() throws Exception {
        String claimId = "CLM-20260423-VAL001";
        ClaimResponse validated = new ClaimResponse(
                claimId, "M001", "1234567890",
                LocalDate.of(2026, 4, 15), "99213", "J45.909",
                new BigDecimal("150.00"), null,
                ClaimStatus.VALIDATED, LocalDate.of(2026, 4, 23), null, null);
        when(claimService.validateClaim(eq(claimId))).thenReturn(validated);

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void postValidate_onRejectedClaim_returns200WithRejectedStatusAndMessage() throws Exception {
        String claimId = "CLM-20260423-REJ001";
        ClaimResponse rejected = new ClaimResponse(
                claimId, "M005", "1234567890",
                LocalDate.of(2026, 4, 15), "99213", "J45.909",
                new BigDecimal("150.00"), null,
                ClaimStatus.REJECTED, LocalDate.of(2026, 4, 23),
                "Expenses incurred after coverage terminated",
                List.of("CARC-27"));
        when(claimService.validateClaim(eq(claimId))).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Expenses incurred after coverage terminated"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("CARC-27"));
    }

    @Test
    void postValidate_onClaimInWrongState_returns409WithErrorResponse() throws Exception {
        String claimId = "CLM-20260423-WRONG";
        when(claimService.validateClaim(eq(claimId)))
                .thenThrow(new InvalidClaimStateException(
                        "validate", ClaimStatus.APPROVED, ClaimStatus.SUBMITTED));

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/validate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot validate claim in APPROVED status; expected SUBMITTED"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void postValidate_onMissingClaim_returns404WithErrorResponse() throws Exception {
        when(claimService.validateClaim(eq("CLM-MISSING")))
                .thenThrow(new ClaimNotFoundException("CLM-MISSING"));

        mockMvc.perform(post("/api/v1/claims/CLM-MISSING/validate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Claim not found: CLM-MISSING"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void postAdjudicate_onValidatedClaim_returns200WithApprovedStatus() throws Exception {
        String claimId = "CLM-20260423-APP001";
        ClaimResponse approved = new ClaimResponse(
                claimId, "M001", "1234567890",
                LocalDate.of(2026, 4, 15), "99213", "J45.909",
                new BigDecimal("150.00"), new BigDecimal("125.00"),
                ClaimStatus.APPROVED, LocalDate.of(2026, 4, 23),
                "Approved at fee schedule rate",
                null);
        when(claimService.adjudicateClaim(eq(claimId))).thenReturn(approved);

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/adjudicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.allowedAmount").value(125.00));
    }

    @Test
    void postAdjudicate_resultingInDenied_returns200WithDeniedStatusAndRejectCodeInMessage() throws Exception {
        String claimId = "CLM-20260423-DEN001";
        ClaimResponse denied = new ClaimResponse(
                claimId, "M001", "1234567890",
                LocalDate.of(2026, 4, 15), "99213", "J45.909",
                new BigDecimal("150.00"), null,
                ClaimStatus.DENIED, LocalDate.of(2026, 4, 23),
                "Duplicate of claim CLM-PRIOR approved on 2026-04-16",
                List.of("CARC-18"));
        when(claimService.adjudicateClaim(eq(claimId))).thenReturn(denied);

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/adjudicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.message").value(
                        "Duplicate of claim CLM-PRIOR approved on 2026-04-16"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("CARC-18"));
    }

    @Test
    void postAdjudicate_resultingInPend_returns200WithValidatedStatusAndMessageMentionsAuth() throws Exception {
        String claimId = "CLM-20260423-PND001";
        ClaimResponse pended = new ClaimResponse(
                claimId, "M001", "1234567890",
                LocalDate.of(2026, 4, 15), "70553", "J45.909",
                new BigDecimal("2500.00"), null,
                ClaimStatus.VALIDATED, LocalDate.of(2026, 4, 23),
                "Procedure 70553 requires prior authorization",
                List.of("CARC-197"));
        when(claimService.adjudicateClaim(eq(claimId))).thenReturn(pended);

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/adjudicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.message").value(
                        "Procedure 70553 requires prior authorization"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("CARC-197"));
    }

    @Test
    void postAdjudicate_onNonValidatedClaim_returns409() throws Exception {
        String claimId = "CLM-20260423-WRONG";
        when(claimService.adjudicateClaim(eq(claimId)))
                .thenThrow(new InvalidClaimStateException(
                        "adjudicate", ClaimStatus.SUBMITTED, ClaimStatus.VALIDATED));

        mockMvc.perform(post("/api/v1/claims/" + claimId + "/adjudicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Cannot adjudicate claim in SUBMITTED status; expected VALIDATED"));
    }

    @Test
    void postAdjudicate_onMissingClaim_returns404() throws Exception {
        when(claimService.adjudicateClaim(eq("CLM-MISSING")))
                .thenThrow(new ClaimNotFoundException("CLM-MISSING"));

        mockMvc.perform(post("/api/v1/claims/CLM-MISSING/adjudicate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Claim not found: CLM-MISSING"));
    }
}
