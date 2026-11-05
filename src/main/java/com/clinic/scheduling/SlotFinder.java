package com.clinic.scheduling;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentType;
import com.clinic.model.Doctor;
import com.clinic.model.WorkingDay;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Pure slot arithmetic for one doctor: is a start time free, which start times are free on a
 * date, what are the next free start times, and how does a day look as a grid of cells.
 *
 * <p>The finder never touches a repository and never mutates its arguments. The current time
 * always comes from the injected {@link Clock}, so tests can move time without sleeping.</p>
 *
 * <p>Appointments passed in are expected to belong to the same doctor; the finder uses their
 * stored start/end and status and ignores every other appointment attribute.</p>
 */
public class SlotFinder {

    private final Clock clock;

    /**
     * Creates a finder that reads the current time from the given clock.
     *
     * @param clock clock used for every "now" decision
     */
    public SlotFinder(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns true when the given start time is a valid slot boundary.
     *
     * @param start candidate start time
     * @return true when the time is a slot boundary, false for null or any other time
     */
    public boolean isSlotBoundary(LocalDateTime start) {
        return ScheduleRules.isSlotBoundary(start);
    }

    /**
     * Returns true when an appointment of the given type could start at the given time.
     *
     * <p>The start must be a slot boundary, strictly in the future, inside the booking window
     * and notice rules of the type, inside the doctor's working hours (including the break
     * rule) and must not overlap a blocking appointment or exceed the daily capacity.</p>
     *
     * @param doctor             doctor whose schedule and capacity are consulted
     * @param start              candidate start time
     * @param type               appointment type
     * @param doctorAppointments all appointments of the doctor, in any order
     * @return true when the slot can be booked, false otherwise
     */
    public boolean isFree(Doctor doctor, LocalDateTime start, AppointmentType type,
                          Collection<Appointment> doctorAppointments) {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(type, "type");
        return isFreeAt(doctor, start, type, doctorAppointments, LocalDateTime.now(clock));
    }

    /**
     * Returns every free start time of the given type on one date, in ascending order.
     *
     * @param doctor             doctor whose schedule and capacity are consulted
     * @param date               calendar date
     * @param type               appointment type
     * @param doctorAppointments all appointments of the doctor, in any order
     * @return the free start times, empty when the doctor does not work that date or is on leave
     */
    public List<LocalDateTime> freeSlots(Doctor doctor, LocalDate date, AppointmentType type,
                                         Collection<Appointment> doctorAppointments) {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(type, "type");
        WorkingDay workingDay = workingDayOn(doctor, date);
        if (workingDay == null || doctor.isOnLeave(date)) {
            return List.of();
        }
        LocalDateTime dayStart = workingDay.startOn(date);
        LocalDateTime dayEnd = workingDay.endOn(date);
        if (!dayEnd.isAfter(dayStart)) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> slots = new ArrayList<>();
        for (LocalDateTime start = dayStart;
             !start.plus(type.getDuration()).isAfter(dayEnd);
             start = start.plusMinutes(ScheduleRules.SLOT_MINUTES)) {
            if (isFreeAt(doctor, start, type, doctorAppointments, now)) {
                slots.add(start);
            }
        }
        return List.copyOf(slots);
    }

    /**
     * Returns up to {@code limit} free start times at or after {@code after}.
     *
     * <p>Today is scanned first and later dates follow, up to
     * {@link ScheduleRules#MAX_DAYS_AHEAD} days ahead. Days off and leave days are skipped.
     * A same-day-only type such as an emergency is searched on today only. The limit is capped
     * between 1 and 10 so a caller can never ask for an unbounded scan.</p>
     *
     * @param doctor             doctor whose schedule and capacity are consulted
     * @param after              earliest acceptable start time; {@code null} means now
     * @param type               appointment type
     * @param doctorAppointments all appointments of the doctor, in any order
     * @param limit              maximum number of suggestions requested
     * @return the suggestions in ascending order, possibly fewer than the limit
     */
    public List<LocalDateTime> suggest(Doctor doctor, LocalDateTime after, AppointmentType type,
                                       Collection<Appointment> doctorAppointments, int limit) {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(type, "type");
        int cappedLimit = Math.max(1, Math.min(10, limit));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lowerBound = after == null ? now : after;
        LocalDate today = now.toLocalDate();
        LocalDate lastDay = today.plusDays(ScheduleRules.MAX_DAYS_AHEAD);
        List<LocalDateTime> suggestions = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (type.sameDayOnly() && !day.equals(today)) {
                break;
            }
            WorkingDay workingDay = workingDayOn(doctor, day);
            if (workingDay == null || doctor.isOnLeave(day)) {
                continue;
            }
            LocalDateTime dayStart = workingDay.startOn(day);
            LocalDateTime dayEnd = workingDay.endOn(day);
            if (!dayEnd.isAfter(dayStart)) {
                continue;
            }
            for (LocalDateTime start = dayStart;
                 !start.plus(type.getDuration()).isAfter(dayEnd);
                 start = start.plusMinutes(ScheduleRules.SLOT_MINUTES)) {
                if (start.isBefore(lowerBound)) {
                    continue;
                }
                if (isFreeAt(doctor, start, type, doctorAppointments, now)) {
                    suggestions.add(start);
                    if (suggestions.size() >= cappedLimit) {
                        suggestions.sort(LocalDateTime::compareTo);
                        return List.copyOf(suggestions);
                    }
                }
            }
        }
        suggestions.sort(LocalDateTime::compareTo);
        return List.copyOf(suggestions);
    }

