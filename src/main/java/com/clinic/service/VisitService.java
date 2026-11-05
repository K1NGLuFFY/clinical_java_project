package com.clinic.service;

import com.clinic.event.EventBus;
import com.clinic.exception.ClinicException;
import com.clinic.model.User;
import com.clinic.model.VisitRecord;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import java.time.Clock;
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
     */
    public VisitRecord addNote(int appointmentId, User actor, String notes, String diagnosis,
                               List<String> prescriptions) throws ClinicException {
        throw new UnsupportedOperationException("VisitService.addNote not implemented yet");
    }

    /**
     * Clinical history of a patient.
     *
     * <p>Patients may view their own history, doctors only the history of patients they have
     * seen, and receptionists are refused because they must not see clinical notes.</p>
     */
    public List<VisitRecord> historyFor(int patientId, User requester) throws ClinicException {
        throw new UnsupportedOperationException("VisitService.historyFor not implemented yet");
    }

    public List<VisitRecord> recordsForAppointment(int appointmentId) {
        throw new UnsupportedOperationException("VisitService.recordsForAppointment not implemented yet");
    }

    public Optional<VisitRecord> latestRecord(int appointmentId) {
        throw new UnsupportedOperationException("VisitService.latestRecord not implemented yet");
    }
}
