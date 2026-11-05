package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.InvalidInputException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.LeavePeriod;
import com.clinic.model.WorkingDay;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.scheduling.SlotCell;
import com.clinic.scheduling.SlotFinder;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Doctor schedules, leave and free-slot queries. Read-only with respect to appointments:
 * booking lives in {@link BookingService}.
 */
public class SchedulingService {

    private static final Comparator<Appointment> BY_START =
            Comparator.comparing(Appointment::getStart, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final SlotFinder slotFinder;
    private final Clock clock;

    public SchedulingService(UserRepository users, AppointmentRepository appointments,
                             SlotFinder slotFinder, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.slotFinder = slotFinder;
        this.clock = clock;
    }

    /**
     * Loads a doctor or fails with the clinic's specific exception.
     *
     * @param doctorId doctor id
     * @return the doctor
     * @throws DoctorNotFoundException when no doctor has this id
     */
    public Doctor requireDoctor(int doctorId) throws DoctorNotFoundException {
        return users.findDoctor(doctorId).orElseThrow(
                () -> new DoctorNotFoundException("No doctor with id " + doctorId + "."));
    }

    /**
     * Free start times for a type on one date, honouring working hours, break, leave and diary.
     *
     * @param doctorId doctor id
     * @param date     requested date
     * @param type     appointment type
     * @return free start times, ordered by time
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when the date or type is missing
     */
    public List<LocalDateTime> freeSlots(int doctorId, LocalDate date, AppointmentTypeCode type)
            throws ClinicException {
        Doctor doctor = requireDoctor(doctorId);
        if (date == null) {
            throw new InvalidInputException("A date is required.");
        }
        AppointmentType appointmentType = requireType(type);
        return slotFinder.freeSlots(doctor, date, appointmentType, appointments.findByDoctor(doctorId));
    }

    /**
     * Next free start times after {@code after}, searching later dates when the day is full.
     *
     * @param doctorId doctor id
     * @param after    lower bound for the search
     * @param type     appointment type
     * @param limit    maximum number of suggestions
     * @return free start times, ordered by time
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when an argument is missing or the limit is not positive
     */
    public List<LocalDateTime> suggest(int doctorId, LocalDateTime after, AppointmentTypeCode type, int limit)
            throws ClinicException {
        Doctor doctor = requireDoctor(doctorId);
        if (after == null) {
            throw new InvalidInputException("A start time is required for the search.");
        }
        if (limit < 1) {
            throw new InvalidInputException("The number of suggestions must be at least 1.");
        }
        AppointmentType appointmentType = requireType(type);
        return slotFinder.suggest(doctor, after, appointmentType, appointments.findByDoctor(doctorId), limit);
    }

    /**
     * Full slot map of one day for the {@code slots} command.
     *
     * @param doctorId doctor id
     * @param date     requested date
     * @param type     appointment type deciding the slot length
     * @return one cell per 15 minute slot of the working day
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when the date or type is missing
     */
    public List<SlotCell> describeDay(int doctorId, LocalDate date, AppointmentTypeCode type)
            throws ClinicException {
        Doctor doctor = requireDoctor(doctorId);
        if (date == null) {
            throw new InvalidInputException("A date is required.");
        }
        AppointmentType appointmentType = requireType(type);
        return slotFinder.describeDay(doctor, date, appointmentType, appointments.findByDoctor(doctorId));
    }

    /**
     * Adds or replaces one weekday of the weekly schedule.
     *
     * <p>The break must sit completely inside the working hours and must have both ends so a
     * half-configured break can never be stored.</p>
     *
     * @param doctorId   doctor id
     * @param day        weekday to set
     * @param start      start of the working day
     * @param end        end of the working day
     * @param breakStart optional break start
     * @param breakEnd   optional break end
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when the times are inconsistent
     * @throws ClinicException         when the doctor cannot be persisted
     */
    public void setSchedule(int doctorId, DayOfWeek day, LocalTime start, LocalTime end,
                            LocalTime breakStart, LocalTime breakEnd) throws ClinicException {
        Doctor doctor = requireDoctor(doctorId);
        if (day == null || start == null || end == null) {
            throw new InvalidInputException("A weekday, start time and end time are required.");
        }
        if (!start.isBefore(end)) {
            throw new InvalidInputException("The schedule start time must be before the end time.");
        }
        boolean hasBreakStart = breakStart != null;
        boolean hasBreakEnd = breakEnd != null;
        if (hasBreakStart != hasBreakEnd) {
            throw new InvalidInputException(
                    "Both break start and break end are required, or leave both empty.");
        }
        if (hasBreakStart) {
            if (!breakStart.isBefore(breakEnd)) {
                throw new InvalidInputException("The break start time must be before the break end time.");
            }
            if (breakStart.isBefore(start) || breakEnd.isAfter(end)) {
                throw new InvalidInputException(
                        "The break must be inside the working hours (" + start + "-" + end + ").");
            }
        }
        WorkingDay workingDay = hasBreakStart
                ? new WorkingDay(day, start, end, breakStart, breakEnd)
                : new WorkingDay(day, start, end);
        doctor.setWorkingDay(workingDay);
        users.save(doctor);
    }

    /**
     * Adds a leave period; refuses overlapping leave periods.
     *
     * <p>Leave may start today or later. The period is inclusive on both ends.</p>
     *
     * @param doctorId doctor id
     * @param from     first day of leave
     * @param to       last day of leave (may equal {@code from})
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when the range is invalid or overlaps existing leave
     * @throws ClinicException         when the doctor cannot be persisted
     */
    public void addLeave(int doctorId, LocalDate from, LocalDate to) throws ClinicException {
        Doctor doctor = requireDoctor(doctorId);
        if (from == null || to == null) {
            throw new InvalidInputException("Both leave dates are required.");
        }
        if (from.isAfter(to)) {
            throw new InvalidInputException("The leave start date must not be after the end date.");
        }
        if (from.isBefore(LocalDate.now(clock))) {
            throw new InvalidInputException("Leave cannot start in the past.");
        }
        for (LeavePeriod existing : doctor.getLeavePeriods()) {
            if (existing == null || existing.getFrom() == null) {
                continue;
            }
            LocalDate existingFrom = existing.getFrom();
            LocalDate existingTo = existing.getTo() == null ? existingFrom : existing.getTo();
            if (!existingFrom.isAfter(to) && !from.isAfter(existingTo)) {
                throw new InvalidInputException(
                        "This leave period overlaps existing leave (" + existing + ").");
            }
        }
        doctor.addLeave(new LeavePeriod(from, to));
        users.save(doctor);
    }

    /**
     * Appointments of one doctor on one date, ordered by start time.
     *
     * @param doctorId doctor id
     * @param date     requested date
     * @return the doctor's appointments for that day
     * @throws DoctorNotFoundException when no doctor has this id
     * @throws InvalidInputException   when the date is missing
     */
    public List<Appointment> dailySchedule(int doctorId, LocalDate date) throws ClinicException {
        requireDoctor(doctorId);
        if (date == null) {
            throw new InvalidInputException("A date is required.");
        }
        return appointments.findByDoctorAndDate(doctorId, date).stream()
                .sorted(BY_START)
                .toList();
    }

    /**
     * All doctors, ordered by name.
     *
     * @return doctors sorted by name
     */
    public List<Doctor> doctors() {
        return users.findAllDoctors().stream()
                .sorted(Comparator.comparing(SchedulingService::nameOf, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static AppointmentType requireType(AppointmentTypeCode type) throws InvalidInputException {
        if (type == null) {
            throw new InvalidInputException("An appointment type is required.");
        }
        AppointmentType appointmentType = AppointmentTypeFactory.forCode(type);
        if (appointmentType == null) {
            throw new InvalidInputException("Unknown appointment type '" + type + "'.");
        }
        return appointmentType;
    }

    private static String nameOf(Doctor doctor) {
        return doctor.getName() == null ? "" : doctor.getName();
    }
}
