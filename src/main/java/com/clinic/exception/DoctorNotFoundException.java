package com.clinic.exception;

/** No doctor exists for the given id. */
public class DoctorNotFoundException extends ClinicException {

    public DoctorNotFoundException(String message) {
        super(message);
    }
}