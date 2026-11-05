package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.Receptionist;
import com.clinic.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for accounts. Patients and receptionists are stored in
 * {@code data/users.json}; doctors (with their schedule and leave) in {@code data/doctors.json}.
 */
public interface UserRepository {

    Optional<User> findById(int id);

    Optional<Doctor> findDoctor(int id);

    Optional<Patient> findPatient(int id);

    List<User> findAll();

    List<Doctor> findAllDoctors();

    List<Patient> findAllPatients();

    List<Receptionist> findAllReceptionists();

    /** Inserts or updates the account and persists the change immediately. */
    void save(User user) throws StorageException;

    /** Persists all in-memory accounts (used after bulk field updates such as unlock). */
    void saveAll() throws StorageException;

    /** Next free user id; continues from the last saved value after a restart. */
    int nextId() throws StorageException;
}