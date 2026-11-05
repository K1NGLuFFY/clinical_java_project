package com.clinic.event;

import com.clinic.exception.StorageException;
import com.clinic.model.Notification;
import com.clinic.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Inbox listener: turns each event into one {@link Notification} per recipient.
 *
 * <p>Notifications are written synchronously when the event is published. A storage failure
 * is printed to stderr and swallowed: notifications are a convenience, never a reason to make
 * a booking fail.</p>
 */
public class NotificationInbox implements EventListener {

    private final NotificationRepository notifications;

    /**
     * @param notifications notification repository backing the inboxes
     */
    public NotificationInbox(NotificationRepository notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notification repository");
    }

    /**
     * Stores one notification for every recipient id of the event.
     *
     * @param event published clinic event
     */
    @Override
    public void onEvent(ClinicEvent event) {
        if (event == null) {
            return;
        }
        for (Integer userId : event.getRecipients()) {
            if (userId == null) {
                continue;
            }
            try {
                Notification notification = new Notification(
                        notifications.nextId(),
                        userId,
                        event.getMessage(),
                        event.getType().name(),
                        event.getOccurredAt());
                notifications.add(notification);
            } catch (StorageException ex) {
                System.err.println("[notifications] could not store notification for user " + userId
                        + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Returns the notifications of one user, in the order the repository stores them.
     *
     * @param userId owner of the inbox
     * @return notifications for the user, never {@code null}
     */
    public List<Notification> inbox(int userId) {
        List<Notification> stored = notifications.findByUser(userId);
        return stored == null ? new ArrayList<>() : stored;
    }

    /**
     * Number of unread notifications of one user.
     *
     * @param userId owner of the inbox
     * @return unread count
     */
    public int unreadCount(int userId) {
        return notifications.unreadCount(userId);
    }

    /**
     * Marks every notification of one user as read.
     *
     * @param userId owner of the inbox
     * @throws StorageException when the inbox cannot be persisted
     */
    public void markAllRead(int userId) throws StorageException {
        notifications.markAllRead(userId);
    }
}
