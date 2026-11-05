package com.clinic.exception;

/** No waiting-list entry exists for the given id. */
public class WaitlistEntryNotFoundException extends ClinicException {

    public WaitlistEntryNotFoundException(String message) {
        super(message);
    }
}