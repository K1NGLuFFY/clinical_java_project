package com.clinic.model;

import java.time.LocalDate;

/** Inclusive leave period. A single day leave has {@code from == to}. */
public class LeavePeriod {

    private LocalDate from;
    private LocalDate to;

    public LeavePeriod() {
        // for the JSON mapper
    }

    public LeavePeriod(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    public boolean covers(LocalDate date) {
        if (from == null) {
            return false;
        }
        LocalDate last = to == null ? from : to;
        return !date.isBefore(from) && !date.isAfter(last);
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    @Override
    public String toString() {
        return (to == null || to.equals(from)) ? from.toString() : from + " to " + to;
    }
}
