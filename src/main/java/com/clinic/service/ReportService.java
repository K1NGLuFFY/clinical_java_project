package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Receptionist reports: daily schedule, utilisation, no-show rate, revenue and blocked patients.
 */
public class ReportService {

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final VisitRecordRepository visits;
    private final Clock clock;

    public ReportService(UserRepository users, AppointmentRepository appointments,
                         VisitRecordRepository visits, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.visits = visits;
        this.clock = clock;
    }

    public DailyScheduleReport daily(int doctorId, LocalDate date) throws DoctorNotFoundException {
        throw new UnsupportedOperationException("ReportService.daily not implemented yet");
    }

    public List<UtilisationReport> utilisation(LocalDate from, LocalDate to) throws ClinicException {
        throw new UnsupportedOperationException("ReportService.utilisation not implemented yet");
    }

    public NoShowReport noShow(LocalDate from, LocalDate to) throws ClinicException {
        throw new UnsupportedOperationException("ReportService.noShow not implemented yet");
    }

    public RevenueReport revenue(LocalDate from, LocalDate to) throws ClinicException {
        throw new UnsupportedOperationException("ReportService.revenue not implemented yet");
    }

    public List<Patient> blockedPatients() {
        throw new UnsupportedOperationException("ReportService.blockedPatients not implemented yet");
    }
}
