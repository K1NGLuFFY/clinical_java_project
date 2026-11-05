package com.clinic.scheduling;

import com.clinic.exception.DoctorOnLeaveException;
import com.clinic.exception.OutsideWorkingHoursException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.model.AppointmentType;
import com.clinic.model.Doctor;
import com.clinic.model.WorkingDay;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * The scheduling constants and the validation rules shared by booking and slot search.
 *
 * <p>Slots are aligned to {@link #SLOT_MINUTES} minute boundaries. A booking may be placed at
 * most {@link #MAX_DAYS_AHEAD} days ahead and normally needs {@link #MIN_NOTICE_HOURS} hour of
 * notice; the appointment type decides which rules apply (an emergency, for example, is
 * same-day only and ignores the minimum notice).</p>
 *
 * <p>Every rule is a pure function of its arguments, which keeps them easy to unit test and
 * safe to call from the console UI, the services and the slot finder.</p>
 */
public final class ScheduleRules {

    /** Length of one diary slot in minutes. */
    public static final int SLOT_MINUTES = 15;

    /** How far ahead an appointment may be booked. */
    public static final int MAX_DAYS_AHEAD = 60;

    /** Minimum notice before the start of an appointment, in hours. */
    public static final long MIN_NOTICE_HOURS = 1;

    private ScheduleRules() {
    }

    /**
     * Returns true when the given start time is a valid slot boundary.
     *
     * <p>A boundary is a whole minute, with no seconds or nanoseconds, whose minute is a
     * multiple of {@link #SLOT_MINUTES} (for example 09:00, 09:15, 09:30).</p>
     *
     * @param start candidate start time
     * @return true when the time is a slot boundary, false for null or any other time
     */
    public static boolean isSlotBoundary(LocalDateTime start) {
        return start != null
                && start.getSecond() == 0
                && start.getNano() == 0
                && start.getMinute() % SLOT_MINUTES == 0;
    }

    /**
     * Returns the doctor's working day for a date.
     *
     * @param doctor doctor whose weekly schedule is consulted
     * @param date   calendar date
     * @return the working day defined for that weekday
     * @throws OutsideWorkingHoursException when the doctor does not work on that weekday
     */
    public static WorkingDay workingDayOrThrow(Doctor doctor, LocalDate date) throws OutsideWorkingHoursException {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(date, "date");
        WorkingDay workingDay = doctor.getWeeklySchedule() == null
                ? null
                : doctor.getWeeklySchedule().get(date.getDayOfWeek());
        if (workingDay == null) {
            throw new OutsideWorkingHoursException(doctor.getDisplayName() + " does not work on "
                    + date.getDayOfWeek() + " (" + date + ").");
        }
        return workingDay;
    }

    /**
     * Refuses a date on which the doctor is on leave.
     *
     * @param doctor doctor whose leave periods are consulted
     * @param date   calendar date
     * @throws DoctorOnLeaveException when the date falls inside a leave period
     */
    public static void checkLeave(Doctor doctor, LocalDate date) throws DoctorOnLeaveException {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(date, "date");
        if (doctor.isOnLeave(date)) {
            throw new DoctorOnLeaveException(doctor.getDisplayName() + " is on leave on " + date + ".");
        }
    }

    /**
     * Validates that an appointment range fits into the doctor's working day.
     *
     * <p>The range must be completely inside {@code start..end} of the working day and, unless
     * the type may use the break, it must not overlap the break period.</p>
     *
     * @param doctor doctor whose schedule is consulted
     * @param date   calendar date of the appointment
     * @param range  appointment range to validate
     * @param type   appointment type, which decides whether the break may be used
     * @throws DoctorOnLeaveException      when the doctor is on leave that date
     * @throws OutsideWorkingHoursException when the doctor does not work that date, the range
     *                                      leaves the working hours, or it overlaps the break
     */
    public static void checkWorkingHours(Doctor doctor, LocalDate date, TimeRange range, AppointmentType type)
            throws OutsideWorkingHoursException, DoctorOnLeaveException {
        Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(type, "type");
        if (doctor.isOnLeave(date)) {
            throw new DoctorOnLeaveException(doctor.getDisplayName() + " is on leave on " + date + ".");
        }
        WorkingDay workingDay = workingDayOrThrow(doctor, date);
        LocalDateTime dayStart = workingDay.startOn(date);
        LocalDateTime dayEnd = workingDay.endOn(date);
        if (!dayEnd.isAfter(dayStart)) {
            throw new OutsideWorkingHoursException(doctor.getDisplayName()
                    + " has no usable working hours on " + date + ".");
        }
        TimeRange workingHours = new TimeRange(dayStart, dayEnd);
        if (!workingHours.contains(range)) {
            throw new OutsideWorkingHoursException("The requested time " + range + " is outside "
                    + doctor.getDisplayName() + "'s working hours on " + date + " ("
                    + workingDay.getStart() + "-" + workingDay.getEnd() + ").");
        }
        if (workingDay.hasBreak()
                && workingDay.getBreakEnd().isAfter(workingDay.getBreakStart())
                && !type.mayUseBreak()) {
            TimeRange breakRange = new TimeRange(workingDay.breakStartOn(date), workingDay.breakEndOn(date));
            if (range.overlaps(breakRange)) {
                throw new OutsideWorkingHoursException("The requested time " + range + " overlaps "
                        + doctor.getDisplayName() + "'s break on " + date + " ("
                        + workingDay.getBreakStart() + "-" + workingDay.getBreakEnd() + ").");
            }
        }
    }

    /**
     * Validates the booking window and the minimum notice of a start time.
     *
     * <ul>
     *   <li>the start must be strictly in the future;</li>
     *   <li>a same-day-only type (emergency) must start on today's date;</li>
     *   <li>any other type must start at most {@link #MAX_DAYS_AHEAD} days ahead;</li>
     *   <li>a type that needs notice must start at least {@link #MIN_NOTICE_HOURS} hour from now.</li>
     * </ul>
     *
     * @param start requested start time
     * @param type  appointment type
     * @param now   current time, taken from the injected clock
     * @throws SlotUnavailableException when the start time violates one of the window rules
     */
    public static void checkBookingWindow(LocalDateTime start, AppointmentType type, LocalDateTime now)
            throws SlotUnavailableException {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(now, "now");
        if (!start.isAfter(now)) {
            throw new SlotUnavailableException("Cannot book " + start + ": the start time is in the past.",
                    List.of());
        }
        LocalDate today = now.toLocalDate();
        if (type.sameDayOnly() && !start.toLocalDate().equals(today)) {
            throw new SlotUnavailableException("Emergency appointments are same-day only; " + start
                    + " is not today (" + today + ").", List.of());
        }
        if (!type.sameDayOnly()) {
            LocalDate lastBookableDate = today.plusDays(MAX_DAYS_AHEAD);
            if (start.toLocalDate().isAfter(lastBookableDate)) {
                throw new SlotUnavailableException("Appointments can be booked up to " + MAX_DAYS_AHEAD
                        + " days ahead; " + start.toLocalDate() + " is after " + lastBookableDate + ".",
                        List.of());
            }
        }
        if (type.requiresMinimumNotice() && start.isBefore(now.plusHours(MIN_NOTICE_HOURS))) {
            throw new SlotUnavailableException("Appointments need at least " + MIN_NOTICE_HOURS
                    + " hour notice; the earliest start is " + now.plusHours(MIN_NOTICE_HOURS) + ".",
                    List.of());
        }
    }
}
