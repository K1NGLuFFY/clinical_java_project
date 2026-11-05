package com.clinic.service;

import com.clinic.event.ClinicEvent;
import com.clinic.event.EventBus;
import com.clinic.event.EventType;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.BookingLimitExceededException;
import com.clinic.exception.CancellationNotAllowedException;
import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.InvalidStatusTransitionException;
import com.clinic.exception.PatientBlockedException;
import com.clinic.exception.PatientNotFoundException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.BookingContext;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.model.WaitlistEntry;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.WaitlistRepository;
import com.clinic.scheduling.ScheduleRules;
import com.clinic.scheduling.SlotFinder;
import com.clinic.scheduling.TimeRange;
import com.clinic.util.Formats;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Booking, cancellation, rescheduling and the no-show block.
 *
 * <p>All write operations run under a single {@link java.util.concurrent.locks.ReentrantLock}
 * so two concurrent requests for the same slot cannot both succeed. The old slot of a
 * reschedule and every cancelled slot are offered to the waiting list.</p>
 */
public class BookingService implements BookingGateway {

    /** Maximum number of active upcoming appointments one patient may hold. */
    public static final int MAX_UPCOMING_APPOINTMENTS = 3;

    /** A no-show can only be recorded this many minutes after the start. */
    public static final int NO_SHOW_GRACE_MINUTES = 15;

    /** No-show history window used for the automatic block. */
    public static final int NO_SHOW_BLOCK_DAYS = 90;

    /** Number of no-shows inside the window that blocks the patient. */
    public static final int NO_SHOW_BLOCK_THRESHOLD = 3;

    /** Suggestions shown when a slot cannot be used. */
    private static final int SUGGESTION_LIMIT = 3;

