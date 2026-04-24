package com.ahmedali.claimguard.exception;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(String claimId) {
        super("Claim not found: " + claimId);
    }
}
