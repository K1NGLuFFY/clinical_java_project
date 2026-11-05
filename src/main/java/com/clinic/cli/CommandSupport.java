package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.Role;
import com.clinic.model.TimeWindow;
import com.clinic.model.User;
import com.clinic.service.BillingService;
import com.clinic.util.Formats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared plumbing for the concrete commands: role sets, argument parsing with friendly errors,
 * display helpers and the one place that renders {@link SlotUnavailableException}s (with the
 * suggested alternative slots).
 */
abstract class CommandSupport implements Command {

    protected static final Set<Role> NO_ROLES = Collections.emptySet();
    protected static final Set<Role> ALL_ROLES =
            Collections.unmodifiableSet(EnumSet.allOf(Role.class));
    protected static final Set<Role> PATIENT_ONLY = Set.of(Role.PATIENT);
    protected static final Set<Role> DOCTOR_ONLY = Set.of(Role.DOCTOR);
    protected static final Set<Role> RECEPTIONIST_ONLY = Set.of(Role.RECEPTIONIST);
    protected static final Set<Role> PATIENT_OR_RECEPTIONIST = Set.of(Role.PATIENT, Role.RECEPTIONIST);
    protected static final Set<Role> PATIENT_OR_DOCTOR = Set.of(Role.PATIENT, Role.DOCTOR);

    private final String name;
    private final List<String> aliases;
    private final Set<Role> allowedRoles;
    private final String usage;
    private final String summary;

    protected CommandSupport(String name, Set<Role> allowedRoles, String usage, String summary,
                             String... aliases) {
        this.name = name;
        this.allowedRoles = allowedRoles;
        this.usage = usage;
        this.summary = summary;
        this.aliases = List.of(aliases);
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final List<String> aliases() {
        return aliases;
    }

    @Override
    public final Set<Role> allowedRoles() {
        return allowedRoles;
    }

    @Override
    public final String usage() {
        return usage;
    }

    @Override
    public final String summary() {
        return summary;
    }

    /** Prints "slot unavailable" plus the suggested alternatives; every other error propagates. */
    @Override
    public final void execute(CommandContext ctx, ParsedCommand command) throws ClinicException {
        try {
            perform(ctx, command);
        } catch (SlotUnavailableException ex) {
            System.out.println("Slot unavailable: " + ex.getMessage());
            if (ex.hasSuggestions()) {
                List<String> suggestions = new ArrayList<>();
                for (LocalDateTime slot : ex.getSuggestions()) {
                    suggestions.add(iso(slot));
                }
                System.out.println("Next free slots: " + String.join(", ", suggestions));
            }
        }
    }

    /** The actual command body; exceptions reach the console loop which prints them. */
    protected abstract void perform(CommandContext ctx, ParsedCommand command) throws ClinicException;

    // ------------------------------------------------------------------ parsing

    /** Required positional argument with a friendly, usage-aware message when missing. */
    protected String requirePositional(ParsedCommand command, int index, String label)
            throws InvalidInputException {
        String value = command.positional(index);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(missingArgument(label));
        }
        return value;
    }

    /** Required whole-number positional argument. */
    protected int requireInt(ParsedCommand command, int index, String label) throws InvalidInputException {
        return parseInt(requirePositional(command, index, label), label);
    }

