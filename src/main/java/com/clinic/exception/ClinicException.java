package com.clinic.exception;

/**
 * Base type for every expected, user-facing clinic error. The CLI catches this type in one
 * place and prints a friendly message, so invalid input can never crash the program.
 */
public class ClinicException extends Exception {

    public ClinicException(String message) {
        super(message);
    }

    public ClinicException(String message, Throwable cause) {
        super(message, cause);
    }
}