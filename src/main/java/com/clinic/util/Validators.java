package com.clinic.util;

import com.clinic.exception.InvalidInputException;

/**
 * Small, dependency-free input validators shared by the CLI and the services.
 *
 * <p>Every method throws {@link InvalidInputException} with a message written for the person at
 * the console: it names the field and explains what was expected. The helpers never touch the
 * repositories, so they are safe to use anywhere.</p>
 */
public final class Validators {

    private Validators() {
    }

    /**
     * Requires a non-blank text value and returns it without leading or trailing whitespace.
     *
     * @param field the user-facing field name, for example {@code "patient name"}
     * @param value the raw value to validate, may be {@code null}
     * @return the trimmed, non-blank value
     * @throws InvalidInputException when the value is {@code null} or blank
     */
    public static String requireNonBlank(String field, String value) throws InvalidInputException {
        String label = label(field);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(label + " is required.");
        }
        return value.trim();
    }

    /**
     * Parses a positive database id, rejecting blank, non-numeric and zero/negative input.
     *
     * @param raw   the raw text typed by the user
     * @param field the user-facing field name, for example {@code "patient id"}
     * @return the parsed id, always at least {@code 1}
     * @throws InvalidInputException when the text is not a positive whole number
     */
    public static int parseId(String raw, String field) throws InvalidInputException {
        return parseIntInRange(raw, field, 1, Integer.MAX_VALUE);
    }

    /**
     * Parses a whole number and requires it to be inside the inclusive range
     * {@code [min, max]}.
     *
     * @param raw   the raw text typed by the user
     * @param field the user-facing field name, for example {@code "daily capacity"}
     * @param min   the smallest accepted value (inclusive)
     * @param max   the largest accepted value (inclusive)
     * @return the parsed value
     * @throws InvalidInputException when the text is not a whole number or is out of range
     */
    public static int parseIntInRange(String raw, String field, int min, int max)
            throws InvalidInputException {
        String label = label(field);
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException(label + " is required.");
        }
        String trimmed = raw.trim();
        int value;
        try {
            value = Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            throw new InvalidInputException(
                    label + " must be a whole number, but was '" + trimmed + "'.");
        }
        if (value < min || value > max) {
            if (max == Integer.MAX_VALUE) {
                throw new InvalidInputException(label + " must be at least " + min + ".");
            }
            if (min == Integer.MIN_VALUE) {
                throw new InvalidInputException(label + " must be at most " + max + ".");
            }
            throw new InvalidInputException(
                    label + " must be between " + min + " and " + max + ", but was " + value + ".");
        }
        return value;
    }

    /** Human readable field label: capitalised first letter or a generic fallback. */
    private static String label(String field) {
        if (field == null || field.isBlank()) {
            return "This value";
        }
        String trimmed = field.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
