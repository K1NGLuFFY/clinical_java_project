package com.clinic.exception;

/** The slot is outside the doctor's working hours or overlaps the break. */
public class OutsideWorkingHoursException extends ClinicException {

    public OutsideWorkingHoursException(String message) {
        super(message);
    }
}