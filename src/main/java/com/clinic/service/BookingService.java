package com.clinic.service;

import com.clinic.event.EventBus;
import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.User;
import com.clinic.model.WaitlistEntry;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.WaitlistRepository;
import com.clinic.scheduling.SlotFinder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Booking, cancellation, rescheduling and the no-show block.
 *
 * <p>All write operations run under a single {@link java.util.concurrent.locks.ReentrantLock}
 * so two concurrent requests for the same slot cannot both succeed. The old slot of a
 * reschedule and every cancelled slot are offered to the waiting list.</p>
 */
public class BookingService implements BookingGateway {

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;
    private final SlotFinder slotFinder;
    private final BillingService billing;
    private final EventBus events;
    private final Clock clock;

    /** Wired after construction to break the booking/waiting-list cycle. */
    private WaitlistService waitlistService;

    public BookingService(UserRepository users, AppointmentRepository appointments, WaitlistRepository waitlist,
                          SlotFinder slotFinder, BillingService billing, EventBus events, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.waitlist = waitlist;
        this.slotFinder = slotFinder;
        this.billing = billing;
        this.events = events;
        this.clock = clock;
    }

    public void setWaitlistService(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    public Optional<Appointment> findById(int appointmentId) {
        return appointments.findById(appointmentId);
    }

    /** All appointments of a patient, ordered by start time (patients only see their own). */
    public List<Appointment> appointmentsForPatient(int patientId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.appointmentsForPatient not implemented yet");
    }

    /**
     * Books an appointment after validating every rule of the brief. Patients may only book
     * for themselves; receptionists may book for any patient.
     */
    public Appointment book(int patientId, int doctorId, LocalDateTime start,
                            AppointmentTypeCode type, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.book not implemented yet");
    }

    /** Cancels an appointment, applies the late fee when inside 24 hours, frees the slot. */
    public Appointment cancel(int appointmentId, String reason, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.cancel not implemented yet");
    }

    /** Atomic reschedule: the new slot is validated before the old booking is replaced. */
    public Appointment reschedule(int appointmentId, LocalDateTime newStart, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.reschedule not implemented yet");
    }

    /** Receptionist checks the patient in on the day of the appointment. */
    public Appointment checkIn(int appointmentId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.checkIn not implemented yet");
    }

    /** Doctor finishes the visit; the appointment must be CHECKED_IN. */
    public Appointment complete(int appointmentId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.complete not implemented yet");
    }

    /** Receptionist marks a no-show more than 15 minutes after the start time. */
    public Appointment markNoShow(int appointmentId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.markNoShow not implemented yet");
    }

    /** Confirms a waiting-list offer by booking the held slot (called by WaitlistService). */
    @Override
    public Appointment bookReservedSlot(WaitlistEntry entry, User actor) throws ClinicException {
        throw new UnsupportedOperationException("BookingService.bookReservedSlot not implemented yet");
    }
}
