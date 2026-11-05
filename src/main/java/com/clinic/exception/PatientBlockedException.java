package com.clinic.exception;

/** The patient is blocked from booking because of repeated no-shows. */
public class PatientBlockedException extends ClinicException {

    public PatientBlockedException(String message) {
        super(message);
    }
}