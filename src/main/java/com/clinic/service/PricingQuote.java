package com.clinic.service;

import com.clinic.model.PatientCategory;
import java.math.BigDecimal;

/**
 * Money breakdown for one appointment. {@code baseFee = patientShare + insurerShare}.
 *
 * @param baseFee      list price of the appointment type
 * @param patientShare amount the patient pays
 * @param insurerShare amount the insurer covers (zero for standard and senior)
 * @param category     pricing category that produced the split
 */
public record PricingQuote(BigDecimal baseFee, BigDecimal patientShare, BigDecimal insurerShare,
                           PatientCategory category) {
}