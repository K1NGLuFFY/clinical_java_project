package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.scheduling.SlotCell;
import com.clinic.scheduling.SlotState;
import com.clinic.util.Formats;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code slots <doctorId> <date> [--type=...]}: the visual day view of the brief.
 *
 * <p>The service produces one {@link SlotCell} per 15 minutes; this command only decides how
 * to merge and wrap those cells and how to print the two summary counters. The counters use the
 * same unit as the grid: free cells out of the cell count of the working window (break cells
 * excluded).</p>
 */
final class SlotsCommand extends CommandSupport {

    /** Target line width for the wrapped cell grid. */
    private static final int LINE_WIDTH = 110;

    SlotsCommand() {
        super("slots", ALL_ROLES,
                "slots <doctorId> <date> [--type=CONSULTATION|FOLLOWUP|EMERGENCY]",
                "Show a doctor's day as 15-minute slot cells with free-slot totals",
                "availability");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        int doctorId = requireInt(command, 0, "doctorId");
        LocalDate date = requireDate(command, 1, "date");
        AppointmentTypeCode type = AppointmentTypeCode.CONSULTATION;
        if (command.flag("type") != null && !command.flag("type").isBlank()) {
            type = AppointmentTypeCode.parse(command.flag("type"));
        }
        Doctor doctor = ctx.scheduling().requireDoctor(doctorId);
        List<SlotCell> cells = ctx.scheduling().describeDay(doctorId, date, type);
        if (cells.isEmpty()) {
            if (doctor.isOnLeave(date)) {
                System.out.println("Dr. " + doctor.getName() + " is on leave on "
                        + Formats.date(date) + ".");
            } else {
                System.out.println("Dr. " + doctor.getName() + " does not work on "
                        + dayName(date.getDayOfWeek()) + ".");
            }
            return;
        }
        System.out.println(doctor.getDisplayName() + " - " + Formats.displayDate(date));
        printGrid(cells);
        int freeCells = countState(cells, SlotState.FREE);
        int totalCells = cells.size() - countState(cells, SlotState.BREAK);
        int capacityUsed = capacityUsed(ctx.scheduling().dailySchedule(doctorId, date));
        System.out.println("Free " + typeName(type).toLowerCase(Locale.ROOT) + " slots: "
                + freeCells + " of " + totalCells
                + "     Daily capacity used: " + capacityUsed + " of " + doctor.getDailyCapacity());
    }

    /** Renders the cell list, merging consecutive break cells into one range. */
    private void printGrid(List<SlotCell> cells) {
        List<String> tokens = new ArrayList<>();
        LocalDateTime breakStart = null;
        LocalDateTime breakEnd = null;
        for (SlotCell cell : cells) {
            if (cell.state() == SlotState.BREAK) {
                if (breakStart == null) {
                    breakStart = cell.start();
                }
                breakEnd = cell.start().plusMinutes(15);
                continue;
            }
            if (breakStart != null) {
                tokens.add(breakToken(breakStart, breakEnd));
                breakStart = null;
                breakEnd = null;
            }
            tokens.add(token(cell));
        }
        if (breakStart != null) {
            tokens.add(breakToken(breakStart, breakEnd));
        }

        StringBuilder line = new StringBuilder();
        for (String token : tokens) {
            if (line.length() > 0 && line.length() + 2 + token.length() > LINE_WIDTH) {
                System.out.println(line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append("  ");
            }
            line.append(token);
        }
        if (line.length() > 0) {
            System.out.println(line);
        }
    }

    private static String breakToken(LocalDateTime start, LocalDateTime end) {
        return "[" + Formats.time(start.toLocalTime()) + "-"
                + Formats.time(end.toLocalTime()) + " BREAK]";
    }

    private static String token(SlotCell cell) {
        String time = Formats.time(cell.start().toLocalTime());
        return switch (cell.state()) {
            case FREE -> time;
            case BOOKED -> "[" + time + " BOOKED]";
            case CHECKED_IN -> "[" + time + " CHECKED_IN]";
            case PAST -> "--:--";
            case LEAVE -> "[" + time + " LEAVE]";
            case OFF_DUTY -> "--:--";
            case BREAK -> breakToken(cell.start(), cell.start().plusMinutes(15));
        };
    }

    private static int countState(List<SlotCell> cells, SlotState state) {
        int count = 0;
        for (SlotCell cell : cells) {
            if (cell.state() == state) {
                count++;
            }
        }
        return count;
    }

    /** Appointments that occupy a slot on the day: BOOKED, CHECKED_IN and COMPLETED. */
    private static int capacityUsed(List<Appointment> appointments) {
        if (appointments == null) {
            return 0;
        }
        int used = 0;
        for (Appointment appointment : appointments) {
            AppointmentStatus status = appointment.getStatus();
            if (status == AppointmentStatus.BOOKED
                    || status == AppointmentStatus.CHECKED_IN
                    || status == AppointmentStatus.COMPLETED) {
                used++;
            }
        }
        return used;
    }
}
