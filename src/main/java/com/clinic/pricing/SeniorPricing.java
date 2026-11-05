package com.clinic.pricing;

import com.clinic.model.PatientCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Senior pricing: the patient gets a 20% discount and no insurer is involved.
 *
 * <p>The discounted share is rounded HALF_UP to two decimals.</p>
 */
public final class SeniorPricing implements PricingPolicy {

    /** Share of the base fee paid by a senior patient. */
    public static final BigDecimal PATIENT_RATE = new BigDecimal("0.80");

    @Override
    public PatientCategory category() {
        return PatientCategory.SENIOR;
    }

    @Override
    public BigDecimal patientShare(BigDecimal baseFee) {
        Objects.requireNonNull(baseFee, "base fee");
        return baseFee.setScale(2, RoundingMode.HALF_UP)
                .multiply(PATIENT_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal insurerShare(BigDecimal baseFee) {
        Objects.requireNonNull(baseFee, "base fee");
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
