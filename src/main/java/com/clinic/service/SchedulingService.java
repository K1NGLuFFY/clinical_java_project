package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.scheduling.SlotCell;
import com.clinic.scheduling.SlotFinder;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Doctor schedules, leave and free-slot queries. Read-only with respect to appointments:
 * booking lives in {@link BookingService}.
 */
public class SchedulingService {

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

    public Doctor requireDoctor(int doctorId) throws DoctorNotFoundException {
        throw new UnsupportedOperationException("SchedulingService.requireDoctor not implemented yet");
    }

    /** Free start times for a type on one date, honouring working hours, break, leave and diary. */
    public List<LocalDateTime> freeSlots(int doctorId, LocalDate date, AppointmentTypeCode type) throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.freeSlots not implemented yet");
    }

    /** Next free start times after {@code after}, searching later dates when the day is full. */
    public List<LocalDateTime> suggest(int doctorId, LocalDateTime after, AppointmentTypeCode type, int limit)
            throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.suggest not implemented yet");
    }

    /** Full slot map of one day for the {@code slots} command. */
    public List<SlotCell> describeDay(int doctorId, LocalDate date, AppointmentTypeCode type) throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.describeDay not implemented yet");
    }

    /** Adds or replaces one weekday of the weekly schedule. */
    public void setSchedule(int doctorId, DayOfWeek day, LocalTime start, LocalTime end,
                            LocalTime breakStart, LocalTime breakEnd) throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.setSchedule not implemented yet");
    }

    /** Adds a leave period; refuses overlapping leave periods. */
    public void addLeave(int doctorId, LocalDate from, LocalDate to) throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.addLeave not implemented yet");
    }

    /** Appointments of one doctor on one date, ordered by start time. */
    public List<Appointment> dailySchedule(int doctorId, LocalDate date) throws ClinicException {
        throw new UnsupportedOperationException("SchedulingService.dailySchedule not implemented yet");
    }

    public List<Doctor> doctors() {
        throw new UnsupportedOperationException("SchedulingService.doctors not implemented yet");
    }
}
