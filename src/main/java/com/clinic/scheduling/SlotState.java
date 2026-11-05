package com.clinic.scheduling;

/** State of one 15 minute slot in a doctor's day. */
public enum SlotState {
    FREE,
    BOOKED,
    CHECKED_IN,
    PAST,
    BREAK,
    LEAVE,
    OFF_DUTY
}
