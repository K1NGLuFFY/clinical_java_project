package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.model.Doctor;
import com.clinic.model.User;
import com.clinic.model.WorkingDay;
import com.clinic.service.CreatedAccount;
import com.clinic.util.Formats;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Receptionist doctor-administration commands: {@code doctor add}, {@code doctor schedule},
 * {@code doctor leave} and the read-only {@code doctor list}.
 */
final class DoctorAddCommand extends CommandSupport {

    DoctorAddCommand() {
        super("doctor add", RECEPTIONIST_ONLY, "doctor add \"<name>\" <specialty>",
                "Register a doctor and print the generated initial password", "adddoctor");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        List<String> positionals = command.positionals();
        if (positionals.size() < 2) {
            throw new InvalidInputException(
                    "Missing required arguments <name> <specialty>. Usage: " + usage());
        }
        String name = positionals.get(0).trim();
        String specialty = String.join(" ", positionals.subList(1, positionals.size())).trim();
        if (name.isEmpty()) {
            throw new InvalidInputException("The doctor name must not be empty. Usage: " + usage());
        }
        if (specialty.isEmpty()) {
            throw new InvalidInputException("The doctor specialty must not be empty. Usage: " + usage());
        }
        CreatedAccount account = ctx.auth().createDoctor(name, specialty, actor);
        System.out.println("Created doctor account #" + account.user().getId() + ": "
                + account.user().getDisplayName() + ".");
        System.out.println("Initial password: " + account.initialPassword());
        System.out.println("Please note this password now - it is shown only once.");
    }
}

/** Adds or replaces one weekday of a doctor's weekly schedule. */
final class DoctorScheduleCommand extends CommandSupport {

    DoctorScheduleCommand() {
        super("doctor schedule", RECEPTIONIST_ONLY,
                "doctor schedule <doctorId> <day> <start> <end> [--break=HH:mm-HH:mm]",
                "Set a doctor's working hours for one weekday", "setschedule");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int doctorId = requireInt(command, 0, "doctorId");
        DayOfWeek day = parseDay(requirePositional(command, 1, "day"));
        LocalTime start = Formats.parseTime(requirePositional(command, 2, "start"));
        LocalTime end = Formats.parseTime(requirePositional(command, 3, "end"));
        if (!start.isBefore(end)) {
            throw new InvalidInputException("The start time must be before the end time. Usage: "
                    + usage());
        }
        LocalTime breakStart = null;
        LocalTime breakEnd = null;
        String rawBreak = command.flag("break");
        if (rawBreak != null && !rawBreak.isBlank()) {
            int dash = rawBreak.indexOf('-');
            if (dash <= 0 || dash == rawBreak.length() - 1) {
                throw new InvalidInputException(
                        "Break must look like HH:mm-HH:mm, for example 13:00-14:00.");
            }
            breakStart = Formats.parseTime(rawBreak.substring(0, dash).trim());
            breakEnd = Formats.parseTime(rawBreak.substring(dash + 1).trim());
            if (!breakStart.isBefore(breakEnd)) {
                throw new InvalidInputException(
                        "The break start time must be before the break end time.");
            }
            if (breakStart.isBefore(start) || breakEnd.isAfter(end)) {
                throw new InvalidInputException(
                        "The break " + rawBreak + " must fall inside the working hours.");
            }
        }
        ctx.scheduling().setSchedule(doctorId, day, start, end, breakStart, breakEnd);
        Optional<WorkingDay> updated = ctx.scheduling().requireDoctor(doctorId).workingDay(day);
        if (updated.isPresent()) {
            System.out.println("Updated schedule: " + updated.get());
        } else {
            System.out.println("Schedule updated for " + dayName(day) + ".");
        }
    }
}

/** Records a leave period (inclusive) for a doctor. */
final class DoctorLeaveCommand extends CommandSupport {

    DoctorLeaveCommand() {
        super("doctor leave", RECEPTIONIST_ONLY, "doctor leave <doctorId> <fromDate> [<toDate>]",
                "Mark one day or an inclusive date range as leave", "addleave");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int doctorId = requireInt(command, 0, "doctorId");
        LocalDate from = requireDate(command, 1, "fromDate");
        String rawTo = command.positional(2);
        LocalDate to = rawTo == null || rawTo.isBlank() ? from : Formats.parseDate(rawTo);
        if (to.isBefore(from)) {
            throw new InvalidInputException("The end date must not be before the start date.");
        }
        ctx.scheduling().addLeave(doctorId, from, to);
        System.out.println("Leave recorded for " + doctorDisplay(ctx, doctorId) + ": "
                + (to.equals(from)
                        ? Formats.date(from)
                        : Formats.date(from) + " to " + Formats.date(to))
                + ".");
    }
}

/** Lists all doctors with specialty, capacity and working weekdays. */
final class DoctorListCommand extends CommandSupport {

    DoctorListCommand() {
        super("doctor list", ALL_ROLES, "doctor list",
                "List every doctor with their weekly working days", "doctors");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) {
        List<Doctor> doctors = new ArrayList<>(ctx.scheduling().doctors());
        if (doctors.isEmpty()) {
            System.out.println("No doctors registered.");
            return;
        }
        doctors.sort(Comparator.comparing(Doctor::getId));
        System.out.println("Doctors (" + doctors.size() + "):");
        Table table = new Table("Id", "Doctor", "Specialty", "Capacity", "Working days")
                .rightAlign(0, 3);
        for (Doctor doctor : doctors) {
            table.addRow(doctor.getId(),
                    doctor.getDisplayName(),
                    doctor.getSpecialty() == null ? "" : doctor.getSpecialty(),
                    doctor.getDailyCapacity(),
                    workingDays(doctor));
        }
        table.print();
    }

    private static String workingDays(Doctor doctor) {
        Map<DayOfWeek, WorkingDay> schedule = doctor.getWeeklySchedule();
        if (schedule == null || schedule.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner(", ");
        schedule.keySet().stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .forEach(day -> joiner.add(day.name()));
        return joiner.toString();
    }
}
