package com.clinic.exception;

/** The patient already holds the maximum number of active upcoming appointments. */
public class BookingLimitExceededException extends ClinicException {

    public BookingLimitExceededException(String message) {
        super(message);
    }
}