    /**
     * Renders one day of a doctor's diary as 15 minute cells.
     *
     * <p>There is one cell per slot from the start of the working day until its end
     * (exclusive). A cell is {@link SlotState#BOOKED} or {@link SlotState#CHECKED_IN} when a
     * blocking appointment covers it, {@link SlotState#BREAK} when it falls inside the
     * doctor's break, {@link SlotState#PAST} when it started before now, and
     * {@link SlotState#FREE} otherwise. A day off or a leave day produces an empty list.</p>
     *
     * @param doctor             doctor whose diary is rendered
     * @param date               calendar date
     * @param type               appointment type the grid is prepared for (part of the service
     *                           contract; it does not change the state of a booked cell)
     * @param doctorAppointments all appointments of the doctor, in any order
     * @return the ordered, non-overlapping cells, empty when the doctor is away that date
     */
    public List<SlotCell> describeDay(Doctor doctor, LocalDate date, AppointmentType type,
                                      Collection<Appointment> doctorAppointments) {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(type, "type");
        WorkingDay workingDay = workingDayOn(doctor, date);
        if (workingDay == null || doctor.isOnLeave(date)) {
            return List.of();
        }
        LocalDateTime dayStart = workingDay.startOn(date);
        LocalDateTime dayEnd = workingDay.endOn(date);
        if (!dayEnd.isAfter(dayStart)) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime breakStart = workingDay.breakStartOn(date);
        LocalDateTime breakEnd = workingDay.breakEndOn(date);
        List<SlotCell> cells = new ArrayList<>();
        for (LocalDateTime cellStart = dayStart;
             cellStart.isBefore(dayEnd);
             cellStart = cellStart.plusMinutes(ScheduleRules.SLOT_MINUTES)) {
            LocalDateTime cellEnd = cellStart.plusMinutes(ScheduleRules.SLOT_MINUTES);
            Appointment occupying = blockingAppointment(doctorAppointments, cellStart, cellEnd);
            if (occupying != null) {
                SlotState state = occupying.getStatus() == AppointmentStatus.CHECKED_IN
                        ? SlotState.CHECKED_IN
                        : SlotState.BOOKED;
                cells.add(new SlotCell(cellStart, state, occupying.getId(), occupying.getType()));
            } else if (breakStart != null && !cellStart.isBefore(breakStart) && cellStart.isBefore(breakEnd)) {
                cells.add(new SlotCell(cellStart, SlotState.BREAK, null, null));
            } else if (cellStart.isBefore(now)) {
                cells.add(new SlotCell(cellStart, SlotState.PAST, null, null));
            } else {
                cells.add(new SlotCell(cellStart, SlotState.FREE, null, null));
            }
        }
        return List.copyOf(cells);
    }

    /** Returns the doctor's working day for a date, or null when the doctor does not work then. */
    private static WorkingDay workingDayOn(Doctor doctor, LocalDate date) {
        if (doctor.getWeeklySchedule() == null) {
            return null;
        }
        return doctor.getWeeklySchedule().get(date.getDayOfWeek());
    }

    /** Core availability check; {@code now} is passed in so one scan uses one consistent clock. */
    private boolean isFreeAt(Doctor doctor, LocalDateTime start, AppointmentType type,
                             Collection<Appointment> doctorAppointments, LocalDateTime now) {
        if (!ScheduleRules.isSlotBoundary(start)) {
            return false;
        }
        TimeRange range = TimeRange.of(start, type.getDuration());
        try {
            ScheduleRules.checkWorkingHours(doctor, start.toLocalDate(), range, type);
            ScheduleRules.checkBookingWindow(start, type, now);
        } catch (ClinicException ex) {
            return false;
        }
        if (blockingAppointment(doctorAppointments, range.getStart(), range.getEnd()) != null) {
            return false;
        }
        return type.ignoresDailyCapacity()
                || dailyLoad(start.toLocalDate(), doctorAppointments) < doctor.getDailyCapacity();
    }

    /**
     * Returns the blocking appointment that overlaps the given range, or null.
     *
     * <p>Only BOOKED and CHECKED_IN appointments block; CANCELLED, NO_SHOW and COMPLETED do
     * not. When more than one appointment overlaps, the earliest one is returned so the grid
     * is deterministic even when the caller supplies an unordered collection.</p>
     */
    private static Appointment blockingAppointment(Collection<Appointment> doctorAppointments,
                                                   LocalDateTime start, LocalDateTime end) {
        if (doctorAppointments == null || doctorAppointments.isEmpty()) {
            return null;
        }
        Appointment best = null;
        for (Appointment candidate : doctorAppointments) {
            if (!blocks(candidate, start, end)) {
                continue;
            }
            if (best == null
                    || candidate.getStart().isBefore(best.getStart())
                    || (candidate.getStart().equals(best.getStart()) && candidate.getId() < best.getId())) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean blocks(Appointment appointment, LocalDateTime start, LocalDateTime end) {
        if (appointment == null || appointment.getStart() == null || appointment.getEnd() == null) {
            return false;
        }
        AppointmentStatus status = appointment.getStatus();
        if (status != AppointmentStatus.BOOKED && status != AppointmentStatus.CHECKED_IN) {
            return false;
        }
        return appointment.getStart().isBefore(end) && start.isBefore(appointment.getEnd());
    }

    /** Number of appointments on the date that use up daily capacity. */
    private static int dailyLoad(LocalDate date, Collection<Appointment> doctorAppointments) {
        if (doctorAppointments == null) {
            return 0;
        }
        int count = 0;
        for (Appointment appointment : doctorAppointments) {
            if (appointment == null || appointment.getStart() == null) {
                continue;
            }
            if (!appointment.getStart().toLocalDate().equals(date)) {
                continue;
            }
            AppointmentStatus status = appointment.getStatus();
            if (status == AppointmentStatus.BOOKED
                    || status == AppointmentStatus.CHECKED_IN
                    || status == AppointmentStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }
}
