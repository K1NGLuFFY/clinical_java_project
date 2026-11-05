package com.clinic.scheduling;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Half-open time interval {@code [start, end)} with the overlap rule every schedule check
 * depends on: touching intervals (10:00-10:30 and 10:30-11:00) do not overlap.
 */
public final class TimeRange {

    private final LocalDateTime start;
    private final LocalDateTime end;

    public TimeRange(LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End must be after start: " + start + " -> " + end);
        }
        this.start = start;
        this.end = end;
    }

    public static TimeRange of(LocalDateTime start, Duration duration) {
        Objects.requireNonNull(duration, "duration");
        return new TimeRange(start, start.plus(duration));
    }

    public static TimeRange on(LocalDate date, LocalTime from, LocalTime to) {
        return new TimeRange(date.atTime(from), date.atTime(to));
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /** True when the two half-open intervals share at least one instant. */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** True when {@code other} is completely inside this range (touching edges allowed). */
    public boolean contains(TimeRange other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    public boolean contains(LocalDateTime instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    /** True when the given instant is exactly the start of the range. */
    public boolean startsAt(LocalDateTime instant) {
        return start.equals(instant);
    }

    @Override
    public String toString() {
        return start + "/" + end;
    }
}
