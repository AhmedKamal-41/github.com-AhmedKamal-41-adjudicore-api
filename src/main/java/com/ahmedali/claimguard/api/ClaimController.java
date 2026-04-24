package com.ahmedali.claimguard.api;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.api.dto.ClaimSubmissionRequest;
import com.ahmedali.claimguard.api.dto.ErrorResponse;
import com.ahmedali.claimguard.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims", description = "Claim lifecycle endpoints")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(
            summary = "Submit a new claim",
            description = "Intake endpoint. Creates a claim in SUBMITTED status. "
                    + "Does not validate eligibility or adjudicate."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Claim created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody ClaimSubmissionRequest request) {
        ClaimResponse response = claimService.submitClaim(request);
        URI location = URI.create("/api/v1/claims/" + response.claimId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{claimId}")
    @Operation(
            summary = "Fetch a claim by id",
            description = "Returns the current state of a claim including status and allowed amount."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Claim found"),
            @ApiResponse(responseCode = "404", description = "Claim not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClaimResponse> getByClaimId(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.getClaim(claimId));
    }

    @PostMapping("/{claimId}/validate")
    @Operation(
            summary = "Validate a submitted claim",
            description = "Runs the validation pipeline (eligibility, network, service date, "
                    + "amount, code format). Transitions SUBMITTED → VALIDATED on success, "
                    + "SUBMITTED → REJECTED on any rule failure."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "404", description = "Claim not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Claim not in SUBMITTED status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClaimResponse> validate(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.validateClaim(claimId));
    }

    @PostMapping("/{claimId}/adjudicate")
    @Operation(
            summary = "Adjudicate a validated claim",
            description = "Runs the adjudication rule engine (duplicate detection, prior auth, "
                    + "coverage limit, allowed-amount calculation). Transitions VALIDATED → "
                    + "APPROVED / DENIED or stays VALIDATED with a PEND audit row."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adjudication completed"),
            @ApiResponse(responseCode = "404", description = "Claim not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Claim not in VALIDATED status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClaimResponse> adjudicate(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.adjudicateClaim(claimId));
    }
}
