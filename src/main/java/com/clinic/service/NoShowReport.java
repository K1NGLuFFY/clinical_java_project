package com.clinic.service;

import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** No-show statistics per doctor and overall, plus the currently blocked patients. */
public record NoShowReport(LocalDate from, LocalDate to, long totalAppointments, long noShows,
                           BigDecimal overallRatePercent, List<DoctorNoShow> perDoctor,
                           List<Patient> blockedPatients) {

    /** Per-doctor no-show row. */
    public record DoctorNoShow(Doctor doctor, long appointments, long noShows, BigDecimal ratePercent) {
    }
}