package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDateValidatorTest {

    private final ServiceDateValidator validator = new ServiceDateValidator();

    private static Claim claim(LocalDate serviceDate, LocalDate submissionDate) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(serviceDate)
                .submissionDate(submissionDate)
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    @Test
    void happyPath_thirtyDaysBefore_returnsValid() {
        LocalDate today = LocalDate.now();
        Claim c = claim(today.minusDays(30), today);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void futureServiceDate_returnsCarc181() {
        LocalDate today = LocalDate.now();
        Claim c = claim(today.plusDays(1), today);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-181");
    }

    @Test
    void exactly365DaysBack_isValid_boundary() {
        LocalDate submission = LocalDate.now();
        Claim c = claim(submission.minusDays(365), submission);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void threeSixtySixDaysBack_isInvalidCarc29_boundary() {
        LocalDate submission = LocalDate.now();
        Claim c = claim(submission.minusDays(366), submission);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-29");
    }

    @Test
    void sameDay_serviceDateEqualsSubmissionDate_isValid() {
        LocalDate today = LocalDate.now();
        Claim c = claim(today, today);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void orderIs30() {
        assertThat(validator.order()).isEqualTo(30);
    }

    // --- Mutation-driven coverage of null branches ---

    @Test
    void nullSubmissionDate_skipsFilingLimitCheck_returnsValid() {
        // Without the `submissionDate != null` short-circuit,
        // ChronoUnit.DAYS.between(serviceDate, null) would NPE.
        // A 400-day-old service date with a null submission date must still
        // pass (the filing window only matters once submission is recorded).
        LocalDate today = LocalDate.now();
        Claim c = claim(today.minusDays(400), null);

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isTrue();
    }
}
