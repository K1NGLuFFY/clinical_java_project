package com.clinic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * A patient. Blocking and the no-show history live here so both survive a restart.
 * The no-show history is kept forever, even after a receptionist lifts a block.
 */
public class Patient extends User {

    private LocalDate dateOfBirth;
    /** Registered category (STANDARD or INSURED). The SENIOR category is derived from age. */
    private PatientCategory category = PatientCategory.STANDARD;
    private String insurer;
    private boolean blocked;
    private LocalDateTime blockedAt;
    private List<LocalDateTime> noShowHistory = new ArrayList<>();

    public Patient() {
        super();
    }

    public Patient(int id, String name, LocalDate dateOfBirth, PatientCategory category, String insurer,
                   String passwordHash, String passwordSalt, LocalDateTime createdAt) {
        super(id, name, passwordHash, passwordSalt, createdAt);
        this.dateOfBirth = dateOfBirth;
        this.category = category;
        this.insurer = insurer;
    }

    @Override
    public Role getRole() {
        return Role.PATIENT;
    }

    public int ageOn(LocalDate date) {
        if (dateOfBirth == null) {
            return 0;
        }
        return Period.between(dateOfBirth, date).getYears();
    }

    /**
     * Category used by the pricing strategy: patients aged 65 or older are billed as SENIOR,
     * otherwise the registered category applies.
     */
    public PatientCategory pricingCategoryOn(LocalDate date) {
        if (ageOn(date) >= 65) {
            return PatientCategory.SENIOR;
        }
        return category == null ? PatientCategory.STANDARD : category;
    }

    public void recordNoShow(LocalDateTime when) {
        if (noShowHistory == null) {
            noShowHistory = new ArrayList<>();
        }
        noShowHistory.add(when);
    }

    /** Number of no-shows recorded at or after {@code since}. */
    public long noShowsSince(LocalDateTime since) {
        if (noShowHistory == null) {
            return 0;
        }
        return noShowHistory.stream().filter(when -> !when.isBefore(since)).count();
    }

    public void block(LocalDateTime when) {
        this.blocked = true;
        this.blockedAt = when;
    }

    public void unblock() {
        this.blocked = false;
        this.blockedAt = null;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public PatientCategory getCategory() {
        return category;
    }

    public void setCategory(PatientCategory category) {
        this.category = category;
    }

    public String getInsurer() {
        return insurer;
    }

    public void setInsurer(String insurer) {
        this.insurer = insurer;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public List<LocalDateTime> getNoShowHistory() {
        return noShowHistory;
    }

    public void setNoShowHistory(List<LocalDateTime> noShowHistory) {
        this.noShowHistory = noShowHistory;
    }
}
