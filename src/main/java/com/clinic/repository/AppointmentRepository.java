package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.Appointment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for appointments. */
public interface AppointmentRepository {

    Optional<Appointment> findById(int id);

    List<Appointment> findAll();

    List<Appointment> findByDoctor(int doctorId);

    List<Appointment> findByDoctorAndDate(int doctorId, LocalDate date);

    List<Appointment> findByPatient(int patientId);

    List<Appointment> findActiveByDoctor(int doctorId);

    List<Appointment> findBetween(LocalDate from, LocalDate to);

    /** Inserts or updates the appointment and persists the change immediately. */
    void save(Appointment appointment) throws StorageException;

    int nextId() throws StorageException;
}