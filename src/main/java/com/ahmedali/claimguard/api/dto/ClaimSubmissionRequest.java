package com.ahmedali.claimguard.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimSubmissionRequest(

        @NotBlank
        @Pattern(regexp = "^M\\d{3,6}$",
                message = "Member ID must start with M followed by 3-6 digits")
        String memberId,

        @NotBlank
        @Pattern(regexp = "^\\d{10}$",
                message = "NPI must be exactly 10 digits")
        String providerNpi,

        @NotNull
        @PastOrPresent(message = "Service date cannot be in the future")
        LocalDate serviceDate,

        @NotBlank
        @Pattern(regexp = "^\\d{5}$",
                message = "Procedure code (CPT) must be exactly 5 digits")
        String procedureCode,

        @NotBlank
        @Pattern(regexp = "^[A-Z]\\d{2}(\\.\\d{1,4})?$",
                message = "Diagnosis code must be valid ICD-10 format (e.g. J45 or J45.909)")
        String diagnosisCode,

        @NotNull
        @DecimalMin(value = "0.01", message = "Billed amount must be at least $0.01")
        @DecimalMax(value = "100000.00", message = "Billed amount cannot exceed $100,000.00")
        @Digits(integer = 6, fraction = 2)
        BigDecimal billedAmount
) {
}
