package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.VisitRecord;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-backed {@link VisitRecordRepository}.
 *
 * <p>Clinical notes are stored as an array in {@code data/visit-records.json} and loaded eagerly
 * when the repository is constructed. Amendments are ordinary records pointing at the note they
 * correct, so every query returns the original notes and their amendments; use
 * {@link #findLatestForAppointment(int)} to get the current version of a record.</p>
 */
public final class JsonVisitRecordRepository implements VisitRecordRepository {

    private static final String FILE_NAME = "visit-records.json";
    private static final String EMPTY_ARRAY = "[]";

    /** Orders records by recording time, then by id; records without a timestamp sort first. */
    private static final Comparator<VisitRecord> LATEST_FIRST =
            Comparator.comparing(VisitRecord::getRecordedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparingInt(VisitRecord::getId);

    private final Path file;
    private final Gson gson;
    private final SequenceStore sequences;
    private final Map<Integer, VisitRecord> records = new LinkedHashMap<>();

    /**
     * Loads {@code data/visit-records.json}, creating the directory and file when missing.
     *
     * @param dataDirectory the folder that holds the JSON data files
     * @param gson          the shared configured Gson instance
     * @param sequences     the shared id sequence store
     * @throws StorageException when the file cannot be read or contains corrupt data
     */
    public JsonVisitRecordRepository(Path dataDirectory, Gson gson, SequenceStore sequences)
            throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.sequences = Objects.requireNonNull(sequences, "sequences are required");
        load();
    }

    /** Returns the visit record with the given id. */
    @Override
    public synchronized Optional<VisitRecord> findById(int id) {
        return Optional.ofNullable(records.get(id));
    }

    /** Returns every record (original notes and amendments) for one appointment. */
    @Override
    public synchronized List<VisitRecord> findByAppointment(int appointmentId) {
        List<VisitRecord> result = new ArrayList<>();
        for (VisitRecord record : records.values()) {
            if (record.getAppointmentId() == appointmentId) {
                result.add(record);
            }
        }
        return result;
    }

    /** Returns every record (original notes and amendments) for one patient. */
    @Override
    public synchronized List<VisitRecord> findByPatient(int patientId) {
        List<VisitRecord> result = new ArrayList<>();
        for (VisitRecord record : records.values()) {
            if (record.getPatientId() == patientId) {
                result.add(record);
            }
        }
        return result;
    }

    /** Returns every record (original notes and amendments) written by one doctor. */
    @Override
    public synchronized List<VisitRecord> findByDoctor(int doctorId) {
        List<VisitRecord> result = new ArrayList<>();
        for (VisitRecord record : records.values()) {
            if (record.getDoctorId() == doctorId) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Returns the newest record for an appointment: greatest {@code recordedAt}, ties broken by
     * the highest id. Because amendments are appended later they win over the note they correct.
     */
    @Override
    public synchronized Optional<VisitRecord> findLatestForAppointment(int appointmentId) {
        VisitRecord latest = null;
        for (VisitRecord record : records.values()) {
            if (record.getAppointmentId() != appointmentId) {
                continue;
            }
            if (latest == null || LATEST_FIRST.compare(record, latest) > 0) {
                latest = record;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** Returns a defensive copy of every visit record in insertion order. */
    @Override
    public synchronized List<VisitRecord> findAll() {
        return new ArrayList<>(records.values());
    }

    /** Inserts or updates the record and immediately rewrites {@code visit-records.json}. */
    @Override
    public synchronized void save(VisitRecord record) throws StorageException {
        if (record == null) {
            throw new IllegalArgumentException("visit record is required");
        }
        records.put(record.getId(), record);
        persist();
    }

    /** Reserves the next visit record id and persists it before returning. */
    @Override
    public int nextId() throws StorageException {
        return sequences.next("visit");
    }

    /** Reads the record array, converting any parser failure into a StorageException. */
    private void load() throws StorageException {
        String content = JsonSupport.readOrCreate(file, EMPTY_ARRAY);
        records.clear();
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
                    throw new JsonSyntaxException("every visit record must be a JSON object");
                }
                VisitRecord record = gson.fromJson(element, VisitRecord.class);
                if (record == null) {
                    throw new JsonSyntaxException("visit record entry is null");
                }
                if (records.containsKey(record.getId())) {
                    throw JsonSupport.corrupt(file, "duplicate visit record id " + record.getId(), null);
                }
                records.put(record.getId(), record);
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw JsonSupport.corrupt(file, ex);
        }
    }

    /** Writes every visit record atomically. */
    private void persist() throws StorageException {
        JsonArray array = new JsonArray();
        for (VisitRecord record : records.values()) {
            array.add(gson.toJsonTree(record));
        }
        JsonSupport.writeAtomically(file, gson.toJson(array) + "\n");
    }
}
