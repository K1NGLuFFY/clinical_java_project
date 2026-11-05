package com.clinic.service;

import com.clinic.event.ClinicEvent;
import com.clinic.event.EventBus;
import com.clinic.event.EventType;
import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.exception.WaitlistEntryNotFoundException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.Role;
import com.clinic.model.TimeWindow;
import com.clinic.model.User;
import com.clinic.model.WaitlistEntry;
import com.clinic.model.WaitlistStatus;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.WaitlistRepository;
import com.clinic.scheduling.ScheduleRules;
import com.clinic.util.Formats;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
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

    private static final Comparator<WaitlistEntry> FIFO =
            Comparator.comparing(WaitlistEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(WaitlistEntry::getId);

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

    /**
     * Wires the booking port after construction (the two services are mutually dependent;
     * {@code Main} breaks the cycle with this setter).
     *
     * @param bookingGateway port used to confirm an accepted offer
     */
    public void setBookingGateway(BookingGateway bookingGateway) {
        this.bookingGateway = bookingGateway;
    }

    /**
     * Joins the waiting list for a doctor and date range (patients only, for themselves).
     *
     * @param patientId  patient on whose behalf the entry is created
     * @param doctorId   wanted doctor
     * @param from       first acceptable date (inclusive)
     * @param to         last acceptable date (inclusive, at most 60 days ahead)
     * @param timeWindow optional preferred window; {@code null} means any time
     * @param actor      logged-in user
     * @return the stored waiting-list entry
     * @throws UnauthorizedActionException when the actor is not the patient themselves
     * @throws DoctorNotFoundException     when no doctor has this id
     * @throws InvalidInputException       when the date range is invalid or a duplicate entry exists
     * @throws ClinicException             when the entry cannot be persisted
     */
    public WaitlistEntry join(int patientId, int doctorId, LocalDate from, LocalDate to,
                              TimeWindow timeWindow, User actor) throws ClinicException {
        if (actor == null || actor.getRole() != Role.PATIENT) {
            throw new UnauthorizedActionException("Only patients can join the waiting list.");
        }
        if (actor.getId() != patientId) {
            throw new UnauthorizedActionException("Patients can only join the waiting list for themselves.");
        }
        Doctor doctor = users.findDoctor(doctorId).orElseThrow(
                () -> new DoctorNotFoundException("No doctor with id " + doctorId + "."));
        if (from == null || to == null) {
            throw new InvalidInputException("Both waiting-list dates are required.");
        }
        if (from.isAfter(to)) {
            throw new InvalidInputException("The waiting-list start date must not be after the end date.");
        }
        LocalDate today = LocalDate.now(clock);
        if (from.isBefore(today)) {
            throw new InvalidInputException("The waiting-list start date cannot be in the past.");
        }
        LocalDate lastAllowed = today.plusDays(ScheduleRules.MAX_DAYS_AHEAD);
        if (to.isAfter(lastAllowed)) {
            throw new InvalidInputException("The waiting-list end date cannot be more than "
                    + ScheduleRules.MAX_DAYS_AHEAD + " days ahead.");
        }
        boolean duplicate = waitlist.findByPatient(patientId).stream()
                .anyMatch(entry -> entry.getDoctorId() == doctorId
                        && entry.getStatus() != null && entry.getStatus().isOpen());
        if (duplicate) {
            throw new InvalidInputException("You already have an open waiting-list entry for "
                    + doctor.getDisplayName() + ".");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        WaitlistEntry entry = new WaitlistEntry(waitlist.nextId(), patientId, doctorId, from, to,
                timeWindow == null ? TimeWindow.ANY : timeWindow, now);
        waitlist.save(entry);
        String message = "You joined the waiting list for " + doctor.getDisplayName() + " from "
                + Formats.date(from) + " to " + Formats.date(to) + ".";
        events.publish(ClinicEvent.of(EventType.WAITLIST_JOINED, now, message, patientId));
        return entry;
    }

    /**
     * Removes an open entry (patient cancels their own interest; a receptionist may also do it).
     *
     * <p>When the removed entry held an offer, the slot is passed on to the next matching
     * patient. Only WAITING or OFFERED entries can be removed.</p>
     *
     * @param entryId waiting-list entry id
     * @param actor   logged-in user
     * @throws WaitlistEntryNotFoundException when the entry does not exist
     * @throws UnauthorizedActionException    when the actor may not remove it
     * @throws InvalidInputException          when the entry is no longer open
     * @throws ClinicException                when the change cannot be persisted
     */
    public void leave(int entryId, User actor) throws ClinicException {
        WaitlistEntry entry = requireEntry(entryId);
        requireCanManage(entry, actor);
        WaitlistStatus status = entry.getStatus();
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.OFFERED) {
            throw new InvalidInputException(
                    "Only WAITING or OFFERED waiting-list entries can be removed (status is " + status + ").");
        }
        LocalDateTime heldSlot = status == WaitlistStatus.OFFERED ? entry.getOfferedStart() : null;
        AppointmentTypeCode heldType = entry.getOfferedType();
        entry.cancel();
        waitlist.save(entry);
        if (heldSlot != null) {
            reoffer(entry.getDoctorId(), heldSlot, heldType);
        }
    }

    /**
     * Accepts an active offer and books the held slot.
     *
     * <p>An expired offer is marked EXPIRED and refused. When booking fails the entry is
     * expired, the slot is offered to the next matching patient and the original error is
     * rethrown.</p>
     *
     * @param entryId waiting-list entry id
     * @param actor   logged-in patient
     * @return the booked appointment
     * @throws WaitlistEntryNotFoundException when the entry does not exist
     * @throws UnauthorizedActionException    when the actor is not the owner
     * @throws InvalidInputException          when there is no active or unexpired offer
     * @throws ClinicException                when booking or persistence fails
     */
    public Appointment accept(int entryId, User actor) throws ClinicException {
        WaitlistEntry entry = requireEntry(entryId);
        requirePatientOwner(entry, actor);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new InvalidInputException("This waiting-list entry has no active offer (status is "
                    + entry.getStatus() + ").");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = entry.getOfferExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            entry.markOfferExpired();
            waitlist.save(entry);
            throw new InvalidInputException("The offer has expired.");
        }
        if (bookingGateway == null) {
            throw new InvalidInputException("Waiting-list booking is not available.");
        }
        Appointment appointment;
        try {
            appointment = bookingGateway.bookReservedSlot(entry, actor);
        } catch (ClinicException bookingFailure) {
            expireAndRethrow(entry);
            throw bookingFailure;
        }
        entry.markAccepted();
        waitlist.save(entry);
        return appointment;
    }

    /**
     * Called when accepting the offer could not be booked: the offer is expired and the slot
     * is passed on to the next matching patient before the original error is rethrown.
     */
    private void expireAndRethrow(WaitlistEntry entry) {
        LocalDateTime heldSlot = entry.getOfferedStart();
        AppointmentTypeCode heldType = entry.getOfferedType();
        int doctorId = entry.getDoctorId();
        entry.markOfferExpired();
        try {
            waitlist.save(entry);
            if (heldSlot != null) {
                reoffer(doctorId, heldSlot, heldType);
            }
        } catch (ClinicException cleanupFailure) {
            System.err.println("[waitlist] could not re-offer the slot after a failed booking: "
                    + cleanupFailure.getMessage());
        }
    }

    /**
     * Declines an active offer; the next matching patient is offered the slot.
     *
     * @param entryId waiting-list entry id
     * @param actor   logged-in patient
     * @throws WaitlistEntryNotFoundException when the entry does not exist
     * @throws UnauthorizedActionException    when the actor is not the owner
     * @throws InvalidInputException          when there is no active offer
     * @throws ClinicException                when the change cannot be persisted
     */
    public void decline(int entryId, User actor) throws ClinicException {
        WaitlistEntry entry = requireEntry(entryId);
        requirePatientOwner(entry, actor);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new InvalidInputException("This waiting-list entry has no active offer (status is "
                    + entry.getStatus() + ").");
        }
        LocalDateTime heldSlot = entry.getOfferedStart();
        AppointmentTypeCode heldType = entry.getOfferedType();
        entry.markDeclined();
        waitlist.save(entry);
        if (heldSlot != null) {
            reoffer(entry.getDoctorId(), heldSlot, heldType);
        }
    }

    /**
     * Expires overdue offers and re-offers their slot to the next matching patient.
     *
     * @return number of offers expired by this call
     * @throws ClinicException when the changes cannot be persisted
     */
    public int expireOffers() throws ClinicException {
        LocalDateTime now = LocalDateTime.now(clock);
        List<WaitlistEntry> due = waitlist.findAll().stream()
                .filter(entry -> entry.getStatus() == WaitlistStatus.OFFERED)
                .filter(entry -> entry.getOfferExpiresAt() != null
                        && !entry.getOfferExpiresAt().isAfter(now))
                .sorted(FIFO)
                .toList();
        int expired = 0;
        for (WaitlistEntry entry : due) {
            entry.markOfferExpired();
            waitlist.save(entry);
            String message = "Your waiting-list offer for " + slotText(entry.getOfferedStart())
                    + " with " + doctorName(entry.getDoctorId()) + " has expired.";
            events.publish(ClinicEvent.of(EventType.WAITLIST_OFFER_EXPIRED, now, message,
                    entry.getPatientId()));
            expired++;
            if (entry.getOfferedStart() != null) {
                reoffer(entry.getDoctorId(), entry.getOfferedStart(), entry.getOfferedType());
            }
        }
        return expired;
    }

    /**
     * Waiting-list entries of one patient, newest first.
     *
     * @param patientId patient id
     * @return the patient's entries
     * @throws ClinicException when the entries cannot be read
     */
    public List<WaitlistEntry> listForPatient(int patientId) throws ClinicException {
        return waitlist.findByPatient(patientId).stream()
                .sorted(FIFO.reversed())
                .toList();
    }

    /**
     * Active offer holding this exact slot, if any. Used by the booking validation.
     *
     * @param doctorId  doctor id
     * @param slotStart slot start
     * @return the active offer, or empty
     */
    public Optional<WaitlistEntry> findActiveOfferFor(int doctorId, LocalDateTime slotStart) {
        if (slotStart == null) {
            return Optional.empty();
        }
        return waitlist.findAll().stream()
                .filter(entry -> entry.getStatus() == WaitlistStatus.OFFERED)
                .filter(entry -> entry.getDoctorId() == doctorId)
                .filter(entry -> slotStart.equals(entry.getOfferedStart()))
                .findFirst();
    }

    /**
     * Offers a freshly freed slot to the first matching waiting-list entry.
     *
     * <p>The offer expires after two hours or one hour before the slot starts, whichever is
     * sooner. Entries whose offer window has already passed are skipped. An already booked
     * slot is never offered again.</p>
     *
     * @param doctorId  doctor whose slot was freed
     * @param slotStart start of the freed slot
     * @param type      appointment type of the freed slot
     * @throws ClinicException when the offer cannot be persisted
     */
    public void onSlotFreed(int doctorId, LocalDateTime slotStart, AppointmentTypeCode type)
            throws ClinicException {
        if (slotStart == null || slotBlocked(doctorId, slotStart, type)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<WaitlistEntry> candidates = waitlist.findAll().stream()
                .filter(entry -> entry.getDoctorId() == doctorId)
                .filter(entry -> entry.matchesDateAndWindow(slotStart))
                .sorted(FIFO)
                .toList();
        for (WaitlistEntry entry : candidates) {
            if (slotBlocked(doctorId, slotStart, type)) {
                return;
            }
            LocalDateTime holdUntil = now.plusHours(OFFER_HOLD_HOURS);
            LocalDateTime cutoff = slotStart.minusHours(OFFER_CUTOFF_HOURS);
            LocalDateTime expiresAt = holdUntil.isBefore(cutoff) ? holdUntil : cutoff;
            if (!expiresAt.isAfter(now)) {
                continue;
            }
            entry.offer(slotStart, type, expiresAt, now);
            waitlist.save(entry);
            String message = "A slot on " + slotText(slotStart) + " with " + doctorName(doctorId)
                    + " is held for you until " + slotText(expiresAt) + " (waiting-list entry #"
                    + entry.getId() + "). Accept it with 'waitlist accept " + entry.getId() + "'.";
            events.publish(ClinicEvent.of(EventType.WAITLIST_SLOT_OFFERED, now, message,
                    entry.getPatientId()));
            return;
        }
    }

    // ---------------------------------------------------------------- helpers

    private WaitlistEntry requireEntry(int entryId) throws WaitlistEntryNotFoundException {
        return waitlist.findById(entryId).orElseThrow(
                () -> new WaitlistEntryNotFoundException("No waiting-list entry with id " + entryId + "."));
    }

    private static void requireCanManage(WaitlistEntry entry, User actor)
            throws UnauthorizedActionException {
        if (actor == null) {
            throw new UnauthorizedActionException("You must be logged in to manage the waiting list.");
        }
        if (actor.getRole() == Role.DOCTOR) {
            throw new UnauthorizedActionException("This command is not available for the DOCTOR role.");
        }
        if (actor.getRole() == Role.PATIENT && actor.getId() != entry.getPatientId()) {
            throw new UnauthorizedActionException(
                    "Patients can only manage their own waiting-list entries.");
        }
    }

    private static void requirePatientOwner(WaitlistEntry entry, User actor)
            throws UnauthorizedActionException {
        if (actor == null || actor.getRole() != Role.PATIENT) {
            throw new UnauthorizedActionException("Only the patient can manage this waiting-list offer.");
        }
        if (actor.getId() != entry.getPatientId()) {
            throw new UnauthorizedActionException(
                    "Patients can only manage their own waiting-list entries.");
        }
    }

    /** Passes a slot that is no longer held on to the next matching entry. */
    private void reoffer(int doctorId, LocalDateTime slotStart, AppointmentTypeCode type)
            throws ClinicException {
        onSlotFreed(doctorId, slotStart, type);
    }

    private boolean slotBlocked(int doctorId, LocalDateTime slotStart, AppointmentTypeCode type) {
        LocalDateTime slotEnd = slotEnd(slotStart, type);
        for (Appointment appointment : appointments.findActiveByDoctor(doctorId)) {
            if (appointment == null || appointment.getStart() == null || appointment.getEnd() == null) {
                continue;
            }
            if (appointment.overlaps(slotStart, slotEnd)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDateTime slotEnd(LocalDateTime slotStart, AppointmentTypeCode type) {
        if (type != null) {
            AppointmentType appointmentType = AppointmentTypeFactory.forCode(type);
            if (appointmentType != null && appointmentType.getDuration() != null) {
                return slotStart.plus(appointmentType.getDuration());
            }
        }
        return slotStart.plusMinutes(ScheduleRules.SLOT_MINUTES);
    }

    private String doctorName(int doctorId) {
        Optional<Doctor> doctor = users.findDoctor(doctorId);
        return doctor.map(Doctor::getDisplayName).orElse("doctor #" + doctorId);
    }

    private static String slotText(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
