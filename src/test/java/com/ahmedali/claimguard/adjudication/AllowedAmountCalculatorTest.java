package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedAmountCalculatorTest {

    private final AllowedAmountCalculator calculator = new AllowedAmountCalculator();

    private static Claim claim(String cpt, BigDecimal billed) {
        return Claim.builder()
                .claimId("CLM-X")
                .memberId("M001").providerNpi("1234567890")
                .serviceDate(LocalDate.of(2025, 6, 15))
                .submissionDate(LocalDate.of(2025, 6, 16))
                .procedureCode(cpt).diagnosisCode("J45.909")
                .billedAmount(billed)
                .status(ClaimStatus.VALIDATED)
                .build();
    }

    private static Provider provider(boolean inNetwork) {
        return Provider.builder()
                .npi("1234567890").name("P").specialty("X").isInNetwork(inNetwork).build();
    }

    @Test
    void knownCpt99213InNetwork_returns125() {
        BigDecimal result = calculator.calculate(
                claim("99213", new BigDecimal("150.00")), provider(true));
        assertThat(result).isEqualByComparingTo("125.00");
    }

    @Test
    void knownCpt99214InNetwork_returns185() {
        BigDecimal result = calculator.calculate(
                claim("99214", new BigDecimal("200.00")), provider(true));
        assertThat(result).isEqualByComparingTo("185.00");
    }

    @Test
    void unknownCpt_returns80PercentOfBilled() {
        BigDecimal result = calculator.calculate(
                claim("88888", new BigDecimal("200.00")), provider(true));
        assertThat(result).isEqualByComparingTo("160.00");
    }

    @Test
    void knownCpt99213OutOfNetwork_returns75_sixtyPercent() {
        BigDecimal result = calculator.calculate(
                claim("99213", new BigDecimal("150.00")), provider(false));
        assertThat(result).isEqualByComparingTo("75.00");
    }

    @Test
    void knownCpt99214OutOfNetwork_returns111_exactPrecision() {
        // $185.00 * 0.60 = $111.00 exactly
        BigDecimal result = calculator.calculate(
                claim("99214", new BigDecimal("200.00")), provider(false));
        assertThat(result).isEqualByComparingTo("111.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void allowedCappedAtBilled_whenFeeScheduleExceedsBilled() {
        // 99213 fee schedule $125, billed $100 → allowed capped at $100
        BigDecimal result = calculator.calculate(
                claim("99213", new BigDecimal("100.00")), provider(true));
        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void nullProvider_treatedAsInNetwork() {
        BigDecimal result = calculator.calculate(
                claim("99213", new BigDecimal("150.00")), null);
        assertThat(result).isEqualByComparingTo("125.00");
    }

    @Test
    void unknownCptOutOfNetwork_appliesBothAdjustments() {
        // billed 100 → unknown 80% → 80.00 → OON 60% → 48.00
        BigDecimal result = calculator.calculate(
                claim("88888", new BigDecimal("100.00")), provider(false));
        assertThat(result).isEqualByComparingTo("48.00");
    }

    @Test
    void priorAuthCptInFeeSchedule_70553_returns1800() {
        BigDecimal result = calculator.calculate(
                claim("70553", new BigDecimal("2500.00")), provider(true));
        assertThat(result).isEqualByComparingTo("1800.00");
    }
}
