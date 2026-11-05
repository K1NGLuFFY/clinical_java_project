package com.clinic.pricing;

import com.clinic.model.PatientCategory;
import java.math.BigDecimal;

/**
 * Strategy for splitting an appointment fee between patient and insurer.
 * Implementations must not change: one class per category.
 */
public interface PricingPolicy {

    PatientCategory category();

    /** Amount the patient pays for the given base fee. */
    BigDecimal patientShare(BigDecimal baseFee);

    /** Amount the insurer covers for the given base fee. */
    BigDecimal insurerShare(BigDecimal baseFee);
}
