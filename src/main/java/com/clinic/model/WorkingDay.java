package com.clinic.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One weekday of a doctor's weekly schedule: start, end and an optional break.
 * Example: Monday 09:00-17:00 with a break 13:00-14:00.
 */
public class WorkingDay {

    private DayOfWeek day;
    private LocalTime start;
    private LocalTime end;
    private LocalTime breakStart;
    private LocalTime breakEnd;

    public WorkingDay() {
        // for the JSON mapper
    }

    public WorkingDay(DayOfWeek day, LocalTime start, LocalTime end) {
        this(day, start, end, null, null);
    }

    public WorkingDay(DayOfWeek day, LocalTime start, LocalTime end,
                      LocalTime breakStart, LocalTime breakEnd) {
        this.day = day;
        this.start = start;
        this.end = end;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
    }

    public boolean hasBreak() {
        return breakStart != null && breakEnd != null;
    }

    public boolean isWithinWorkingHours(LocalTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    public boolean isDuringBreak(LocalTime time) {
        return hasBreak() && !time.isBefore(breakStart) && time.isBefore(breakEnd);
    }

    public LocalDateTime startOn(LocalDate date) {
        return date.atTime(start);
    }

    public LocalDateTime endOn(LocalDate date) {
        return date.atTime(end);
    }

    public LocalDateTime breakStartOn(LocalDate date) {
        return hasBreak() ? date.atTime(breakStart) : null;
    }

    public LocalDateTime breakEndOn(LocalDate date) {
        return hasBreak() ? date.atTime(breakEnd) : null;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public void setDay(DayOfWeek day) {
        this.day = day;
    }

    public LocalTime getStart() {
        return start;
    }

    public void setStart(LocalTime start) {
        this.start = start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public void setEnd(LocalTime end) {
        this.end = end;
    }

    public LocalTime getBreakStart() {
        return breakStart;
    }

    public void setBreakStart(LocalTime breakStart) {
        this.breakStart = breakStart;
    }

    public LocalTime getBreakEnd() {
        return breakEnd;
    }

    public void setBreakEnd(LocalTime breakEnd) {
        this.breakEnd = breakEnd;
    }

    @Override
    public String toString() {
        return day + " " + start + "-" + end + (hasBreak() ? " (break " + breakStart + "-" + breakEnd + ")" : "");
    }
}
