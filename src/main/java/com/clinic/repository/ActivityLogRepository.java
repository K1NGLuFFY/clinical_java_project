package com.clinic.repository;

import com.clinic.exception.StorageException;

/** Append-only audit trail written to {@code data/activity.log}. */
public interface ActivityLogRepository {

    void append(String line) throws StorageException;
}