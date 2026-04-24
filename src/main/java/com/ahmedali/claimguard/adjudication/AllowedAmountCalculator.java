package com.ahmedali.claimguard.adjudication;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Provider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class AllowedAmountCalculator {

    private static final Map<String, BigDecimal> FEE_SCHEDULE = Map.ofEntries(
            Map.entry("99213", new BigDecimal("125.00")),
            Map.entry("99214", new BigDecimal("185.00")),
            Map.entry("99215", new BigDecimal("245.00")),
            Map.entry("99203", new BigDecimal("175.00")),
            Map.entry("99204", new BigDecimal("260.00")),
            Map.entry("80050", new BigDecimal("95.00")),
            Map.entry("36415", new BigDecimal("15.00")),
            Map.entry("93000", new BigDecimal("45.00")),
            Map.entry("71046", new BigDecimal("85.00")),
            Map.entry("85025", new BigDecimal("35.00")),
            Map.entry("70553", new BigDecimal("1800.00")),
            Map.entry("27447", new BigDecimal("32000.00")),
            Map.entry("43644", new BigDecimal("25000.00")),
            Map.entry("29827", new BigDecimal("8500.00")),
            Map.entry("22612", new BigDecimal("45000.00"))
    );

    private static final BigDecimal UNKNOWN_CPT_MULTIPLIER = new BigDecimal("0.80");
    private static final BigDecimal OUT_OF_NETWORK_MULTIPLIER = new BigDecimal("0.60");

    public BigDecimal calculate(Claim claim, Provider provider) {
        BigDecimal billed = claim.getBilledAmount();
        BigDecimal base = FEE_SCHEDULE.get(claim.getProcedureCode());
        if (base == null) {
            base = billed.multiply(UNKNOWN_CPT_MULTIPLIER);
        }

        BigDecimal allowed = base;
        if (provider != null && Boolean.FALSE.equals(provider.getIsInNetwork())) {
            allowed = allowed.multiply(OUT_OF_NETWORK_MULTIPLIER);
        }

        allowed = allowed.setScale(2, RoundingMode.HALF_UP);

        if (allowed.compareTo(billed) > 0) {
            allowed = billed.setScale(2, RoundingMode.HALF_UP);
        }
        return allowed;
    }
}
