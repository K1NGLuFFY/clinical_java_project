package com.clinic.model;

/** Lifecycle of a waiting-list entry. */
public enum WaitlistStatus {
    WAITING,
    OFFERED,
    ACCEPTED,
    EXPIRED,
    DECLINED,
    CANCELLED;

    public boolean isOpen() {
        return this == WAITING || this == OFFERED;
    }
}