    private static final Comparator<Appointment> BY_START =
            Comparator.comparing(Appointment::getStart, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;
    private final SlotFinder slotFinder;
    private final BillingService billing;
    private final EventBus events;
    private final Clock clock;

    /** Serialises every booking mutation so a slot cannot be double-booked. */
    private final ReentrantLock bookingLock = new ReentrantLock();

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

    /**
     * Wires the waiting-list service after construction (the two services are mutually
     * dependent; {@code Main} breaks the cycle with this setter).
     *
     * @param waitlistService waiting-list service used to pass freed slots on
     */
    public void setWaitlistService(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    /**
     * Read-only lookup of one appointment.
     *
     * @param appointmentId appointment id
     * @return the appointment, or empty when unknown
     */
    public Optional<Appointment> findById(int appointmentId) {
        return appointments.findById(appointmentId);
    }

    /**
     * All appointments of a patient, ordered by start time (patients only see their own).
     *
     * @param patientId patient id
     * @param actor     logged-in user
     * @return appointments ordered by start time
     * @throws UnauthorizedActionException when the actor may not see this patient's appointments
     */
    public List<Appointment> appointmentsForPatient(int patientId, User actor) throws ClinicException {
        if (actor == null) {
            throw new UnauthorizedActionException("You must be logged in to view appointments.");
        }
        switch (actor.getRole()) {
            case RECEPTIONIST -> {
                // receptionists may view every patient
            }
            case PATIENT -> {
                if (actor.getId() != patientId) {
                    throw new UnauthorizedActionException("Patients can only view their own appointments.");
                }
            }
            default -> throw new UnauthorizedActionException(
                    "This command is not available for the " + actor.getRole() + " role.");
        }
        return appointments.findByPatient(patientId).stream()
                .sorted(BY_START)
                .toList();
    }

    /**
     * Books an appointment after validating every rule of the brief. Patients may only book
     * for themselves; receptionists may book for any patient.
     *
     * @param patientId patient the appointment is for
     * @param doctorId  requested doctor
     * @param start     requested start time (15 minute boundary)
     * @param type      appointment type
     * @param actor     logged-in user
     * @return the stored appointment
     * @throws UnauthorizedActionException when the actor may not book for this patient
     * @throws PatientNotFoundException    when no patient has this id
     * @throws PatientBlockedException     when the patient is blocked
     * @throws DoctorNotFoundException     when no doctor has this id
     * @throws InvalidInputException       when the slot or type is invalid
     * @throws SlotUnavailableException    when the slot is taken, held or the doctor is full
     * @throws BookingLimitExceededException when the patient already holds 3 upcoming appointments
     * @throws ClinicException             when the appointment cannot be persisted
     */
    public Appointment book(int patientId, int doctorId, LocalDateTime start,
                            AppointmentTypeCode type, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            requireCanBookFor(patientId, actor);
            Patient patient = requirePatient(patientId);
            requireNotBlocked(patient);
            Doctor doctor = requireDoctor(doctorId);
            requireSlotBoundary(start);
            AppointmentType appointmentType = requireType(type);
            validateSlot(patient, doctor, appointmentType, start, now, null, true);
            requireBookingLimit(patient, now);

            PricingQuote quote = billing.quote(patient, appointmentType.getCode(), LocalDate.now(clock));
            LocalDateTime end = start.plus(appointmentType.getDuration());
            Appointment appointment = new Appointment(appointments.nextId(), patientId, doctorId,
                    appointmentType.getCode(), start, end, now);
            appointment.applyCharges(quote.baseFee(), quote.patientShare(), quote.insurerShare());
            appointments.save(appointment);
            publishBooked(appointment, doctor, appointmentType, now, false);
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Cancels an appointment, applies the late fee when inside 24 hours, frees the slot.
     *
     * @param appointmentId appointment id
     * @param reason        human readable cancellation reason
     * @param actor         logged-in user
     * @return the cancelled appointment
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor may not cancel it
     * @throws CancellationNotAllowedException when the appointment already started
     * @throws InvalidStatusTransitionException when the status is final
     * @throws ClinicException              when the change cannot be persisted
     */
    public Appointment cancel(int appointmentId, String reason, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            Appointment appointment = requireAppointment(appointmentId);
            requireCanManage(appointment, actor);
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime start = appointment.getStart();
            if (start == null || !now.isBefore(start)) {
                throw new CancellationNotAllowedException("Appointment #" + appointmentId
                        + " can no longer be cancelled because it has already started.");
            }
            AppointmentType type = requireType(appointment.getType());
            appointment.transitionTo(AppointmentStatus.CANCELLED, now);
            BigDecimal lateFee = BigDecimal.ZERO;
            if (now.isAfter(start.minusHours(24))) {
                lateFee = billing.lateCancellationFee(type.getCode());
                appointment.setLateCancellationFee(lateFee);
            }
            String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
            appointment.setCancellationReason(cleanReason);
            appointments.save(appointment);

            String message = "Appointment #" + appointmentId + " with Dr. " + doctorName(appointment.getDoctorId())
                    + " on " + slotText(start) + " was cancelled"
                    + (cleanReason == null ? "" : " (" + cleanReason + ")")
                    + (lateFee.signum() > 0
                            ? ". A late cancellation fee of " + Formats.money(lateFee) + " applies."
                            : ".");
            events.publish(ClinicEvent.of(EventType.APPOINTMENT_CANCELLED, now, message,
                    appointment.getPatientId(), appointment.getDoctorId()));

            if (waitlistService != null) {
                waitlistService.onSlotFreed(appointment.getDoctorId(), start, type.getCode());
            }
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Atomic reschedule: the new slot is validated before the old booking is replaced.
     *
     * @param appointmentId appointment id
     * @param newStart      new start time (15 minute boundary)
     * @param actor         logged-in user
     * @return the rescheduled appointment
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor may not reschedule it
     * @throws InvalidInputException        when the appointment is not BOOKED or the new slot is invalid
     * @throws CancellationNotAllowedException when the appointment already started
     * @throws SlotUnavailableException     when the new slot is not free
     * @throws ClinicException              when the change cannot be persisted
     */
    public Appointment reschedule(int appointmentId, LocalDateTime newStart, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            Appointment appointment = requireAppointment(appointmentId);
            requireCanManage(appointment, actor);
            if (appointment.getStatus() != AppointmentStatus.BOOKED) {
                throw new InvalidInputException("Only BOOKED appointments can be rescheduled (status is "
                        + appointment.getStatus() + ").");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime oldStart = appointment.getStart();
            if (oldStart == null || !now.isBefore(oldStart)) {
                throw new CancellationNotAllowedException("Appointment #" + appointmentId
                        + " can no longer be rescheduled because it has already started.");
            }
            if (newStart == null) {
                throw new InvalidInputException("A new start time is required.");
            }
            if (newStart.equals(oldStart)) {
                throw new InvalidInputException(
                        "The new start time must differ from the current start time.");
            }
            requireSlotBoundary(newStart);
            Patient patient = requirePatient(appointment.getPatientId());
            requireNotBlocked(patient);
            Doctor doctor = requireDoctor(appointment.getDoctorId());
            AppointmentType type = requireType(appointment.getType());
            validateSlot(patient, doctor, type, newStart, now, appointment.getId(), true);

            AppointmentTypeCode oldType = appointment.getType();
            appointment.setStart(newStart);
            appointment.setEnd(newStart.plus(type.getDuration()));
            appointment.setUpdatedAt(now);
            appointments.save(appointment);

            String message = "Appointment #" + appointmentId + " with Dr. " + doctorName(appointment.getDoctorId())
                    + " was rescheduled from " + slotText(oldStart) + " to " + slotText(newStart) + ".";
            events.publish(ClinicEvent.of(EventType.APPOINTMENT_RESCHEDULED, now, message,
                    appointment.getPatientId(), appointment.getDoctorId()));

            if (waitlistService != null) {
                waitlistService.onSlotFreed(doctor.getId(), oldStart, oldType);
            }
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Receptionist checks the patient in on the day of the appointment.
     *
     * @param appointmentId appointment id
     * @param actor         logged-in user
     * @return the checked-in appointment
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor is not a receptionist
     * @throws InvalidInputException        when today is not the appointment day
     * @throws InvalidStatusTransitionException when the appointment is not BOOKED
     * @throws ClinicException              when the change cannot be persisted
     */
    public Appointment checkIn(int appointmentId, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            if (actor == null || actor.getRole() != Role.RECEPTIONIST) {
                throw new UnauthorizedActionException("Only a receptionist can check in a patient.");
            }
            Appointment appointment = requireAppointment(appointmentId);
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime start = appointment.getStart();
            if (start == null || !start.toLocalDate().equals(now.toLocalDate())) {
                throw new InvalidInputException("Check-in is only possible on the day of the appointment.");
            }
            appointment.transitionTo(AppointmentStatus.CHECKED_IN, now);
            appointments.save(appointment);
            String message = "Appointment #" + appointmentId + " with Dr. "
                    + doctorName(appointment.getDoctorId()) + " on " + slotText(start)
                    + " was checked in at " + slotText(now) + ".";
            events.publish(ClinicEvent.of(EventType.APPOINTMENT_CHECKED_IN, now, message,
                    appointment.getPatientId(), appointment.getDoctorId()));
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Doctor finishes the visit; the appointment must be CHECKED_IN.
     *
     * @param appointmentId appointment id
     * @param actor         logged-in doctor
     * @return the completed appointment
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor is not the owning doctor
     * @throws InvalidStatusTransitionException when the appointment is not CHECKED_IN
     * @throws ClinicException              when the change cannot be persisted
     */
    public Appointment complete(int appointmentId, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            Appointment appointment = requireAppointment(appointmentId);
            if (actor == null || actor.getRole() != Role.DOCTOR
                    || appointment.getDoctorId() != actor.getId()) {
                throw new UnauthorizedActionException(
                        "Only the doctor who owns appointment #" + appointmentId + " can complete it.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            appointment.transitionTo(AppointmentStatus.COMPLETED, now);
            appointments.save(appointment);
            String message = "Appointment #" + appointmentId + " with Dr. "
                    + doctorName(appointment.getDoctorId()) + " on " + slotText(appointment.getStart())
                    + " was completed.";
            events.publish(ClinicEvent.of(EventType.APPOINTMENT_COMPLETED, now, message,
                    appointment.getPatientId(), appointment.getDoctorId()));
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Receptionist marks a no-show more than 15 minutes after the start time.
     *
     * <p>The no-show is recorded on the patient; three no-shows in 90 days block the account.</p>
     *
     * @param appointmentId appointment id
     * @param actor         logged-in user
     * @return the appointment marked NO_SHOW
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor is not a receptionist
     * @throws InvalidStatusTransitionException when the appointment is not BOOKED
     * @throws InvalidInputException        when less than 15 minutes have passed
     * @throws ClinicException              when the change cannot be persisted
     */
    public Appointment markNoShow(int appointmentId, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            if (actor == null || actor.getRole() != Role.RECEPTIONIST) {
                throw new UnauthorizedActionException("Only a receptionist can mark a no-show.");
            }
            Appointment appointment = requireAppointment(appointmentId);
            if (appointment.getStatus() != AppointmentStatus.BOOKED) {
                throw new InvalidStatusTransitionException("Cannot mark appointment #" + appointmentId
                        + " as a no-show from status " + appointment.getStatus() + ".");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime start = appointment.getStart();
            if (start == null || now.isBefore(start.plusMinutes(NO_SHOW_GRACE_MINUTES))) {
                throw new InvalidInputException("A no-show can only be recorded at least "
                        + NO_SHOW_GRACE_MINUTES + " minutes after the start time.");
            }
            appointment.transitionTo(AppointmentStatus.NO_SHOW, now);
            Patient patient = requirePatient(appointment.getPatientId());
            patient.recordNoShow(now);
            users.save(patient);

            String noShowMessage = "Patient " + patient.getName() + " did not attend appointment #"
                    + appointmentId + " with Dr. " + doctorName(appointment.getDoctorId())
                    + " on " + slotText(start) + ".";
            events.publish(ClinicEvent.of(EventType.NO_SHOW_RECORDED, now, noShowMessage,
                    patient.getId(), appointment.getDoctorId()));

            if (patient.noShowsSince(now.minusDays(NO_SHOW_BLOCK_DAYS)) >= NO_SHOW_BLOCK_THRESHOLD) {
                patient.block(now);
                users.save(patient);
                String blockMessage = "Patient " + patient.getName() + " was blocked after "
                        + NO_SHOW_BLOCK_THRESHOLD + " no-shows in " + NO_SHOW_BLOCK_DAYS
                        + " days. Ask a receptionist to unblock the account.";
                events.publish(ClinicEvent.of(EventType.PATIENT_BLOCKED, now, blockMessage,
                        patient.getId()));
            }
            appointments.save(appointment);
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Confirms a waiting-list offer by booking the held slot (called by WaitlistService).
     *
     * <p>Validation is the normal booking validation except that the waiting-list hold on the
     * very slot being accepted is not treated as a conflict.</p>
     *
     * @param entry waiting-list entry holding the slot
     * @param actor logged-in patient
     * @return the stored appointment
     * @throws InvalidInputException        when the entry has no slot or type
     * @throws PatientNotFoundException     when the patient no longer exists
     * @throws PatientBlockedException      when the patient is blocked
     * @throws DoctorNotFoundException      when the doctor no longer exists
     * @throws SlotUnavailableException     when the slot is no longer free
     * @throws BookingLimitExceededException when the patient holds 3 upcoming appointments
     * @throws ClinicException              when the appointment cannot be persisted
     */
    @Override
    public Appointment bookReservedSlot(WaitlistEntry entry, User actor) throws ClinicException {
        bookingLock.lock();
        try {
            if (entry == null) {
                throw new InvalidInputException("A waiting-list entry is required.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            int patientId = entry.getPatientId();
            requireCanBookFor(patientId, actor);
            Patient patient = requirePatient(patientId);
            requireNotBlocked(patient);
            Doctor doctor = requireDoctor(entry.getDoctorId());
            LocalDateTime start = entry.getOfferedStart();
            if (start == null) {
                throw new InvalidInputException("The waiting-list offer has no slot.");
            }
            requireSlotBoundary(start);
            AppointmentType type = requireType(entry.getOfferedType());
            validateSlot(patient, doctor, type, start, now, null, false);
            requireBookingLimit(patient, now);

            PricingQuote quote = billing.quote(patient, type.getCode(), LocalDate.now(clock));
            Appointment appointment = new Appointment(appointments.nextId(), patientId, doctor.getId(),
                    type.getCode(), start, start.plus(type.getDuration()), now);
            appointment.applyCharges(quote.baseFee(), quote.patientShare(), quote.insurerShare());
            appointments.save(appointment);
            publishBooked(appointment, doctor, type, now, true);
            return appointment;
        } finally {
            bookingLock.unlock();
        }
    }

    // ------------------------------------------------------------- validation

    /**
     * Full booking validation shared by book, reschedule and waiting-list acceptance.
     * The start time must already be non-null and on a slot boundary.
     */
    private void validateSlot(Patient patient, Doctor doctor, AppointmentType type, LocalDateTime start,
                              LocalDateTime now, Integer excludedAppointmentId, boolean checkWaitlistHold)
            throws ClinicException {
        type.checkEligibility(bookingContext(patient, doctor, start, now));
        ScheduleRules.checkBookingWindow(start, type, now);
        TimeRange requested = TimeRange.of(start, type.getDuration());
        ScheduleRules.checkWorkingHours(doctor, start.toLocalDate(), requested, type);

        List<Appointment> doctorAppointments = appointments.findByDoctor(doctor.getId());
        for (Appointment existing : doctorAppointments) {
            if (!isBlocking(existing) || isExcluded(existing, excludedAppointmentId)) {
                continue;
            }
            if (existing.overlaps(start, requested.getEnd())) {
                throw new SlotUnavailableException("Dr. " + doctor.getName() + " is already booked at "
                        + slotText(start) + ".", suggestions(doctor, start, type, doctorAppointments));
            }
        }
        List<Appointment> patientAppointments = appointments.findByPatient(patient.getId());
        for (Appointment existing : patientAppointments) {
            if (!isBlocking(existing) || isExcluded(existing, excludedAppointmentId)) {
                continue;
            }
            if (existing.overlaps(start, requested.getEnd())) {
                throw new SlotUnavailableException("Patient " + patient.getName()
                        + " already has an appointment at " + slotText(existing.getStart()) + ".",
                        suggestions(doctor, start, type, doctorAppointments));
            }
        }
        if (checkWaitlistHold && waitlistService != null) {
            Optional<WaitlistEntry> hold = waitlistService.findActiveOfferFor(doctor.getId(), start);
            if (hold.isPresent() && hold.get().getPatientId() != patient.getId()) {
                throw new SlotUnavailableException(
                        "That slot is currently held for another patient on the waiting list.",
                        suggestions(doctor, start, type, doctorAppointments));
            }
        }
        requireDailyCapacity(doctor, type, start.toLocalDate(), excludedAppointmentId);
    }

    private BookingContext bookingContext(Patient patient, Doctor doctor, LocalDateTime start,
                                          LocalDateTime now) {
        Integer lastDoctorId = null;
        LocalDateTime lastCompletedAt = null;
        for (Appointment appointment : appointments.findByPatient(patient.getId())) {
            if (appointment == null || appointment.getStatus() != AppointmentStatus.COMPLETED
                    || appointment.getDoctorId() != doctor.getId()
                    || appointment.getUpdatedAt() == null) {
                continue;
            }
            if (lastCompletedAt == null || appointment.getUpdatedAt().isAfter(lastCompletedAt)) {
                lastCompletedAt = appointment.getUpdatedAt();
                lastDoctorId = appointment.getDoctorId();
            }
        }
        return new BookingContext(patient.getId(), doctor.getId(), start, now, lastDoctorId, lastCompletedAt);
    }

    private void requireDailyCapacity(Doctor doctor, AppointmentType type, LocalDate date,
                                      Integer excludedAppointmentId) throws SlotUnavailableException {
        if (type.ignoresDailyCapacity()) {
            return;
        }
        int capacity = doctor.getDailyCapacity();
        if (capacity <= 0) {
            return;
        }
        int booked = 0;
        for (Appointment appointment : appointments.findByDoctorAndDate(doctor.getId(), date)) {
            if (appointment == null || isExcluded(appointment, excludedAppointmentId)) {
                continue;
            }
            if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
                continue;
            }
            booked++;
        }
        if (booked >= capacity) {
            throw new SlotUnavailableException("Dr. " + doctor.getName() + " is at daily capacity ("
                    + capacity + ").", List.of());
        }
    }

    private void requireBookingLimit(Patient patient, LocalDateTime now) throws ClinicException {
        long upcoming = appointments.findByPatient(patient.getId()).stream()
                .filter(BookingService::isBlocking)
                .filter(appointment -> !appointment.getStart().isBefore(now))
                .count();
        if (upcoming >= MAX_UPCOMING_APPOINTMENTS) {
            throw new BookingLimitExceededException("Patients can hold at most "
                    + MAX_UPCOMING_APPOINTMENTS + " upcoming appointments. Cancel one before booking another.");
        }
    }

    private List<LocalDateTime> suggestions(Doctor doctor, LocalDateTime start, AppointmentType type,
                                            List<Appointment> doctorAppointments) {
        List<LocalDateTime> slots = slotFinder.suggest(doctor, start, type, doctorAppointments,
                SUGGESTION_LIMIT);
        return slots == null ? List.of() : slots;
    }

    // ------------------------------------------------------------- authorities

    private void requireCanBookFor(int patientId, User actor) throws UnauthorizedActionException {
        if (actor == null) {
            throw new UnauthorizedActionException("You must be logged in to book an appointment.");
        }
        switch (actor.getRole()) {
            case RECEPTIONIST -> {
                // receptionists may book for any patient
            }
            case PATIENT -> {
                if (actor.getId() != patientId) {
                    throw new UnauthorizedActionException("Patients can only book appointments for themselves.");
                }
            }
            default -> throw new UnauthorizedActionException("Doctors cannot book appointments.");
        }
    }

    private void requireCanManage(Appointment appointment, User actor) throws UnauthorizedActionException {
        if (actor == null) {
            throw new UnauthorizedActionException("You must be logged in to manage an appointment.");
        }
        switch (actor.getRole()) {
            case RECEPTIONIST -> {
                // receptionists may cancel and reschedule any appointment
            }
            case PATIENT -> {
                if (appointment.getPatientId() != actor.getId()) {
                    throw new UnauthorizedActionException(
                            "Patients can only manage their own appointments.");
                }
            }
            default -> throw new UnauthorizedActionException(
                    "This command is not available for the " + actor.getRole() + " role.");
        }
    }

    // ------------------------------------------------------------------ lookup

    private Appointment requireAppointment(int appointmentId) throws AppointmentNotFoundException {
        return appointments.findById(appointmentId).orElseThrow(
                () -> new AppointmentNotFoundException("No appointment with id " + appointmentId + "."));
    }

    private Patient requirePatient(int patientId) throws PatientNotFoundException {
        return users.findPatient(patientId).orElseThrow(
                () -> new PatientNotFoundException("No patient with id " + patientId + "."));
    }

    private Doctor requireDoctor(int doctorId) throws DoctorNotFoundException {
        return users.findDoctor(doctorId).orElseThrow(
                () -> new DoctorNotFoundException("No doctor with id " + doctorId + "."));
    }

    private void requireNotBlocked(Patient patient) throws PatientBlockedException {
        if (patient.isBlocked()) {
            throw new PatientBlockedException("Patient " + patient.getName()
                    + " is blocked from booking because of repeated no-shows. "
                    + "Ask a receptionist to unblock the account.");
        }
    }

    private static AppointmentType requireType(AppointmentTypeCode code) throws InvalidInputException {
        if (code == null) {
            throw new InvalidInputException("An appointment type is required.");
        }
        AppointmentType type = AppointmentTypeFactory.forCode(code);
        if (type == null) {
            throw new InvalidInputException("Unknown appointment type '" + code + "'.");
        }
        return type;
    }

    private static void requireSlotBoundary(LocalDateTime start) throws InvalidInputException {
        if (start == null) {
            throw new InvalidInputException("An appointment start time is required.");
        }
        if (!ScheduleRules.isSlotBoundary(start)) {
            throw new InvalidInputException(
                    "Appointments must start on a 15 minute boundary (for example 10:00 or 10:15).");
        }
    }

    private static boolean isBlocking(Appointment appointment) {
        return appointment != null
                && appointment.getStatus() != null
                && appointment.getStatus().isActive()
                && appointment.getStart() != null
                && appointment.getEnd() != null;
    }

    private static boolean isExcluded(Appointment appointment, Integer excludedAppointmentId) {
        return excludedAppointmentId != null && appointment.getId() == excludedAppointmentId;
    }

    // ------------------------------------------------------------------ events

    private void publishBooked(Appointment appointment, Doctor doctor, AppointmentType type,
                               LocalDateTime now, boolean fromWaitlist) {
        String message = "Appointment #" + appointment.getId() + " booked with Dr. " + doctor.getName()
                + " for " + slotText(appointment.getStart()) + " (" + type.getDisplayName() + ")"
                + (fromWaitlist ? " from the waiting list." : ".");
        events.publish(ClinicEvent.of(EventType.APPOINTMENT_BOOKED, now, message,
                appointment.getPatientId(), appointment.getDoctorId()));
    }

    private String doctorName(int doctorId) {
        return users.findDoctor(doctorId).map(Doctor::getName).orElse("doctor #" + doctorId);
    }

    /** ISO start time without trailing zero seconds, e.g. {@code 2026-11-02T10:00}. */
    private static String slotText(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
