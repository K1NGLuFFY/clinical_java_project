package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.util.Formats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared JSON configuration and file helpers for the persistence layer.
 *
 * <p>Every repository uses the same {@link Gson} instance so the data files share one stable
 * on-disk format: dates are written as {@code yyyy-MM-dd}, times as {@code HH:mm}, date-times
 * as {@code yyyy-MM-dd'T'HH:mm:ss} and money as its plain decimal string (never in scientific
 * notation). All adapters are null safe, so an absent optional value stays absent.</p>
 *
 * <p>The helper methods implement the project persistence rules: missing files are created with
 * their empty JSON seed, every write replaces the file atomically through a sibling
 * {@code .tmp} file, and unreadable or corrupt content is reported as a {@link StorageException}
 * that names the offending file and explains how to recover.</p>
 */
public final class JsonSupport {

    /** Recovery hint appended to every corrupt-file message. */
    private static final String CORRUPT_HINT = "Fix or delete the file to start again.";

    private static final StandardOpenOption[] WRITE_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    };

    private JsonSupport() {
    }

    /**
     * Creates the configured Gson instance used by every JSON repository.
     *
     * <p>Output is pretty printed and HTML escaping is disabled so the data files stay readable
     * in a text editor. The java.time and money adapters keep the stored format ISO-8601 based
     * and independent of the machine locale.</p>
     *
     * @return a ready-to-use, thread-safe Gson instance
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(DayOfWeek.class, new DayOfWeekAdapter())
                .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())
                .create();
    }

    /**
     * Creates {@code dataDirectory} (and its parents) when missing and returns its absolute form.
     *
     * @param dataDirectory the data folder requested by the caller
     * @return the normalised absolute path of the existing directory
     * @throws StorageException when the directory cannot be created
     */
    static Path prepareDirectory(Path dataDirectory) throws StorageException {
        Objects.requireNonNull(dataDirectory, "dataDirectory is required");
        Path directory = dataDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new StorageException(
                    "Could not create data directory '" + directory + "': " + ex.getMessage(), ex);
        }
        return directory;
    }

    /**
     * Reads a UTF-8 data file, creating it with {@code emptyContent} when it does not exist yet.
     *
     * <p>A leading byte order mark is tolerated and stripped, although this layer never writes
     * one. The method never returns {@code null}; an existing empty file yields an empty string,
     * which callers treat as "no records".</p>
     *
     * @param file         the data file to read
     * @param emptyContent the seed content written when the file is missing, e.g. {@code []}
     * @return the file content without a byte order mark
     * @throws StorageException when the file cannot be created or read
     */
    static String readOrCreate(Path file, String emptyContent) throws StorageException {
        Path absolute = file.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(absolute)) {
                Files.writeString(absolute, emptyContent, StandardCharsets.UTF_8);
                return emptyContent;
            }
            String content = Files.readString(absolute, StandardCharsets.UTF_8);
            if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
                return content.substring(1);
            }
            return content == null ? "" : content;
        } catch (IOException ex) {
            throw new StorageException(
                    "Could not read data file '" + absolute + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Replaces {@code file} with {@code content} using the atomic temp-file-then-move pattern.
     *
     * <p>The content is first written to a sibling {@code <name>.tmp} file and the temporary file
     * is then moved over the target with {@link StandardCopyOption#ATOMIC_MOVE}. If the file
     * system cannot move atomically the move is retried without that option, which still avoids
     * exposing a half-written data file.</p>
     *
     * @param file    the target data file
     * @param content the complete new UTF-8 file content
     * @throws StorageException when the file cannot be written
     */
    static void writeAtomically(Path file, String content) throws StorageException {
        Path target = file.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temporary, content, StandardCharsets.UTF_8, WRITE_OPTIONS);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            deleteQuietly(temporary);
            throw new StorageException(
                    "Could not write data file '" + target + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Builds the standard corrupt-file error for {@code file} from a parser failure.
     *
     * @param file  the offending data file
     * @param cause the low level failure, may be {@code null}
     * @return a {@link StorageException} naming the file and the recovery hint
     */
    static StorageException corrupt(Path file, Throwable cause) {
        String detail = cause == null ? "" : cause.getMessage();
        if (detail != null) {
            // Keep the human part of library messages: drop the exception class prefix and the
            // Gson troubleshooting link, both of which only add noise to a console message.
            detail = detail.replaceFirst("^[A-Za-z_$][A-Za-z0-9_$.]*Exception: ", "");
            int troubleshooting = detail.indexOf("See https://");
            if (troubleshooting >= 0) {
                detail = detail.substring(0, troubleshooting);
            }
            detail = detail.trim();
        }
        if ((detail == null || detail.isBlank()) && cause != null) {
            detail = cause.getClass().getSimpleName();
        }
        return corrupt(file, detail, cause);
    }

    /**
     * Builds the standard corrupt-file error for {@code file} from a textual detail.
     *
     * @param file   the offending data file
     * @param detail what is wrong with it, may be {@code null} or blank
     * @param cause  the underlying failure, may be {@code null}
     * @return a {@link StorageException} naming the file and the recovery hint
     */
    static StorageException corrupt(Path file, String detail, Throwable cause) {
        String target = file.toAbsolutePath().normalize().toString();
        String clean = detail == null ? "" : detail.trim();
        if (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        String message = "Corrupt data file '" + target + "'"
                + (clean.isEmpty() ? "." : ": " + clean + ".")
                + " " + CORRUPT_HINT;
        return cause == null ? new StorageException(message) : new StorageException(message, cause);
    }

    /** Deletes a leftover temporary file without masking the original failure. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The temporary file is best-effort cleanup only.
        }
    }

    // ------------------------------------------------------------- type adapters

    /** Null-safe ISO date adapter ({@code yyyy-MM-dd}). */
    private static final class LocalDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(Formats.ISO_DATE));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return LocalDate.parse(raw, Formats.ISO_DATE);
            } catch (DateTimeParseException ex) {
                throw new JsonSyntaxException("Invalid date '" + raw + "', expected yyyy-MM-dd.", ex);
            }
        }
    }

    /** Null-safe clock time adapter ({@code HH:mm}). */
    private static final class LocalTimeAdapter extends TypeAdapter<LocalTime> {

        @Override
        public void write(JsonWriter out, LocalTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(Formats.CLOCK_TIME));
            }
        }

        @Override
        public LocalTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return LocalTime.parse(raw, Formats.CLOCK_TIME);
            } catch (DateTimeParseException ex) {
                try {
                    return LocalTime.parse(raw, DateTimeFormatter.ISO_LOCAL_TIME);
                } catch (DateTimeParseException fallback) {
                    throw new JsonSyntaxException(
                            "Invalid time '" + raw + "', expected HH:mm.", fallback);
                }
            }
        }
    }

    /** Null-safe date-time adapter ({@code yyyy-MM-dd'T'HH:mm:ss}). */
    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(Formats.ISO_DATE_TIME));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return LocalDateTime.parse(raw, Formats.ISO_DATE_TIME);
            } catch (DateTimeParseException ex) {
                try {
                    return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException fallback) {
                    throw new JsonSyntaxException("Invalid date-time '" + raw
                            + "', expected yyyy-MM-dd'T'HH:mm:ss.", fallback);
                }
            }
        }
    }

    /** Null-safe day of week adapter that stores the enum name, e.g. {@code MONDAY}. */
    private static final class DayOfWeekAdapter extends TypeAdapter<DayOfWeek> {

        @Override
        public void write(JsonWriter out, DayOfWeek value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.name());
            }
        }

        @Override
        public DayOfWeek read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new JsonSyntaxException("Invalid day of week '" + raw + "'.", ex);
            }
        }
    }

    /** Null-safe money adapter that stores the plain decimal form as a JSON string. */
    private static final class BigDecimalAdapter extends TypeAdapter<BigDecimal> {

        @Override
        public void write(JsonWriter out, BigDecimal value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toPlainString());
            }
        }

        @Override
        public BigDecimal read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException ex) {
                throw new JsonSyntaxException("Invalid decimal '" + raw + "'.", ex);
            }
        }
    }
}
