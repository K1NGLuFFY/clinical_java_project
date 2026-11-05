package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.Notification;
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

/**
 * JSON-backed {@link NotificationRepository}.
 *
 * <p>Messages live in {@code data/notifications.json} and are loaded eagerly when the repository
 * is constructed. Ids are reserved with {@link #nextId()} and supplied by the caller to
 * {@link #add(Notification)}; both {@code add} and {@code markAllRead} rewrite the file
 * immediately, so unread state survives a restart.</p>
 */
public final class JsonNotificationRepository implements NotificationRepository {

    private static final String FILE_NAME = "notifications.json";
    private static final String EMPTY_ARRAY = "[]";

    private final Path file;
    private final Gson gson;
    private final SequenceStore sequences;
    private final Map<Integer, Notification> notifications = new LinkedHashMap<>();

    /**
     * Loads {@code data/notifications.json}, creating the directory and file when missing.
     *
     * @param dataDirectory the folder that holds the JSON data files
     * @param gson          the shared configured Gson instance
     * @param sequences     the shared id sequence store
     * @throws StorageException when the file cannot be read or contains corrupt data
     */
    public JsonNotificationRepository(Path dataDirectory, Gson gson, SequenceStore sequences)
            throws StorageException {
        Path directory = JsonSupport.prepareDirectory(dataDirectory);
        this.file = directory.resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson, "gson is required");
        this.sequences = Objects.requireNonNull(sequences, "sequences are required");
        load();
    }

    /** Stores a notification that already carries an id from {@link #nextId()}. */
    @Override
    public synchronized void add(Notification notification) throws StorageException {
        if (notification == null) {
            throw new IllegalArgumentException("notification is required");
        }
        notifications.put(notification.getId(), notification);
        persist();
    }

    /**
     * Returns a user's notifications newest last (the order they were added), as a defensive copy.
     */
    @Override
    public synchronized List<Notification> findByUser(int userId) {
        List<Notification> result = new ArrayList<>();
        for (Notification notification : notifications.values()) {
            if (notification.getUserId() == userId) {
                result.add(notification);
            }
        }
        return result;
    }

    /** Returns how many notifications of the user are still unread. */
    @Override
    public synchronized int unreadCount(int userId) {
        int unread = 0;
        for (Notification notification : notifications.values()) {
            if (notification.getUserId() == userId && !notification.isRead()) {
                unread++;
            }
        }
        return unread;
    }

    /** Marks every notification of the user as read and persists the change immediately. */
    @Override
    public synchronized void markAllRead(int userId) throws StorageException {
        boolean changed = false;
        for (Notification notification : notifications.values()) {
            if (notification.getUserId() == userId && !notification.isRead()) {
                notification.setRead(true);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    /** Reserves the next notification id and persists it before returning. */
    @Override
    public int nextId() throws StorageException {
        return sequences.next("notification");
    }

    /** Reads the notification array, converting any parser failure into a StorageException. */
    private void load() throws StorageException {
        String content = JsonSupport.readOrCreate(file, EMPTY_ARRAY);
        notifications.clear();
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
                    throw new JsonSyntaxException("every notification entry must be a JSON object");
                }
                Notification notification = gson.fromJson(element, Notification.class);
                if (notification == null) {
                    throw new JsonSyntaxException("notification entry is null");
                }
                if (notifications.containsKey(notification.getId())) {
                    throw JsonSupport.corrupt(
                            file, "duplicate notification id " + notification.getId(), null);
                }
                notifications.put(notification.getId(), notification);
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw JsonSupport.corrupt(file, ex);
        }
    }

    /** Writes every notification atomically. */
    private void persist() throws StorageException {
        JsonArray array = new JsonArray();
        for (Notification notification : notifications.values()) {
            array.add(gson.toJsonTree(notification));
        }
        JsonSupport.writeAtomically(file, gson.toJson(array) + "\n");
    }
}
