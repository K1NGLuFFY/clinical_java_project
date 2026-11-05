package com.clinic.pricing;

import com.clinic.model.PatientCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Insured pricing: the insurer covers 70% of the list price and the patient pays the rest.
 *
 * <p>The insurer share is rounded HALF_UP to two decimals and the patient share is derived by
 * subtraction, so the two shares always add up to exactly the base fee.</p>
 */
public final class InsuredPricing implements PricingPolicy {

    /** Share of the base fee covered by the insurer. */
    public static final BigDecimal INSURER_RATE = new BigDecimal("0.70");

    @Override
    public PatientCategory category() {
        return PatientCategory.INSURED;
    }

    @Override
    public BigDecimal patientShare(BigDecimal baseFee) {
        BigDecimal base = normalise(baseFee);
        return base.subtract(insurerShare(base));
    }

    @Override
    public BigDecimal insurerShare(BigDecimal baseFee) {
        BigDecimal base = normalise(baseFee);
        return base.multiply(INSURER_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalise(BigDecimal baseFee) {
        Objects.requireNonNull(baseFee, "base fee");
        return baseFee.setScale(2, RoundingMode.HALF_UP);
    }
}
