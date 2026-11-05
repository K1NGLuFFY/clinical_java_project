package com.clinic.exception;

/** The appointment can no longer be cancelled (already started or already final). */
public class CancellationNotAllowedException extends ClinicException {

    public CancellationNotAllowedException(String message) {
        super(message);
    }
}
