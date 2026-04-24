package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderNetworkValidatorTest {

    private final ProviderNetworkValidator validator = new ProviderNetworkValidator();

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

    private static Provider provider(boolean inNetwork) {
        return Provider.builder()
                .npi("1234567890")
                .name("Test Provider")
                .specialty("FAMILY_MEDICINE")
                .isInNetwork(inNetwork)
                .build();
    }

    private static Claim baseClaim() {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 20))
                .procedureCode("99213")
                .diagnosisCode("J45.909")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    @Test
    void inNetwork_ppoGold_returnsValid() {
        ValidationResult result = validator.validate(baseClaim(), member("PPO_GOLD"), provider(true));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void inNetwork_hmoSilver_returnsValid() {
        ValidationResult result = validator.validate(baseClaim(), member("HMO_SILVER"), provider(true));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void outOfNetwork_ppoGold_returnsValid() {
        ValidationResult result = validator.validate(baseClaim(), member("PPO_GOLD"), provider(false));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void outOfNetwork_hmoSilver_returnsCarc242() {
        ValidationResult result = validator.validate(baseClaim(), member("HMO_SILVER"), provider(false));

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-242");
    }

    @Test
    void outOfNetwork_epoBronze_returnsCarc242() {
        ValidationResult result = validator.validate(baseClaim(), member("EPO_BRONZE"), provider(false));

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-242");
    }

    @Test
    void nullProvider_returnsCarcB7() {
        ValidationResult result = validator.validate(baseClaim(), member("PPO_GOLD"), null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-B7");
    }

    @Test
    void orderIs20() {
        assertThat(validator.order()).isEqualTo(20);
    }

    // --- Mutation-driven coverage of null branches ---

    @Test
    void outOfNetwork_nullMember_returnsValid_doesNotNpeOnPlanCode() {
        // Guards the `member != null` short-circuit in the OON && chain:
        // without it, enforcesStrictNetwork(null.getPlanCode()) would NPE.
        ValidationResult result = validator.validate(baseClaim(), null, provider(false));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void outOfNetwork_memberWithNullPlanCode_returnsValid() {
        // Guards the `planCode == null` early-return in enforcesStrictNetwork:
        // without it, null.startsWith("HMO") would NPE.
        ValidationResult result = validator.validate(
                baseClaim(), member(null), provider(false));
        assertThat(result.isValid()).isTrue();
    }
}
