package com.clinic.repository;

import com.clinic.exception.StorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only audit trail backed by {@code data/activity.log}.
 *
 * <p>Each call to {@link #append(String)} writes exactly one UTF-8 line terminated by a newline.
 * The class is thread safe, so several services can log concurrently without interleaving partial
 * lines. Missing directories and files are created when the repository is constructed.</p>
 */
public final class JsonActivityLogRepository implements ActivityLogRepository {

    private static final String FILE_NAME = "activity.log";

    private final Path file;

    /**
     * Creates the data directory and an empty {@code data/activity.log} when they are missing.
     *
     * @param dataDirectory the folder that holds the data files
     * @throws StorageException when the directory or log file cannot be created
     */
    public JsonActivityLogRepository(Path dataDirectory) throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, "", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        } catch (IOException ex) {
            throw new StorageException(
                    "Could not create activity log '" + file + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Appends one audit line. Embedded line breaks are flattened to spaces so one call always
     * produces exactly one line.
     *
     * @param line the complete line to append, without a trailing newline
     * @throws StorageException when the line cannot be written
     */
    @Override
    public synchronized void append(String line) throws StorageException {
        if (line == null) {
            throw new IllegalArgumentException("activity log line is required");
        }
        String normalised = line.replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ');
        try {
            Files.writeString(file, normalised + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new StorageException(
                    "Could not append to activity log '" + file + "': " + ex.getMessage(), ex);
        }
    }
}
