package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.factory.UserFactory;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.Receptionist;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-backed {@link UserRepository}.
 *
 * <p>Accounts are split over two files by role: doctors (with their weekly schedule and leave
 * periods) are written to {@code data/doctors.json} and receptionists plus patients to
 * {@code data/users.json}. Both files are loaded eagerly into one in-memory map, so
 * {@link #findById(int)} works for every role. Each stored record carries a {@code type}
 * discriminator written from {@link UserFactory#typeOf(User)}; loading rebuilds the concrete
 * subclass through {@link UserFactory#fromJson(JsonObject, Gson)}.</p>
 *
 * <p>All methods are thread safe and every write replaces the whole file atomically.</p>
 */
public final class JsonUserRepository implements UserRepository {

    private static final String USERS_FILE = "users.json";
    private static final String DOCTORS_FILE = "doctors.json";
    private static final String EMPTY_ARRAY = "[]";

    private final Path usersFile;
    private final Path doctorsFile;
    private final Gson gson;
    private final SequenceStore sequences;
    private final Map<Integer, User> users = new LinkedHashMap<>();

    /**
     * Loads both user files, creating them (and the data directory) when missing.
     *
     * @param dataDirectory the folder that holds the JSON data files
     * @param gson          the shared configured Gson instance
     * @param sequences     the shared id sequence store
     * @throws StorageException when a file cannot be read or contains corrupt data
     */
    public JsonUserRepository(Path dataDirectory, Gson gson, SequenceStore sequences)
            throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.usersFile = directory.resolve(USERS_FILE);
        this.doctorsFile = directory.resolve(DOCTORS_FILE);
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.sequences = Objects.requireNonNull(sequences, "sequences are required");
        load();
    }

    /** Returns the account with the given id, whichever role it has. */
    @Override
    public synchronized Optional<User> findById(int id) {
        return Optional.ofNullable(users.get(id));
    }

    /** Returns the doctor with the given id, or empty when the id is not a doctor. */
    @Override
    public synchronized Optional<Doctor> findDoctor(int id) {
        User user = users.get(id);
        return user instanceof Doctor doctor ? Optional.of(doctor) : Optional.empty();
    }

    /** Returns the patient with the given id, or empty when the id is not a patient. */
    @Override
    public synchronized Optional<Patient> findPatient(int id) {
        User user = users.get(id);
        return user instanceof Patient patient ? Optional.of(patient) : Optional.empty();
    }

    /** Returns a defensive copy of every account in insertion order. */
    @Override
    public synchronized List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    /** Returns a defensive copy of the doctors only. */
    @Override
    public synchronized List<Doctor> findAllDoctors() {
        List<Doctor> doctors = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Doctor doctor) {
                doctors.add(doctor);
            }
        }
        return doctors;
    }

    /** Returns a defensive copy of the patients only. */
    @Override
    public synchronized List<Patient> findAllPatients() {
        List<Patient> patients = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Patient patient) {
                patients.add(patient);
            }
        }
        return patients;
    }

    /** Returns a defensive copy of the receptionists only. */
    @Override
    public synchronized List<Receptionist> findAllReceptionists() {
        List<Receptionist> receptionists = new ArrayList<>();
        for (User user : users.values()) {
            if (user instanceof Receptionist receptionist) {
                receptionists.add(receptionist);
            }
        }
        return receptionists;
    }

    /**
     * Inserts or updates the account and immediately rewrites the file that owns its role:
     * doctors go to {@code doctors.json}, everyone else to {@code users.json}.
     */
    @Override
    public synchronized void save(User user) throws StorageException {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        users.put(user.getId(), user);
        if (user.getRole() == Role.DOCTOR) {
            persistDoctors();
        } else {
            persistUsers();
        }
    }

    /** Persists every in-memory account, rewriting both {@code users.json} and {@code doctors.json}. */
    @Override
    public synchronized void saveAll() throws StorageException {
        persistUsers();
        persistDoctors();
    }

    /** Reserves the next user id from the shared sequence store and persists it before returning. */
    @Override
    public int nextId() throws StorageException {
        return sequences.next("user");
    }

    /** Reads both files into one map, rejecting duplicates instead of partially loading. */
    private void load() throws StorageException {
        Map<Integer, User> loaded = new LinkedHashMap<>();
        for (User user : parse(usersFile, JsonSupport.readOrCreate(usersFile, EMPTY_ARRAY))) {
            addUnique(loaded, usersFile, user);
        }
        for (User user : parse(doctorsFile, JsonSupport.readOrCreate(doctorsFile, EMPTY_ARRAY))) {
            addUnique(loaded, doctorsFile, user);
        }
        users.clear();
        users.putAll(loaded);
    }

    /** Parses a user array, converting any parser failure into a file-naming StorageException. */
    private List<User> parse(Path file, String content) throws StorageException {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonElement root = JsonParser.parseString(content);
            if (root.isJsonNull()) {
                return new ArrayList<>();
            }
            if (!root.isJsonArray()) {
                throw new JsonSyntaxException("root element must be a JSON array");
            }
            List<User> parsed = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new JsonSyntaxException("every account entry must be a JSON object");
                }
                parsed.add(UserFactory.fromJson(element.getAsJsonObject(), gson));
            }
            return parsed;
        } catch (StorageException ex) {
            throw JsonSupport.corrupt(file, ex);
        } catch (RuntimeException ex) {
            throw JsonSupport.corrupt(file, ex);
        }
    }

    /** Adds one loaded account, refusing duplicate ids across the two files. */
    private void addUnique(Map<Integer, User> target, Path file, User user) throws StorageException {
        if (user == null) {
            throw JsonSupport.corrupt(file, "account entry is null", null);
        }
        if (target.containsKey(user.getId())) {
            throw JsonSupport.corrupt(file, "duplicate account id " + user.getId(), null);
        }
        target.put(user.getId(), user);
    }

    /** Serialises one account with its polymorphic {@code type} discriminator. */
    private JsonObject toJson(User user) {
        JsonObject object = gson.toJsonTree(user).getAsJsonObject();
        object.addProperty("type", UserFactory.typeOf(user));
        return object;
    }

    /** Writes every non-doctor account to {@code users.json}. */
    private void persistUsers() throws StorageException {
        JsonArray array = new JsonArray();
        for (User user : users.values()) {
            if (user.getRole() != Role.DOCTOR) {
                array.add(toJson(user));
            }
        }
        JsonSupport.writeAtomically(usersFile, gson.toJson(array) + "\n");
    }

    /** Writes every doctor account to {@code doctors.json}. */
    private void persistDoctors() throws StorageException {
        JsonArray array = new JsonArray();
        for (User user : users.values()) {
            if (user.getRole() == Role.DOCTOR) {
                array.add(toJson(user));
            }
        }
        JsonSupport.writeAtomically(doctorsFile, gson.toJson(array) + "\n");
    }
}
