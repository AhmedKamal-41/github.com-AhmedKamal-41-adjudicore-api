package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AmountValidatorTest {

    private final AmountValidator validator = new AmountValidator();

    private static Member member(String planCode) {
        return Member.builder()
                .memberId("M001")
                .firstName("Test")
                .lastName("Member")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .planCode(planCode)
                .eligibilityStart(LocalDate.of(2024, 1, 1))
                .build();
    }

    private static Claim claimWithAmount(BigDecimal amount) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 20))
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(amount)
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    @Test
    void happyPath_150ForPpoGold_returnsValid() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("150.00")), member("PPO_GOLD"), null);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void zeroAmount_returnsCarc45() {
        ValidationResult result = validator.validate(
                claimWithAmount(BigDecimal.ZERO), member("PPO_GOLD"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-45");
    }

    @Test
    void negativeAmount_returnsCarc45() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("-1.00")), member("PPO_GOLD"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-45");
    }

    @Test
    void hmoSilver_atExactly50000_isValid_boundary() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("50000.00")), member("HMO_SILVER"), null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void hmoSilver_at50000_01_isInvalidCarc45_boundary() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("50000.01")), member("HMO_SILVER"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-45");
    }

    @Test
    void epoBronze_at25001_isInvalidCarc45() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("25001.00")), member("EPO_BRONZE"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-45");
    }

    @Test
    void ppoGold_at99999_noCap_isValid() {
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("99999.00")), member("PPO_GOLD"), null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void orderIs40() {
        assertThat(validator.order()).isEqualTo(40);
    }

    // --- Mutation-driven coverage of null branches ---

    @Test
    void nullAmount_returnsCarc45() {
        ValidationResult result = validator.validate(
                claimWithAmount(null), member("PPO_GOLD"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-45");
    }

    @Test
    void nullMember_skipsCapCheck_returnsValid_evenIfOverAnyCap() {
        // With member null the plan-cap lookup is never reached, so a 60k
        // amount that would trip the HMO/EPO cap passes through as valid.
        ValidationResult result = validator.validate(
                claimWithAmount(new BigDecimal("60000.00")), null, null);

        assertThat(result.isValid()).isTrue();
    }
}
