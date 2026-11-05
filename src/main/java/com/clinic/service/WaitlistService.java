package com.clinic.service;

import com.clinic.event.EventBus;
import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.TimeWindow;
import com.clinic.model.User;
import com.clinic.model.WaitlistEntry;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.WaitlistRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The waiting list. Offers are FIFO, hold the slot for at most two hours (or until one hour
 * before the slot when that is sooner) and expire lazily: at startup and before every command
 * (no background threads).
 */
public class WaitlistService {

    /** How long an offer is held. */
    public static final int OFFER_HOLD_HOURS = 2;
    /** A slot that starts in less than this many hours cannot be offered any more. */
    public static final int OFFER_CUTOFF_HOURS = 1;

    private final WaitlistRepository waitlist;
    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final EventBus events;
    private final Clock clock;

    private BookingGateway bookingGateway;

    public WaitlistService(WaitlistRepository waitlist, UserRepository users, AppointmentRepository appointments,
                           EventBus events, Clock clock) {
        this.waitlist = waitlist;
        this.users = users;
        this.appointments = appointments;
        this.events = events;
        this.clock = clock;
    }

    public void setBookingGateway(BookingGateway bookingGateway) {
        this.bookingGateway = bookingGateway;
    }

    /** Joins the waiting list for a doctor and date range (patients only, for themselves). */
    public WaitlistEntry join(int patientId, int doctorId, LocalDate from, LocalDate to,
                              TimeWindow timeWindow, User actor) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.join not implemented yet");
    }

    /** Removes an open entry (patient cancels their own interest). */
    public void leave(int entryId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.leave not implemented yet");
    }

    /** Accepts an active offer and books the held slot. */
    public Appointment accept(int entryId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.accept not implemented yet");
    }

    /** Declines an active offer; the next matching patient is offered the slot. */
    public void decline(int entryId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.decline not implemented yet");
    }

    /**
     * Expires overdue offers and re-offers their slot to the next matching patient.
     *
     * @return number of offers expired by this call
     */
    public int expireOffers() throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.expireOffers not implemented yet");
    }

    public List<WaitlistEntry> listForPatient(int patientId) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.listForPatient not implemented yet");
    }

    /** Active offer holding this exact slot, if any. Used by the booking validation. */
    public Optional<WaitlistEntry> findActiveOfferFor(int doctorId, LocalDateTime slotStart) {
        throw new UnsupportedOperationException("WaitlistService.findActiveOfferFor not implemented yet");
    }

    /** Offers a freshly freed slot to the first matching waiting-list entry. */
    public void onSlotFreed(int doctorId, LocalDateTime slotStart, AppointmentTypeCode type) throws ClinicException {
        throw new UnsupportedOperationException("WaitlistService.onSlotFreed not implemented yet");
    }
}
