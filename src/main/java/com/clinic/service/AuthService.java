package com.clinic.service;

import com.clinic.exception.AccountLockedException;
import com.clinic.exception.AuthenticationException;
import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.factory.UserFactory;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.Receptionist;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.repository.UserRepository;
import com.clinic.util.PasswordHasher;
import com.clinic.util.Validators;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Authentication, lockout and role authorisation.
 *
 * <p>Rules: three consecutive failed logins lock the account, only a receptionist can unlock
 * it, and every command checks the role of the logged-in user.</p>
 */
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 3;

    /** Capacity of a newly registered doctor, as required by the brief. */
    public static final int DEFAULT_DAILY_CAPACITY = 20;

    /** Name of the account seeded on a brand-new data folder. */
    public static final String DEFAULT_RECEPTIONIST_NAME = "Front Desk";
    /** Password of the account seeded on a brand-new data folder (documented in the README). */
    public static final String DEFAULT_RECEPTIONIST_PASSWORD = "admin123";

    private final UserRepository users;
    private final Clock clock;

    public AuthService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /**
     * Verifies the credentials and returns the logged-in user.
     *
     * <p>A wrong password increments the consecutive failure counter and is persisted; the
     * third consecutive failure locks the account. A successful login clears the counter.</p>
     *
     * @param userId   id of the account
     * @param password raw password entered by the user
     * @return the authenticated user
     * @throws AccountLockedException  when the account is locked
     * @throws AuthenticationException when the account does not exist or the password is wrong
     * @throws ClinicException         when the lockout state cannot be persisted
     */
    public User login(int userId, String password) throws ClinicException {
        User user = users.findById(userId).orElseThrow(
                () -> new AuthenticationException("Unknown user id " + userId + "."));
        if (user.isLocked()) {
            throw new AccountLockedException(
                    "Account " + userId + " is locked. Ask a receptionist to unlock it.");
        }
        boolean passwordMatches = user.hasPassword()
                && PasswordHasher.matches(password, user.getPasswordHash(), user.getPasswordSalt());
        if (!passwordMatches) {
            user.recordFailedLogin(MAX_FAILED_ATTEMPTS);
            users.save(user);
            if (user.isLocked()) {
                throw new AuthenticationException(
                        "Account locked after 3 failed attempts. Ask a receptionist to unlock it.");
            }
            throw new AuthenticationException("Incorrect password for user id " + userId + ".");
        }
        user.resetFailedLogins();
        users.save(user);
        return user;
    }

    /**
     * Creates the first receptionist account (called by the seeder on a fresh data folder).
     *
     * @param name     receptionist display name
     * @param password initial password, stored only as a salted hash
     * @return the created receptionist
     * @throws InvalidInputException when the name or password is blank
     * @throws ClinicException       when the account cannot be persisted
     */
    public Receptionist createReceptionist(String name, String password) throws ClinicException {
        String cleanName = Validators.requireNonBlank("receptionist name", name);
        String cleanPassword = Validators.requireNonBlank("password", password);
        Receptionist receptionist = UserFactory.newReceptionist(users.nextId(), cleanName,
                cleanPassword, LocalDateTime.now(clock));
        users.save(receptionist);
        return receptionist;
    }

    /**
     * Lifts a login lock; only a receptionist is allowed to do this.
     *
     * <p>For a patient this also clears the no-show booking block set by
     * {@link BookingService#markNoShow(int, User)} after three no-shows in 90 days, so a
     * receptionist can restore a patient's ability to book. The no-show history itself is
     * never cleared.</p>
     *
     * @param userId id of the account to unlock
     * @param actor  logged-in user
     * @throws UnauthorizedActionException when the actor is not a receptionist
     * @throws InvalidInputException       when no account has this id
     * @throws ClinicException             when the change cannot be persisted
     */
    public void unlock(int userId, User actor) throws ClinicException {
        requireRole(actor, Role.RECEPTIONIST);
        User target = users.findById(userId).orElseThrow(
                () -> new InvalidInputException("Unknown user id " + userId + "."));
        target.unlock();
        if (target instanceof Patient patient) {
            patient.unblock();
        }
        users.save(target);
    }

    /**
     * Receptionist registers a doctor account; the generated password is returned once.
     *
     * @param name      doctor name
     * @param specialty medical specialty
     * @param actor     logged-in user
     * @return the created doctor and its initial password
     * @throws UnauthorizedActionException when the actor is not a receptionist
     * @throws InvalidInputException       when a field is blank
     * @throws ClinicException             when the account cannot be persisted
     */
    public CreatedAccount createDoctor(String name, String specialty, User actor) throws ClinicException {
        requireRole(actor, Role.RECEPTIONIST);
        String cleanName = Validators.requireNonBlank("doctor name", name);
        String cleanSpecialty = Validators.requireNonBlank("specialty", specialty);
        String initialPassword = PasswordHasher.generatePassword();
        Doctor doctor = UserFactory.newDoctor(users.nextId(), cleanName, cleanSpecialty,
                DEFAULT_DAILY_CAPACITY, initialPassword, LocalDateTime.now(clock));
        users.save(doctor);
        return new CreatedAccount(doctor, initialPassword);
    }

    /**
     * Receptionist registers a patient account; the generated password is returned once.
     *
     * <p>SENIOR cannot be registered because it is derived from the date of birth (65+).
     * INSURED requires an insurer name.</p>
     *
     * @param name         patient name
     * @param dateOfBirth  date of birth, not in the future
     * @param category     registered category: STANDARD or INSURED
     * @param insurer      insurer name, required for INSURED
     * @param actor        logged-in user
     * @return the created patient and its initial password
     * @throws UnauthorizedActionException when the actor is not a receptionist
     * @throws InvalidInputException       when a field is invalid
     * @throws ClinicException             when the account cannot be persisted
     */
    public CreatedAccount createPatient(String name, LocalDate dateOfBirth, PatientCategory category,
                                        String insurer, User actor) throws ClinicException {
        requireRole(actor, Role.RECEPTIONIST);
        String cleanName = Validators.requireNonBlank("patient name", name);
        if (dateOfBirth == null) {
            throw new InvalidInputException("Date of birth is required.");
        }
        if (dateOfBirth.isAfter(LocalDate.now(clock))) {
            throw new InvalidInputException("Date of birth cannot be in the future.");
        }
        if (category == null) {
            throw new InvalidInputException("Patient category is required (STANDARD or INSURED).");
        }
        if (category == PatientCategory.SENIOR) {
            throw new InvalidInputException("Patients cannot be registered as SENIOR: the SENIOR category "
                    + "is derived automatically from the date of birth (65+). Register the patient as "
                    + "STANDARD or INSURED.");
        }
        String cleanInsurer = null;
        if (category == PatientCategory.INSURED) {
            cleanInsurer = Validators.requireNonBlank("insurer", insurer);
        } else if (insurer != null && !insurer.isBlank()) {
            cleanInsurer = insurer.trim();
        }
        String initialPassword = PasswordHasher.generatePassword();
        Patient patient = UserFactory.newPatient(users.nextId(), cleanName, dateOfBirth, category,
                cleanInsurer, initialPassword, LocalDateTime.now(clock));
        users.save(patient);
        return new CreatedAccount(patient, initialPassword);
    }

    /**
     * Throws {@link UnauthorizedActionException} unless the user has one of the allowed roles.
     *
     * @param user    logged-in user, may be {@code null}
     * @param allowed roles allowed to run the command
     * @throws UnauthorizedActionException when the user is missing or has another role
     */
    public void requireRole(User user, Role... allowed) throws UnauthorizedActionException {
        if (user == null) {
            throw new UnauthorizedActionException("You must be logged in to run this command.");
        }
        for (Role role : allowed) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw new UnauthorizedActionException(
                "This command is not available for the " + user.getRole() + " role.");
    }

    /**
     * Convenience guard that also casts the user.
     *
     * @param user logged-in user
     * @return the same user as a {@link Doctor}
     * @throws UnauthorizedActionException when the user is not a doctor
     */
    public Doctor requireDoctor(User user) throws UnauthorizedActionException {
        requireRole(user, Role.DOCTOR);
        return (Doctor) user;
    }

    /**
     * Convenience guard that also casts the user.
     *
     * @param user logged-in user
     * @return the same user as a {@link Patient}
     * @throws UnauthorizedActionException when the user is not a patient
     */
    public Patient requirePatient(User user) throws UnauthorizedActionException {
        requireRole(user, Role.PATIENT);
        return (Patient) user;
    }

    /**
     * Creates the default receptionist on a brand-new data folder if no receptionist exists.
     *
     * <p>The seeded account is {@code Front Desk} with the password {@code admin123}; the
     * password is documented in the README and must be changed by the clinic.</p>
     *
     * @throws ClinicException when the account cannot be created or persisted
     */
    public void ensureDefaultReceptionist() throws ClinicException {
        if (!users.findAllReceptionists().isEmpty()) {
            return;
        }
        createReceptionist(DEFAULT_RECEPTIONIST_NAME, DEFAULT_RECEPTIONIST_PASSWORD);
    }
}
