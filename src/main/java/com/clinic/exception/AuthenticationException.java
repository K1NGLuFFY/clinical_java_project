package com.clinic.exception;

/** The user id or password is incorrect. */
public class AuthenticationException extends ClinicException {

    public AuthenticationException(String message) {
        super(message);
    }
}