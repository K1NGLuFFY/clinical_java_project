package com.clinic.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Common base for every person who can log in.
 *
 * <p>Passwords are never stored in plain text: each user owns a SHA-256 hash and a
 * per-user salt. Lockout state is part of the user so it survives restarts.</p>
 */
public abstract class User {

    private int id;
    private String name;
    private String passwordHash;
    private String passwordSalt;
    private int failedLoginAttempts;
    private boolean locked;
    private LocalDateTime createdAt;

    protected User() {
        // no-arg constructor used by the JSON mapper
    }

    protected User(int id, String name, String passwordHash, String passwordSalt, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.createdAt = createdAt;
        this.locked = false;
        this.failedLoginAttempts = 0;
    }

    /** Role discriminator used by authentication and command authorisation. */
    public abstract Role getRole();

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /** Records one failed attempt and locks the account on the third consecutive failure. */
    public void recordFailedLogin(int maxAttempts) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            locked = true;
        }
    }

    public void resetFailedLogins() {
        failedLoginAttempts = 0;
    }

    /** Lifts a lock and clears the failed attempt counter. */
    public void unlock() {
        locked = false;
        failedLoginAttempts = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** Human readable label used by the console, e.g. {@code Dr. Bello}. */
    public String getDisplayName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getRole() + "#" + id + " " + name;
    }
}
