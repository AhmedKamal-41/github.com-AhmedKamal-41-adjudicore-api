package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFormatValidatorTest {

    private final CodeFormatValidator validator = new CodeFormatValidator();

    private static Claim claim(String cpt, String icd) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 20))
                .procedureCode(cpt)
                .diagnosisCode(icd)
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }

    @Test
    void validCptAndValidIcd10_returnsValid() {
        ValidationResult result = validator.validate(claim("99213", "J45.909"), null, null);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void fourDigitCpt_returnsCarc181() {
        ValidationResult result = validator.validate(claim("9921", "J45.909"), null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-181");
    }

    @Test
    void sixDigitCpt_returnsCarc181() {
        ValidationResult result = validator.validate(claim("992130", "J45.909"), null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-181");
    }

    @Test
    void lowercaseIcd10_returnsCarc181() {
        ValidationResult result = validator.validate(claim("99213", "j45"), null, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-181");
    }

    @Test
    void icd10WithoutDecimal_isValid() {
        ValidationResult result = validator.validate(claim("99213", "J45"), null, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void icd10With4DigitDecimal_isValid_boundary() {
        ValidationResult result = validator.validate(claim("99213", "J45.1234"), null, null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void orderIs50() {
        assertThat(validator.order()).isEqualTo(50);
    }
}
