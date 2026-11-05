package com.clinic.exception;

/** A follow-up appointment is not allowed for this patient and doctor. */
public class FollowUpNotEligibleException extends ClinicException {

    public FollowUpNotEligibleException(String message) {
        super(message);
    }
}