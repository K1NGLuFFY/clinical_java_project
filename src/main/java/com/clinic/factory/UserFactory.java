package com.clinic.factory;

import com.clinic.exception.StorageException;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.Receptionist;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.util.PasswordHasher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * User factory. Creates the right {@link User} subclass with a hashed initial password and
 * rebuilds subclasses from JSON using the {@code type} discriminator stored in the data files.
 */
public final class UserFactory {

    public static final String TYPE_RECEPTIONIST = "RECEPTIONIST";
    public static final String TYPE_DOCTOR = "DOCTOR";
    public static final String TYPE_PATIENT = "PATIENT";

    private UserFactory() {
    }

    public static Receptionist newReceptionist(int id, String name, String rawPassword, LocalDateTime createdAt) {
        String salt = PasswordHasher.newSalt();
        return new Receptionist(id, name, PasswordHasher.hash(rawPassword, salt), salt, createdAt);
    }

    public static Doctor newDoctor(int id, String name, String specialty, int dailyCapacity,
                                   String rawPassword, LocalDateTime createdAt) {
        String salt = PasswordHasher.newSalt();
        return new Doctor(id, name, specialty, dailyCapacity,
                PasswordHasher.hash(rawPassword, salt), salt, createdAt);
    }

    public static Patient newPatient(int id, String name, LocalDate dateOfBirth, PatientCategory category,
                                     String insurer, String rawPassword, LocalDateTime createdAt) {
        String salt = PasswordHasher.newSalt();
        return new Patient(id, name, dateOfBirth, category, insurer,
                PasswordHasher.hash(rawPassword, salt), salt, createdAt);
    }

    /** Discriminator written into JSON. */
    public static String typeOf(User user) {
        return switch (user.getRole()) {
            case RECEPTIONIST -> TYPE_RECEPTIONIST;
            case DOCTOR -> TYPE_DOCTOR;
            case PATIENT -> TYPE_PATIENT;
        };
    }

    /** Rebuilds the concrete subclass for a stored {@code type} value. */
    public static Class<? extends User> classForType(String type) throws StorageException {
        if (type == null || type.isBlank()) {
            throw new StorageException("User record is missing its 'type' field.");
        }
        return switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "RECEPTIONIST" -> Receptionist.class;
            case "DOCTOR" -> Doctor.class;
            case "PATIENT" -> Patient.class;
            default -> throw new StorageException("Unknown user type '" + type + "'.");
        };
    }

    /**
     * Rebuilds any user subclass from a JSON object. The caller supplies the configured Gson
     * instance so date/time adapters are shared with the rest of the storage layer.
     */
    public static User fromJson(JsonObject json, Gson gson) throws StorageException {
        if (json == null) {
            throw new StorageException("Cannot rebuild a null user record.");
        }
        String type = null;
        if (json.has("type") && !json.get("type").isJsonNull()) {
            type = json.get("type").getAsString();
        } else if (json.has("role") && !json.get("role").isJsonNull()) {
            type = json.get("role").getAsString();
        }
        Class<? extends User> userClass = classForType(type);
        try {
            return gson.fromJson(json, userClass);
        } catch (RuntimeException ex) {
            throw new StorageException("Corrupt user record: " + ex.getMessage(), ex);
        }
    }

    /** Type name for a role, used when seeding. */
    public static String typeForRole(Role role) {
        return switch (role) {
            case RECEPTIONIST -> TYPE_RECEPTIONIST;
            case DOCTOR -> TYPE_DOCTOR;
            case PATIENT -> TYPE_PATIENT;
        };
    }
}
