package com.clinic.pricing;

import com.clinic.model.PatientCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Standard pricing: the patient pays the full list price and no insurer is involved.
 *
 * <p>All amounts are returned at scale 2 with {@link RoundingMode#HALF_UP}, so money never
 * carries more than two decimals and is never represented as a {@code double}.</p>
 */
public final class StandardPricing implements PricingPolicy {

    @Override
    public PatientCategory category() {
        return PatientCategory.STANDARD;
    }

    @Override
    public BigDecimal patientShare(BigDecimal baseFee) {
        return normalise(baseFee);
    }

    @Override
    public BigDecimal insurerShare(BigDecimal baseFee) {
        normalise(baseFee);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalise(BigDecimal baseFee) {
        Objects.requireNonNull(baseFee, "base fee");
        return baseFee.setScale(2, RoundingMode.HALF_UP);
    }
}
