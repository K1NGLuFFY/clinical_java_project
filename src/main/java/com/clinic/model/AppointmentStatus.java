package com.clinic.model;

/**
 * Appointment lifecycle states.
 *
 * <pre>
 * BOOKED -> CHECKED_IN -> COMPLETED
 * BOOKED -> CANCELLED
 * BOOKED -> NO_SHOW
 * </pre>
 *
 * COMPLETED, CANCELLED and NO_SHOW are final states.
 */
public enum AppointmentStatus {
    BOOKED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** Final states can never change again. */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    /** Active states still occupy a slot in the diary. */
    public boolean isActive() {
        return this == BOOKED || this == CHECKED_IN;
    }
}