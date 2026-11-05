package com.clinic.exception;

/** The doctor is on leave for the requested date. */
public class DoctorOnLeaveException extends ClinicException {

    public DoctorOnLeaveException(String message) {
        super(message);
    }
}