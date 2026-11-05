package com.clinic.service;

import com.clinic.event.ClinicEvent;
import com.clinic.event.EventBus;
import com.clinic.event.EventType;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.model.VisitRecord;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import com.clinic.util.Formats;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Visit notes. Records are append-only: adding a note to an appointment that already has one
 * creates an amendment with a timestamp and the doctor's name.
 */
public class VisitService {

    private final VisitRecordRepository visits;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final EventBus events;
    private final Clock clock;

    public VisitService(VisitRecordRepository visits, AppointmentRepository appointments, UserRepository users,
                        EventBus events, Clock clock) {
        this.visits = visits;
        this.appointments = appointments;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Adds notes (and optionally a diagnosis and prescriptions) to a CHECKED_IN or COMPLETED
     * appointment. Only the doctor who owns the appointment may write notes.
     *
     * <p>A correction to an existing record is stored as a new amendment that points at the
     * latest record; old records are never modified.</p>
     *
     * @param appointmentId appointment the notes belong to
     * @param actor         logged-in doctor
     * @param notes         free-text clinical notes, required
     * @param diagnosis     optional diagnosis
     * @param prescriptions optional list of prescribed medicines; {@code null} becomes an empty list
     * @return the persisted visit record
     * @throws AppointmentNotFoundException when the appointment does not exist
     * @throws UnauthorizedActionException  when the actor is not the owning doctor
     * @throws InvalidInputException        when the notes are blank or the status is wrong
     * @throws ClinicException              when the record cannot be persisted
     */
    public VisitRecord addNote(int appointmentId, User actor, String notes, String diagnosis,
                               List<String> prescriptions) throws ClinicException {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(
                        "No appointment with id " + appointmentId + "."));
        if (actor == null || actor.getRole() != Role.DOCTOR) {
            throw new UnauthorizedActionException("Only a doctor can record visit notes.");
        }
        if (appointment.getDoctorId() != actor.getId()) {
            throw new UnauthorizedActionException(
                    "Only the doctor who owns appointment #" + appointmentId + " can add notes.");
        }
        if (notes == null || notes.isBlank()) {
            throw new InvalidInputException("Visit notes are required.");
        }
        AppointmentStatus status = appointment.getStatus();
        if (status != AppointmentStatus.CHECKED_IN && status != AppointmentStatus.COMPLETED) {
            throw new InvalidInputException(
                    "Visit notes can only be added to a CHECKED_IN or COMPLETED appointment (status is "
                            + status + ").");
        }
        Integer amendmentOf = visits.findLatestForAppointment(appointmentId)
                .map(VisitRecord::getId)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        VisitRecord record = new VisitRecord(
                visits.nextId(),
                appointmentId,
                appointment.getPatientId(),
                appointment.getDoctorId(),
                "Dr. " + actor.getName(),
                notes,
                diagnosis,
                prescriptions == null ? List.of() : prescriptions,
                now,
                amendmentOf);
        visits.save(record);
        String message = (record.isAmendment() ? "Amendment to visit notes" : "Visit notes")
                + " recorded for appointment #" + appointmentId + " (Dr. " + actor.getName() + ", "
                + Formats.displayDateTime(now) + ").";
        events.publish(ClinicEvent.of(EventType.VISIT_RECORDED, now, message,
                appointment.getPatientId(), appointment.getDoctorId()));
        return record;
    }

    /**
     * Clinical history of a patient.
     *
     * <p>Patients may view their own history, doctors only the history of patients they have
     * seen, and receptionists are refused because they must not see clinical notes.</p>
     *
     * @param patientId patient whose records are requested
     * @param requester logged-in user
     * @return records ordered by recorded time (oldest first)
     * @throws UnauthorizedActionException when the requester may not read this history
     * @throws ClinicException             when the records cannot be read
     */
    public List<VisitRecord> historyFor(int patientId, User requester) throws ClinicException {
        if (requester == null) {
            throw new UnauthorizedActionException("You must be logged in to view clinical history.");
        }
        switch (requester.getRole()) {
            case PATIENT -> {
                if (requester.getId() != patientId) {
                    throw new UnauthorizedActionException(
                            "Patients can only view their own clinical history.");
                }
            }
            case DOCTOR -> {
                boolean hasSeenPatient = visits.findByDoctor(requester.getId()).stream()
                        .anyMatch(record -> record.getPatientId() == patientId);
                if (!hasSeenPatient) {
                    throw new UnauthorizedActionException(
                            "Doctors can only view the history of patients they have seen.");
                }
            }
            default -> throw new UnauthorizedActionException(
                    "Receptionists can see appointment data but not clinical notes.");
        }
        return visits.findByPatient(patientId).stream()
                .sorted(Comparator.comparing(VisitRecord::getRecordedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * All records of one appointment, oldest first.
     *
     * @param appointmentId appointment id
     * @return records of the appointment
     */
    public List<VisitRecord> recordsForAppointment(int appointmentId) {
        return visits.findByAppointment(appointmentId).stream()
                .sorted(Comparator.comparing(VisitRecord::getRecordedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Latest (newest) record of one appointment, if any.
     *
     * @param appointmentId appointment id
     * @return latest record or empty
     */
    public Optional<VisitRecord> latestRecord(int appointmentId) {
        return visits.findLatestForAppointment(appointmentId);
    }
}
