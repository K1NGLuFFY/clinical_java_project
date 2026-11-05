package com.clinic.exception;

/** A data file is missing, unreadable, unwritable or corrupt. */
public class StorageException extends ClinicException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}