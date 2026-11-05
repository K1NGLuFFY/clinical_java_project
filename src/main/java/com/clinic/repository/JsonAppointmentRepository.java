package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.Appointment;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-backed {@link AppointmentRepository}.
 *
 * <p>Appointments are stored as an array in {@code data/appointments.json} and loaded eagerly when
 * the repository is constructed. Date filters compare the appointment's start date only; ranges
 * include both end points. Every write replaces the whole file atomically, and all methods are
 * thread safe.</p>
 */
public final class JsonAppointmentRepository implements AppointmentRepository {

    private static final String FILE_NAME = "appointments.json";
    private static final String EMPTY_ARRAY = "[]";

    private final Path file;
    private final Gson gson;
    private final SequenceStore sequences;
    private final Map<Integer, Appointment> appointments = new LinkedHashMap<>();

    /**
     * Loads {@code data/appointments.json}, creating the directory and file when missing.
     *
     * @param dataDirectory the folder that holds the JSON data files
     * @param gson          the shared configured Gson instance
     * @param sequences     the shared id sequence store
     * @throws StorageException when the file cannot be read or contains corrupt data
     */
    public JsonAppointmentRepository(Path dataDirectory, Gson gson, SequenceStore sequences)
            throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.sequences = Objects.requireNonNull(sequences, "sequences are required");
        load();
    }

    /** Returns the appointment with the given id. */
    @Override
    public synchronized Optional<Appointment> findById(int id) {
        return Optional.ofNullable(appointments.get(id));
    }

    /** Returns a defensive copy of every appointment in insertion order. */
    @Override
    public synchronized List<Appointment> findAll() {
        return new ArrayList<>(appointments.values());
    }

    /** Returns a defensive copy of every appointment of one doctor. */
    @Override
    public synchronized List<Appointment> findByDoctor(int doctorId) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment appointment : appointments.values()) {
            if (appointment.getDoctorId() == doctorId) {
                result.add(appointment);
            }
        }
        return result;
    }

    /** Returns the doctor's appointments whose start date equals {@code date}. */
    @Override
    public synchronized List<Appointment> findByDoctorAndDate(int doctorId, LocalDate date) {
        List<Appointment> result = new ArrayList<>();
        if (date == null) {
            return result;
        }
        for (Appointment appointment : appointments.values()) {
            LocalDateTime start = appointment.getStart();
            if (appointment.getDoctorId() == doctorId && start != null
                    && start.toLocalDate().equals(date)) {
                result.add(appointment);
            }
        }
        return result;
    }

    /** Returns every appointment of one patient regardless of status. */
    @Override
    public synchronized List<Appointment> findByPatient(int patientId) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment appointment : appointments.values()) {
            if (appointment.getPatientId() == patientId) {
                result.add(appointment);
            }
        }
        return result;
    }

    /** Returns the doctor's appointments that still occupy a slot (BOOKED or CHECKED_IN). */
    @Override
    public synchronized List<Appointment> findActiveByDoctor(int doctorId) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment appointment : appointments.values()) {
            if (appointment.getDoctorId() == doctorId && appointment.isActive()) {
                result.add(appointment);
            }
        }
        return result;
    }

    /**
     * Returns appointments whose start date lies inside the inclusive range
     * {@code [from, to]}. A {@code null} bound means unbounded.
     */
    @Override
    public synchronized List<Appointment> findBetween(LocalDate from, LocalDate to) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment appointment : appointments.values()) {
            LocalDateTime start = appointment.getStart();
            if (start == null) {
                continue;
            }
            LocalDate date = start.toLocalDate();
            if (from != null && date.isBefore(from)) {
                continue;
            }
            if (to != null && date.isAfter(to)) {
                continue;
            }
            result.add(appointment);
        }
        return result;
    }

    /** Inserts or updates the appointment and immediately rewrites {@code appointments.json}. */
    @Override
    public synchronized void save(Appointment appointment) throws StorageException {
        if (appointment == null) {
            throw new IllegalArgumentException("appointment is required");
        }
        appointments.put(appointment.getId(), appointment);
        persist();
    }

    /** Reserves the next appointment id and persists it before returning. */
    @Override
    public int nextId() throws StorageException {
        return sequences.next("appointment");
    }

    /** Reads the appointment array, converting any parser failure into a StorageException. */
    private void load() throws StorageException {
        String content = JsonSupport.readOrCreate(file, EMPTY_ARRAY);
        appointments.clear();
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(content);
            if (root.isJsonNull()) {
                return;
            }
            if (!root.isJsonArray()) {
                throw new JsonSyntaxException("root element must be a JSON array");
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new JsonSyntaxException("every appointment entry must be a JSON object");
                }
                Appointment appointment = gson.fromJson(element, Appointment.class);
                if (appointment == null) {
                    throw new JsonSyntaxException("appointment entry is null");
                }
                if (appointments.containsKey(appointment.getId())) {
                    throw JsonSupport.corrupt(
                            file, "duplicate appointment id " + appointment.getId(), null);
                }
                appointments.put(appointment.getId(), appointment);
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw JsonSupport.corrupt(file, ex);
        }
    }

    /** Writes every appointment atomically. */
    private void persist() throws StorageException {
        JsonArray array = new JsonArray();
        for (Appointment appointment : appointments.values()) {
            array.add(gson.toJsonTree(appointment));
        }
        JsonSupport.writeAtomically(file, gson.toJson(array) + "\n");
    }
}
