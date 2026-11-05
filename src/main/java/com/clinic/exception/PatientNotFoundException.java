package com.clinic.exception;

/** No patient exists for the given id. */
public class PatientNotFoundException extends ClinicException {

    public PatientNotFoundException(String message) {
        super(message);
    }
}