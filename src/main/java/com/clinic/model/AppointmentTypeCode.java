package com.clinic.model;

import com.clinic.exception.InvalidInputException;

/** Discriminator stored in JSON so the factory can rebuild the right subclass. */
public enum AppointmentTypeCode {
    CONSULTATION,
    FOLLOWUP,
    EMERGENCY;

    public static AppointmentTypeCode parse(String raw) throws InvalidInputException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("Appointment type is required (CONSULTATION, FOLLOWUP or EMERGENCY).");
        }
        String normalised = raw.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalised) {
            case "CONSULTATION" -> CONSULTATION;
            case "FOLLOWUP" -> FOLLOWUP;
            case "EMERGENCY" -> EMERGENCY;
            default -> throw new InvalidInputException(
                    "Unknown appointment type '" + raw + "'. Use CONSULTATION, FOLLOWUP or EMERGENCY.");
        };
    }
}