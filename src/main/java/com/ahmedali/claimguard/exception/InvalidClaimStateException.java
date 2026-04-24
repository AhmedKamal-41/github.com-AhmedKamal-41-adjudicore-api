package com.ahmedali.claimguard.exception;

import com.ahmedali.claimguard.domain.ClaimStatus;

public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(String action, ClaimStatus currentStatus, ClaimStatus expectedStatus) {
        super("Cannot " + action + " claim in " + currentStatus
                + " status; expected " + expectedStatus);
    }
}
