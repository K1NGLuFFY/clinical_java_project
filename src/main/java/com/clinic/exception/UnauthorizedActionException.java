package com.clinic.exception;

/** The logged-in role is not allowed to perform this action. */
public class UnauthorizedActionException extends ClinicException {

    public UnauthorizedActionException(String message) {
        super(message);
    }
}