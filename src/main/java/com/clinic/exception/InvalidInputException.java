package com.clinic.exception;

/** Input could not be parsed or failed a basic validation rule. */
public class InvalidInputException extends ClinicException {

    public InvalidInputException(String message) {
        super(message);
    }
}