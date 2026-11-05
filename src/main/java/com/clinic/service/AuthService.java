package com.clinic.service;

import com.clinic.exception.AccountLockedException;
import com.clinic.exception.AuthenticationException;
import com.clinic.exception.ClinicException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.Receptionist;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Authentication, lockout and role authorisation.
 *
 * <p>Rules: three consecutive failed logins lock the account, only a receptionist can unlock
 * it, and every command checks the role of the logged-in user.</p>
 */
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final UserRepository users;
    private final Clock clock;

    public AuthService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * Verifies the credentials and returns the logged-in user.
     *
     * @throws AccountLockedException when the account is locked
     * @throws AuthenticationException when the account does not exist or the password is wrong
     */
    public User login(int userId, String password) throws ClinicException {
        throw new UnsupportedOperationException("AuthService.login not implemented yet");
    }

    /** Creates the first receptionist account (called by the seeder on a fresh data folder). */
    public Receptionist createReceptionist(String name, String password) throws ClinicException {
        throw new UnsupportedOperationException("AuthService.createReceptionist not implemented yet");
    }

    /** Lifts a lock; only a receptionist is allowed to do this. */
    public void unlock(int userId, User actor) throws ClinicException {
        throw new UnsupportedOperationException("AuthService.unlock not implemented yet");
    }

    /** Receptionist registers a doctor account; the generated password is returned once. */
    public CreatedAccount createDoctor(String name, String specialty, User actor) throws ClinicException {
        throw new UnsupportedOperationException("AuthService.createDoctor not implemented yet");
    }

    /** Receptionist registers a patient account; the generated password is returned once. */
    public CreatedAccount createPatient(String name, LocalDate dateOfBirth, PatientCategory category,
                                        String insurer, User actor) throws ClinicException {
        throw new UnsupportedOperationException("AuthService.createPatient not implemented yet");
    }

    /** Throws {@link UnauthorizedActionException} unless the user has one of the allowed roles. */
    public void requireRole(User user, Role... allowed) throws UnauthorizedActionException {
        throw new UnsupportedOperationException("AuthService.requireRole not implemented yet");
    }

    /** Convenience guard that also casts the user. */
    public Doctor requireDoctor(User user) throws UnauthorizedActionException {
        throw new UnsupportedOperationException("AuthService.requireDoctor not implemented yet");
    }

    /** Convenience guard that also casts the user. */
    public Patient requirePatient(User user) throws UnauthorizedActionException {
        throw new UnsupportedOperationException("AuthService.requirePatient not implemented yet");
    }

    /** Creates the default receptionist on a brand-new data folder if no receptionist exists. */
    public void ensureDefaultReceptionist() throws ClinicException {
        throw new UnsupportedOperationException("AuthService.ensureDefaultReceptionist not implemented yet");
    }
}
