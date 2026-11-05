package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.factory.PricingPolicyFactory;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.pricing.PricingPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Pricing. The category to policy mapping is a strategy lookup in the factory: this service
 * contains no if/else chain on the patient category.
 */
public class BillingService {

    /** Late cancellations are charged this fraction of the base fee. */
    public static final BigDecimal LATE_CANCELLATION_RATE = new BigDecimal("0.20");

    /** Money is always reported with two decimals. */
    private static final int MONEY_SCALE = 2;

    private final PricingPolicyFactory policies;

    public BillingService(PricingPolicyFactory policies) {
        this.policies = policies;
    }

    /**
     * Splits the base fee between patient and insurer using the patient's pricing policy.
     *
     * <p>The category is derived from the patient's age on {@code onDate}, so a patient who
     * turns 65 is billed as a senior from that date without any stored category change.</p>
     *
     * @param patient patient the appointment is for
     * @param type    appointment type to price
     * @param onDate  date the price applies to (used for the age-based category)
     * @return the base fee and its patient/insurer split, rounded to two decimals
     * @throws ClinicException when the type is unknown or no policy exists for the category
     */
    public PricingQuote quote(Patient patient, AppointmentTypeCode type, LocalDate onDate) throws ClinicException {
        if (patient == null) {
            throw new InvalidInputException("A patient is required for a price quote.");
        }
        if (type == null) {
            throw new InvalidInputException("An appointment type is required for a price quote.");
        }
        if (onDate == null) {
            throw new InvalidInputException("A date is required for a price quote.");
        }
        AppointmentType appointmentType = AppointmentTypeFactory.forCode(type);
        if (appointmentType == null) {
            throw new InvalidInputException("Unknown appointment type '" + type + "'.");
        }
        PatientCategory category = patient.pricingCategoryOn(onDate);
        PricingPolicy policy = policies.forCategory(category);
        if (policy == null) {
            throw new InvalidInputException("No pricing policy for patient category " + category + ".");
        }
        BigDecimal baseFee = money(appointmentType.getBaseFee());
        BigDecimal patientShare = money(policy.patientShare(baseFee));
        BigDecimal insurerShare = money(policy.insurerShare(baseFee));
        return new PricingQuote(baseFee, patientShare, insurerShare, category);
    }

    /**
     * 20% of the base fee of the appointment type, rounded to two decimals.
     *
     * @param type cancelled appointment type
     * @return late cancellation fee
     */
    public BigDecimal lateCancellationFee(AppointmentTypeCode type) {
        if (type == null) {
            throw new IllegalArgumentException("Appointment type is required.");
        }
        AppointmentType appointmentType = AppointmentTypeFactory.forCode(type);
        if (appointmentType == null) {
            throw new IllegalArgumentException("Unknown appointment type '" + type + "'.");
        }
        BigDecimal baseFee = appointmentType.getBaseFee() == null ? BigDecimal.ZERO : appointmentType.getBaseFee();
        return baseFee.multiply(LATE_CANCELLATION_RATE).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
