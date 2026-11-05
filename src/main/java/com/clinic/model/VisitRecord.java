package com.clinic.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A clinical note attached to an appointment. Records are append-only: a correction is stored
 * as a new amendment that points at the record it corrects.
 */
public class VisitRecord {

    private int id;
    private int appointmentId;
    private int patientId;
    private int doctorId;
    private String doctorName;
    private String notes;
    private String diagnosis;
    private List<String> prescriptions = new ArrayList<>();
    private LocalDateTime recordedAt;
    /** Id of the record this one amends; {@code null} for the original note. */
    private Integer amendmentOf;

    public VisitRecord() {
        // for the JSON mapper
    }

    public VisitRecord(int id, int appointmentId, int patientId, int doctorId, String doctorName,
                       String notes, String diagnosis, List<String> prescriptions,
                       LocalDateTime recordedAt, Integer amendmentOf) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.notes = notes;
        this.diagnosis = diagnosis;
        this.prescriptions = prescriptions == null ? new ArrayList<>() : new ArrayList<>(prescriptions);
        this.recordedAt = recordedAt;
        this.amendmentOf = amendmentOf;
    }

    public boolean isAmendment() {
        return amendmentOf != null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(int appointmentId) {
        this.appointmentId = appointmentId;
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

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public List<String> getPrescriptions() {
        return prescriptions;
    }

    public void setPrescriptions(List<String> prescriptions) {
        this.prescriptions = prescriptions;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Integer getAmendmentOf() {
        return amendmentOf;
    }

    public void setAmendmentOf(Integer amendmentOf) {
        this.amendmentOf = amendmentOf;
    }
}
