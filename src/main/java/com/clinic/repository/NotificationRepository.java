package com.clinic.repository;

import com.clinic.exception.StorageException;
import com.clinic.model.Notification;
import java.util.List;

/** Persistence boundary for notification inboxes. */
public interface NotificationRepository {

    void add(Notification notification) throws StorageException;

    List<Notification> findByUser(int userId);

    int unreadCount(int userId);

    void markAllRead(int userId) throws StorageException;

    int nextId() throws StorageException;
}