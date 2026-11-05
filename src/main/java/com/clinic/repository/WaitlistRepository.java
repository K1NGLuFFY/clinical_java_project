package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.WaitlistEntry;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for the waiting list. */
public interface WaitlistRepository {

    Optional<WaitlistEntry> findById(int id);

    List<WaitlistEntry> findAll();

    List<WaitlistEntry> findOpenForDoctor(int doctorId);

    List<WaitlistEntry> findByPatient(int patientId);

    /** Inserts or updates the entry and persists the change immediately. */
    void save(WaitlistEntry entry) throws StorageException;

    int nextId() throws StorageException;
}