package com.ahmedali.claimguard.api;

import com.ahmedali.claimguard.api.dto.ClaimResponse;
import com.ahmedali.claimguard.api.dto.ClaimSubmissionRequest;
import com.ahmedali.claimguard.service.ClaimService;
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
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody ClaimSubmissionRequest request) {
        ClaimResponse response = claimService.submitClaim(request);
        URI location = URI.create("/api/v1/claims/" + response.claimId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> getByClaimId(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.getClaim(claimId));
    }

    @PostMapping("/{claimId}/validate")
    public ResponseEntity<ClaimResponse> validate(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.validateClaim(claimId));
    }

    @PostMapping("/{claimId}/adjudicate")
    public ResponseEntity<ClaimResponse> adjudicate(@PathVariable String claimId) {
        return ResponseEntity.ok(claimService.adjudicateClaim(claimId));
    }
}
