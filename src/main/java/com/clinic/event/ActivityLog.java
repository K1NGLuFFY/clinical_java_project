package com.clinic.event;

import com.clinic.exception.StorageException;
import com.clinic.repository.ActivityLogRepository;
import com.clinic.util.Formats;
import java.util.Objects;

/**
 * Audit-trail listener: every event is appended to the activity log as one line.
 *
 * <p>The format is {@code yyyy-MM-ddTHH:mm:ss | EVENT_TYPE | message}. A storage failure is
 * printed to stderr and swallowed so that a booked appointment is never rolled back because
 * the audit file could not be written.</p>
 */
public class ActivityLog implements EventListener {

    private final ActivityLogRepository log;

    /**
     * @param log append-only activity log repository
     */
    public ActivityLog(ActivityLogRepository log) {
        this.log = Objects.requireNonNull(log, "activity log repository");
    }

    /**
     * Appends one line for the event; failures are reported to stderr and never rethrown.
     *
     * @param event published clinic event
     */
    @Override
    public void onEvent(ClinicEvent event) {
        if (event == null) {
            return;
        }
        String line = Formats.dateTime(event.getOccurredAt()) + " | " + event.getType() + " | "
                + event.getMessage();
        try {
            log.append(line);
        } catch (StorageException ex) {
            System.err.println("[activity] could not append activity log entry: " + ex.getMessage());
        }
    }
}