    /** Required whole-number option ({@code --name=<number>}). */
    protected int requireIntFlag(ParsedCommand command, String flag, String label)
            throws InvalidInputException {
        String raw = command.flag(flag);
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException(
                    "Missing required option --" + flag + "=<" + label + ">. Usage: " + usage());
        }
        return parseInt(raw, label);
    }

    /** Required ISO date ({@code yyyy-MM-dd}) positional argument. */
    protected LocalDate requireDate(ParsedCommand command, int index, String label)
            throws InvalidInputException {
        return Formats.parseDate(requirePositional(command, index, label));
    }

    /** Required ISO date-time ({@code yyyy-MM-ddTHH:mm}) positional argument. */
    protected LocalDateTime requireDateTime(ParsedCommand command, int index, String label)
            throws InvalidInputException {
        return Formats.parseDateTime(requirePositional(command, index, label));
    }

    protected static int parseInt(String raw, String label) throws InvalidInputException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new InvalidInputException(
                    "Invalid " + label + " '" + raw + "': expected a whole number.");
        }
    }

    protected static DayOfWeek parseDay(String raw) throws InvalidInputException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("A day name is required, for example MONDAY.");
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(
                    "Unknown day '" + raw + "'. Use MONDAY, TUESDAY, ..., SUNDAY.");
        }
    }

    protected static PatientCategory parseCategory(String raw) throws InvalidInputException {
        String normalised = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "STANDARD" -> PatientCategory.STANDARD;
            case "INSURED" -> PatientCategory.INSURED;
            case "SENIOR" -> throw new InvalidInputException(
                    "Patients are registered as STANDARD or INSURED; SENIOR is derived from the date of birth.");
            default -> throw new InvalidInputException(
                    "Unknown category '" + raw + "'. Use STANDARD or INSURED.");
        };
    }

    protected static TimeWindow parseWindow(String raw) throws InvalidInputException {
        return TimeWindow.parse(raw);
    }

    /** Fails with a friendly message when a report date range is inverted. */
    protected static void requireOrdered(LocalDate from, LocalDate to) throws InvalidInputException {
        if (from.isAfter(to)) {
            throw new InvalidInputException("The start date must not be after the end date.");
        }
    }

    /** Splits a semicolon separated list such as the {@code --rx} option. */
    protected static List<String> splitItems(String raw) {
        List<String> items = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return items;
        }
        for (String part : raw.split(";")) {
            if (!part.isBlank()) {
                items.add(part.trim());
            }
        }
        return items;
    }

    // ------------------------------------------------------------------ users

    /** The logged-in user; the registry gate already guarantees one for role-protected commands. */
    protected User user(CommandContext ctx) throws UnauthorizedActionException {
        ctx.requireLogin();
        return ctx.getCurrentUser();
    }

    /** Best-effort patient name for console output; falls back to the numeric id. */
    protected String patientName(CommandContext ctx, int patientId) {
        return ctx.reports().findPatient(patientId)
                .map(Patient::getName)
                .orElse("patient #" + patientId);
    }

    /** Best-effort doctor display name for console output; falls back to the numeric id. */
    protected String doctorDisplay(CommandContext ctx, int doctorId) {
        try {
            return ctx.scheduling().requireDoctor(doctorId).getDisplayName();
        } catch (ClinicException ex) {
            return "doctor #" + doctorId;
        }
    }

    protected Doctor doctor(CommandContext ctx, int doctorId) throws ClinicException {
        return ctx.scheduling().requireDoctor(doctorId);
    }

    // ------------------------------------------------------------------ formatting

    /** ISO-8601 date-time without a zero seconds part, e.g. {@code 2026-11-02T10:00}. */
    private static final DateTimeFormatter ISO_MINUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH);

    /** ISO-8601 date-time as used in slot suggestions, e.g. {@code 2026-11-02T10:00}. */
    protected static String iso(LocalDateTime value) {
        return value == null ? "" : ISO_MINUTE.format(value);
    }

    /** Full English day name, e.g. {@code Monday}. */
    protected static String dayName(DayOfWeek day) {
        return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** Percentage with one decimal, e.g. {@code 53.3%}. */
    protected static String percent(BigDecimal value) {
        if (value == null) {
            return "0.0%";
        }
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Late cancellation percentage as shown in the cancel message, e.g. {@code 20}. */
    protected static String lateFeePercent() {
        return Formats.money(BillingService.LATE_CANCELLATION_RATE.multiply(BigDecimal.valueOf(100)));
    }

    protected static AppointmentType typeOf(AppointmentTypeCode code) {
        return AppointmentTypeFactory.forCode(code);
    }

    /** Display name of an appointment type, e.g. {@code Consultation}. */
    protected static String typeName(AppointmentTypeCode code) {
        return code == null ? "" : typeOf(code).getDisplayName();
    }

    /** {@code 09:00-09:30} for one appointment. */
    protected static String appointmentWindow(Appointment appointment) {
        if (appointment == null || appointment.getStart() == null || appointment.getEnd() == null) {
            return "";
        }
        return Formats.time(appointment.getStart().toLocalTime())
                + "-" + Formats.time(appointment.getEnd().toLocalTime());
    }

    protected static String appointmentStatus(Appointment appointment) {
        return appointment == null || appointment.getStatus() == null
                ? "" : appointment.getStatus().name();
    }

    /** Builds the friendly "missing argument + usage" message. */
    private String missingArgument(String label) {
        return "Missing required argument <" + label + ">. Usage: " + usage();
    }
}
