package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.VisitRecord;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for append-only visit records. */
public interface VisitRecordRepository {

    Optional<VisitRecord> findById(int id);

    List<VisitRecord> findByAppointment(int appointmentId);

    List<VisitRecord> findByPatient(int patientId);

    List<VisitRecord> findByDoctor(int doctorId);

    Optional<VisitRecord> findLatestForAppointment(int appointmentId);

    List<VisitRecord> findAll();

    /** Inserts the record and persists the change immediately. */
    void save(VisitRecord record) throws StorageException;

    int nextId() throws StorageException;
}