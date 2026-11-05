package com.clinic.util;

import com.clinic.exception.InvalidInputException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Central date, time and money formatting/parsing. One place keeps every command and report
 * consistent and makes the ISO-8601 storage format explicit.
 */
public final class Formats {

    /** Canonical storage format for date-times: {@code 2026-11-02T10:00:00}. */
    public static final DateTimeFormatter ISO_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter CLOCK_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    public static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    private Formats() {
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Parses an ISO date-time. Both {@code 2026-11-02T10:00} and {@code 2026-11-02 10:00}
     * are accepted, with or without seconds.
     */
    public static LocalDateTime parseDateTime(String raw) throws InvalidInputException {
        String value = raw == null ? "" : raw.trim().replace(' ', 'T');
        if (value.isEmpty()) {
            throw new InvalidInputException("A date and time is required, for example 2026-11-02T10:00.");
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new InvalidInputException(
                    "Invalid date/time '" + raw + "'. Use yyyy-MM-ddTHH:mm (for example 2026-11-02T10:00).");
        }
    }

    public static LocalDate parseDate(String raw) throws InvalidInputException {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new InvalidInputException("A date is required, for example 2026-11-02.");
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new InvalidInputException("Invalid date '" + raw + "'. Use yyyy-MM-dd (for example 2026-11-02).");
        }
    }

    public static LocalTime parseTime(String raw) throws InvalidInputException {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new InvalidInputException("A time is required in 24h format, for example 09:00.");
        }
        try {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH));
        } catch (DateTimeParseException ex) {
            throw new InvalidInputException("Invalid time '" + raw + "'. Use HH:mm in 24h format, for example 09:00.");
        }
    }

    // ------------------------------------------------------------- formatting

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : ISO_DATE_TIME.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? "" : ISO_DATE.format(value);
    }

    public static String time(LocalTime value) {
        return value == null ? "" : CLOCK_TIME.format(value);
    }

    public static String displayDate(LocalDate value) {
        if (value == null) {
            return "";
        }
        DayOfWeek day = value.getDayOfWeek();
        String dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return dayName + " " + ISO_DATE.format(value);
    }

    public static String displayDateTime(LocalDateTime value) {
        return value == null ? "" : DISPLAY_DATE_TIME.format(value);
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        format.setMinimumFractionDigits(0);
        return format.format(amount);
    }

    /** Money with the currency label used by the brief (values are in the clinic's local currency). */
    public static String moneyWithUnit(BigDecimal amount) {
        return money(amount);
    }
}
