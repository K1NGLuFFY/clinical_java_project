package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persistent id sequences for the repositories.
 *
 * <p>Sequences live in {@code data/sequences.json} and are persisted after every increment, so
 * ids keep counting after a restart and are never reused. The file is a JSON object of counters:</p>
 *
 * <pre>
 * {
 *   "user": 15,
 *   "appointment": 15,
 *   "waitlist": 1,
 *   "visit": 13,
 *   "notification": 0
 * }
 * </pre>
 *
 * <p>The class is thread safe: increments of different repositories are serialised so two
 * threads can never receive the same id.</p>
 */
public final class SequenceStore {

    /** Sequence names, in file order. */
    private static final List<String> KEYS =
            List.of("user", "appointment", "waitlist", "visit", "notification");

    private static final String FILE_NAME = "sequences.json";

    private final Path file;
    private final Gson gson;
    private final Map<String, Integer> values = new LinkedHashMap<>();

    /**
     * Loads (or creates) {@code data/sequences.json} inside the supplied data directory.
     *
     * @param dataDirectory the folder that holds the JSON data files; created when missing
     * @throws StorageException when the directory cannot be created or the file is corrupt
     */
    public SequenceStore(Path dataDirectory) throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        this.gson = JsonSupport.createGson();
        load();
    }

    /**
     * Reserves and returns the next value of a named sequence.
     *
     * <p>The new value is written to disk before this method returns, so a crash right after the
     * call can never make the id available again. The first call for a sequence returns {@code 1}.</p>
     *
     * @param name one of {@code user}, {@code appointment}, {@code waitlist}, {@code visit},
     *             {@code notification}
     * @return the next positive id for that sequence
     * @throws StorageException when the new value cannot be persisted
     * @throws IllegalArgumentException when the name is unknown
     */
    public synchronized int next(String name) throws StorageException {
        String key = requireKey(name);
        int previous = values.getOrDefault(key, 0);
        values.put(key, previous + 1);
        try {
            persist();
        } catch (StorageException ex) {
            values.put(key, previous);
            throw ex;
        }
        return previous + 1;
    }

    /**
     * Returns the last value handed out for a sequence without changing it.
     *
     * @param name one of the known sequence names; unknown or {@code null} names yield {@code 0}
     * @return the last persisted value, or {@code 0} when the sequence was never used
     */
    public synchronized int current(String name) {
        if (name == null) {
            return 0;
        }
        return values.getOrDefault(name.trim().toLowerCase(Locale.ROOT), 0);
    }

    /** Reads the stored counters, validating the file before anything is returned to callers. */
    private void load() throws StorageException {
        String content = JsonSupport.readOrCreate(file, defaultJson());
        Map<String, Integer> loaded = new LinkedHashMap<>();
        for (String key : KEYS) {
            loaded.put(key, 0);
        }
        if (content != null && !content.isBlank()) {
            try {
                JsonElement root = JsonParser.parseString(content);
                if (root.isJsonNull()) {
                    loaded.replaceAll((key, value) -> 0);
                } else if (!root.isJsonObject()) {
                    throw new JsonSyntaxException("root element must be a JSON object");
                } else {
                    JsonObject object = root.getAsJsonObject();
                    for (String key : KEYS) {
                        if (!object.has(key) || object.get(key).isJsonNull()) {
                            continue;
                        }
                        JsonElement element = object.get(key);
                        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                            throw new JsonSyntaxException(
                                    "value of '" + key + "' must be a whole number");
                        }
                        int value = element.getAsInt();
                        if (value < 0) {
                            throw new JsonSyntaxException(
                                    "value of '" + key + "' must not be negative");
                        }
                        loaded.put(key, value);
                    }
                }
            } catch (RuntimeException ex) {
                throw JsonSupport.corrupt(file, ex);
            }
        }
        values.clear();
        values.putAll(loaded);
    }

    /** Writes all counters atomically. */
    private void persist() throws StorageException {
        JsonObject object = new JsonObject();
        for (String key : KEYS) {
            object.addProperty(key, values.getOrDefault(key, 0));
        }
        JsonSupport.writeAtomically(file, gson.toJson(object) + "\n");
    }

    /** JSON seed written when the sequence file does not exist yet. */
    private String defaultJson() {
        JsonObject object = new JsonObject();
        for (String key : KEYS) {
            object.addProperty(key, 0);
        }
        return gson.toJson(object) + "\n";
    }

    /** Normalises and validates a sequence name. */
    private static String requireKey(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sequence name is required.");
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!KEYS.contains(key)) {
            throw new IllegalArgumentException(
                    "Unknown id sequence '" + name.trim() + "'. Expected one of " + KEYS + ".");
        }
        return key;
    }
}
