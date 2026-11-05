package com.clinic.model;

/**
 * Registered pricing category for a patient.
 * SENIOR is derived automatically from the date of birth (65 years or older),
 * so a patient is registered as STANDARD or INSURED only.
 */
public enum PatientCategory {
    STANDARD,
    INSURED,
    SENIOR
}