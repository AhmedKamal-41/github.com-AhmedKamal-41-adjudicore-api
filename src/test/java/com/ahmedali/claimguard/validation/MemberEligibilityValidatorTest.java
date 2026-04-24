package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MemberEligibilityValidatorTest {

    private final MemberEligibilityValidator validator = new MemberEligibilityValidator();

    private static Claim claimOn(LocalDate serviceDate) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(serviceDate)
                .submissionDate(serviceDate.plusDays(1))
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    private static Member member(LocalDate start, LocalDate end) {
        return Member.builder()
                .memberId("M001")
                .firstName("Test")
                .lastName("Member")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .planCode("PPO_GOLD")
                .eligibilityStart(start)
                .eligibilityEnd(end)
                .build();
    }

    @Test
    void happyPath_serviceDateBetweenStartAndEnd_returnsValid() {
        Member m = member(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));
        Claim c = claimOn(LocalDate.of(2025, 6, 15));

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void nullMember_returnsCarc31() {
        Claim c = claimOn(LocalDate.of(2025, 6, 15));

        ValidationResult result = validator.validate(c, null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-31");
    }

    @Test
    void serviceDateBeforeEligibilityStart_returnsCarc27() {
        Member m = member(LocalDate.of(2024, 1, 1), null);
        Claim c = claimOn(LocalDate.of(2023, 12, 31));

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-27");
        assertThat(result.messages()).containsExactly("Expenses incurred prior to coverage");
    }

    @Test
    void serviceDateAfterEligibilityEnd_returnsCarc27() {
        Member m = member(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 12, 31));
        Claim c = claimOn(LocalDate.of(2024, 1, 1));

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-27");
        assertThat(result.messages()).containsExactly("Expenses incurred after coverage terminated");
    }

    @Test
    void boundary_serviceDateEqualsEligibilityStart_isValid() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        Member m = member(start, null);
        Claim c = claimOn(start);

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void boundary_serviceDateEqualsEligibilityEnd_isValid() {
        LocalDate end = LocalDate.of(2023, 12, 31);
        Member m = member(LocalDate.of(2022, 1, 1), end);
        Claim c = claimOn(end);

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void nullEligibilityEnd_isStillActive_returnsValid() {
        Member m = member(LocalDate.of(2024, 1, 1), null);
        Claim c = claimOn(LocalDate.of(2099, 1, 1));

        ValidationResult result = validator.validate(c, m, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void orderIs10() {
        assertThat(validator.order()).isEqualTo(10);
    }
}
