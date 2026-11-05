package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.WaitlistEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * JSON-backed {@link WaitlistRepository}.
 *
 * <p>Entries are stored as an array in {@code data/waitlist.json} and loaded eagerly when the
 * repository is constructed. {@link #findAll()} returns every entry including closed ones, while
 * {@link #findOpenForDoctor(int)} only reports the statuses that can still be offered. Entries are
 * returned in insertion order; callers that need first-in-first-out behaviour must sort by
 * {@code createdAt} themselves.</p>
 */
public final class JsonWaitlistRepository implements WaitlistRepository {

    private static final String FILE_NAME = "waitlist.json";
    private static final String EMPTY_ARRAY = "[]";

    private final Path file;
    private final Gson gson;
    private final SequenceStore sequences;
    private final Map<Integer, WaitlistEntry> entries = new LinkedHashMap<>();

    /**
     * Loads {@code data/waitlist.json}, creating the directory and file when missing.
     *
     * @param dataDirectory the folder that holds the JSON data files
     * @param gson          the shared configured Gson instance
     * @param sequences     the shared id sequence store
     * @throws StorageException when the file cannot be read or contains corrupt data
     */
    public JsonWaitlistRepository(Path dataDirectory, Gson gson, SequenceStore sequences)
            throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.sequences = Objects.requireNonNull(sequences, "sequences are required");
        load();
    }

    /** Returns the waiting-list entry with the given id. */
    @Override
    public synchronized Optional<WaitlistEntry> findById(int id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** Returns a defensive copy of every entry, including closed ones, in insertion order. */
    @Override
    public synchronized List<WaitlistEntry> findAll() {
        return new ArrayList<>(entries.values());
    }

    /** Returns the doctor's entries that are still open (WAITING or OFFERED). */
    @Override
    public synchronized List<WaitlistEntry> findOpenForDoctor(int doctorId) {
        List<WaitlistEntry> result = new ArrayList<>();
        for (WaitlistEntry entry : entries.values()) {
            if (entry.getDoctorId() == doctorId && entry.getStatus() != null
                    && entry.getStatus().isOpen()) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Returns every waiting-list entry of one patient, including closed ones. */
    @Override
    public synchronized List<WaitlistEntry> findByPatient(int patientId) {
        List<WaitlistEntry> result = new ArrayList<>();
        for (WaitlistEntry entry : entries.values()) {
            if (entry.getPatientId() == patientId) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Inserts or updates the entry and immediately rewrites {@code waitlist.json}. */
    @Override
    public synchronized void save(WaitlistEntry entry) throws StorageException {
        if (entry == null) {
            throw new IllegalArgumentException("waitlist entry is required");
        }
        entries.put(entry.getId(), entry);
        persist();
    }

    /** Reserves the next waitlist id and persists it before returning. */
    @Override
    public int nextId() throws StorageException {
        return sequences.next("waitlist");
    }

    /** Reads the entry array, converting any parser failure into a StorageException. */
    private void load() throws StorageException {
        String content = JsonSupport.readOrCreate(file, EMPTY_ARRAY);
        entries.clear();
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
                    throw new JsonSyntaxException(
                            "every waiting-list entry must be a JSON object");
                }
                WaitlistEntry entry = gson.fromJson(element, WaitlistEntry.class);
                if (entry == null) {
                    throw new JsonSyntaxException("waiting-list entry is null");
                }
                if (entries.containsKey(entry.getId())) {
                    throw JsonSupport.corrupt(
                            file, "duplicate waiting-list id " + entry.getId(), null);
                }
                entries.put(entry.getId(), entry);
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw JsonSupport.corrupt(file, ex);
        }
    }

    /** Writes every entry atomically. */
    private void persist() throws StorageException {
        JsonArray array = new JsonArray();
        for (WaitlistEntry entry : entries.values()) {
            array.add(gson.toJsonTree(entry));
        }
        JsonSupport.writeAtomically(file, gson.toJson(array) + "\n");
    }
}
