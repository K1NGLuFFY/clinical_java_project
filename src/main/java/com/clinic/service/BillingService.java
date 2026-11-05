package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Patient;
import com.clinic.pricing.PricingPolicyFactory;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pricing. The category to policy mapping is a strategy lookup in the factory: this service
 * contains no if/else chain on the patient category.
 */
public class BillingService {

    /** Late cancellations are charged this fraction of the base fee. */
    public static final BigDecimal LATE_CANCELLATION_RATE = new BigDecimal("0.20");

    private final PricingPolicyFactory policies;

    public BillingService(PricingPolicyFactory policies) {
        this.policies = policies;
    }

    /** Splits the base fee between patient and insurer using the patient's pricing policy. */
    public PricingQuote quote(Patient patient, AppointmentTypeCode type, LocalDate onDate) throws ClinicException {
        throw new UnsupportedOperationException("BillingService.quote not implemented yet");
    }

    /** 20% of the base fee of the appointment type, rounded to two decimals. */
    public BigDecimal lateCancellationFee(AppointmentTypeCode type) {
        throw new UnsupportedOperationException("BillingService.lateCancellationFee not implemented yet");
    }
}
