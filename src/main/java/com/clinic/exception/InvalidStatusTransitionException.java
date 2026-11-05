package com.clinic.exception;

/** The appointment lifecycle does not allow this status change. */
public class InvalidStatusTransitionException extends ClinicException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}