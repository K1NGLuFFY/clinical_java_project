package com.clinic.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event published by the services. {@code message} is the human readable sentence
 * stored in inboxes and in the activity log; {@code recipients} lists the user ids that must
 * receive it.
 */
public final class ClinicEvent {

    private final EventType type;
    private final LocalDateTime occurredAt;
    private final String message;
    private final Map<String, String> data;
    private final List<Integer> recipients;

    private ClinicEvent(EventType type, LocalDateTime occurredAt, String message,
                        Map<String, String> data, List<Integer> recipients) {
        this.type = Objects.requireNonNull(type, "type");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.message = message == null ? "" : message;
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }

    public static ClinicEvent of(EventType type, LocalDateTime occurredAt, String message,
                                 Map<String, String> data, List<Integer> recipients) {
        return new ClinicEvent(type, occurredAt, message, data, recipients);
    }

    public static ClinicEvent of(EventType type, LocalDateTime occurredAt, String message,
                                 Integer... recipients) {
        return new ClinicEvent(type, occurredAt, message, Map.of(), List.of(recipients));
    }

    public EventType getType() {
        return type;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getData() {
        return data;
    }

    public List<Integer> getRecipients() {
        return recipients;
    }

    public String getData(String key) {
        return data.get(key);
    }

    @Override
    public String toString() {
        return "ClinicEvent[" + type + " @ " + occurredAt + ": " + message + "]";
    }
}
