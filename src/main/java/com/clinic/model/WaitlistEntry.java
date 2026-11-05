package com.clinic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A patient waiting for a slot with a doctor inside a date range and optional time window.
 * Entries are offered cancelled slots first-in-first-out.
 */
public class WaitlistEntry {

    private int id;
    private int patientId;
    private int doctorId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private TimeWindow timeWindow = TimeWindow.ANY;
    private WaitlistStatus status = WaitlistStatus.WAITING;
    private LocalDateTime createdAt;
    private LocalDateTime offeredStart;
    private AppointmentTypeCode offeredType;
    private LocalDateTime offeredAt;
    private LocalDateTime offerExpiresAt;

    public WaitlistEntry() {
        // for the JSON mapper
    }

    public WaitlistEntry(int id, int patientId, int doctorId,
                         LocalDate fromDate, LocalDate toDate, TimeWindow timeWindow,
                         LocalDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.timeWindow = timeWindow == null ? TimeWindow.ANY : timeWindow;
        this.createdAt = createdAt;
        this.status = WaitlistStatus.WAITING;
    }

    /** True when a slot on {@code slotStart} is inside this entry's date range and time window. */
    public boolean matchesDateAndWindow(LocalDateTime slotStart) {
        LocalDate date = slotStart.toLocalDate();
        boolean inRange = !date.isBefore(fromDate) && !date.isAfter(toDate);
        return inRange && timeWindow.matches(slotStart.toLocalTime()) && status == WaitlistStatus.WAITING;
    }

    /** Starts an offer for the cancelled slot. The offer is held until {@code expiresAt}. */
    public void offer(LocalDateTime slotStart, AppointmentTypeCode type, LocalDateTime expiresAt,
                      LocalDateTime now) {
        this.status = WaitlistStatus.OFFERED;
        this.offeredStart = slotStart;
        this.offeredType = type;
        this.offeredAt = now;
        this.offerExpiresAt = expiresAt;
    }

    public void markOfferExpired() {
        this.status = WaitlistStatus.EXPIRED;
    }

    public void markAccepted() {
        this.status = WaitlistStatus.ACCEPTED;
    }

    public void markDeclined() {
        this.status = WaitlistStatus.DECLINED;
    }

    public void cancel() {
        this.status = WaitlistStatus.CANCELLED;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public int getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(int doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindow timeWindow) {
        this.timeWindow = timeWindow;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setStatus(WaitlistStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getOfferedStart() {
        return offeredStart;
    }

    public void setOfferedStart(LocalDateTime offeredStart) {
        this.offeredStart = offeredStart;
    }

    public AppointmentTypeCode getOfferedType() {
        return offeredType;
    }

    public void setOfferedType(AppointmentTypeCode offeredType) {
        this.offeredType = offeredType;
    }

    public LocalDateTime getOfferedAt() {
        return offeredAt;
    }

    public void setOfferedAt(LocalDateTime offeredAt) {
        this.offeredAt = offeredAt;
    }

    public LocalDateTime getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public void setOfferExpiresAt(LocalDateTime offerExpiresAt) {
        this.offerExpiresAt = offerExpiresAt;
    }
}
