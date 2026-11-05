package com.clinic.model;

import com.clinic.exception.InvalidInputException;
import java.time.LocalTime;

/** Optional preferred time window when joining the waiting list. */
public enum TimeWindow {
    ANY,
    MORNING,
    AFTERNOON;

    public static TimeWindow parse(String raw) throws InvalidInputException {
        if (raw == null || raw.isBlank()) {
            return ANY;
        }
        try {
            return TimeWindow.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException("Unknown time window '" + raw + "'. Use MORNING, AFTERNOON or ANY.");
        }
    }

    /** MORNING is before 12:00, AFTERNOON is 12:00 onwards. */
    public boolean matches(LocalTime time) {
        return switch (this) {
            case ANY -> true;
            case MORNING -> time.isBefore(LocalTime.NOON);
            case AFTERNOON -> !time.isBefore(LocalTime.NOON);
        };
    }
}