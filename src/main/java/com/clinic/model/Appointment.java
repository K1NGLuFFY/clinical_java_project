package com.clinic.model;

import com.clinic.exception.InvalidStatusTransitionException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A booked slot of a doctor's diary.
 *
 * <p>{@code end} is derived from the appointment type duration and stored so the diary can
 * be rendered without rebuilding type objects. Status changes are validated here, which keeps
 * the lifecycle rules in one place and out of the CLI and services.</p>
 */
public class Appointment {

    private int id;
    private int patientId;
    private int doctorId;
    private AppointmentTypeCode type;
    private LocalDateTime start;
    private LocalDateTime end;
    private AppointmentStatus status = AppointmentStatus.BOOKED;
    private BigDecimal baseFee = BigDecimal.ZERO;
    private BigDecimal patientShare = BigDecimal.ZERO;
    private BigDecimal insurerShare = BigDecimal.ZERO;
    private BigDecimal lateCancellationFee = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    public Appointment() {
        // for the JSON mapper
    }

    public Appointment(int id, int patientId, int doctorId, AppointmentTypeCode type,
                       LocalDateTime start, LocalDateTime end, LocalDateTime createdAt) {
        this.id = id;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.type = type;
        this.start = start;
        this.end = end;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = AppointmentStatus.BOOKED;
    }

    /** Applies a status change, refusing every transition that is not part of the lifecycle. */
    public void transitionTo(AppointmentStatus target, LocalDateTime at) throws InvalidStatusTransitionException {
        Objects.requireNonNull(target, "target status");
        if (!isAllowedTransition(target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot change appointment #" + id + " from " + status + " to " + target + ".");
        }
        this.status = target;
        this.updatedAt = at;
        if (target == AppointmentStatus.CANCELLED) {
            this.cancelledAt = at;
        }
    }

    private boolean isAllowedTransition(AppointmentStatus target) {
        return switch (status) {
            case BOOKED -> target == AppointmentStatus.CHECKED_IN
                    || target == AppointmentStatus.CANCELLED
                    || target == AppointmentStatus.NO_SHOW;
            case CHECKED_IN -> target == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    /** Half-open interval overlap: touching appointments do not overlap. */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public boolean isActive() {
        return status != null && status.isActive();
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public void applyCharges(BigDecimal baseFee, BigDecimal patientShare, BigDecimal insurerShare) {
        this.baseFee = baseFee;
        this.patientShare = patientShare;
        this.insurerShare = insurerShare;
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

    public AppointmentTypeCode getType() {
        return type;
    }

    public void setType(AppointmentTypeCode type) {
        this.type = type;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public void setBaseFee(BigDecimal baseFee) {
        this.baseFee = baseFee;
    }

    public BigDecimal getPatientShare() {
        return patientShare;
    }

    public void setPatientShare(BigDecimal patientShare) {
        this.patientShare = patientShare;
    }

    public BigDecimal getInsurerShare() {
        return insurerShare;
    }

    public void setInsurerShare(BigDecimal insurerShare) {
        this.insurerShare = insurerShare;
    }

    public BigDecimal getLateCancellationFee() {
        return lateCancellationFee;
    }

    public void setLateCancellationFee(BigDecimal lateCancellationFee) {
        this.lateCancellationFee = lateCancellationFee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Appointment appointment)) {
            return false;
        }
        return id == appointment.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
