package com.clinic.exception;

/** No appointment exists for the given id. */
public class AppointmentNotFoundException extends ClinicException {

    public AppointmentNotFoundException(String message) {
        super(message);
    }
}