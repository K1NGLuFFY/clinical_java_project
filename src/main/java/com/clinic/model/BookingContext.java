package com.clinic.model;

import java.time.LocalDateTime;

/**
 * Everything an appointment type needs to decide whether a slot is eligible for it.
 *
 * @param patientId                  patient asking for the appointment
 * @param doctorId                   requested doctor
 * @param slotStart                  requested start time
 * @param now                        current time from the injected clock
 * @param lastCompletedVisitDoctorId doctor of the most recent completed visit, or null
 * @param lastCompletedVisitAt       time of the most recent completed visit, or null
 */
public record BookingContext(
        int patientId,
        int doctorId,
        LocalDateTime slotStart,
        LocalDateTime now,
        Integer lastCompletedVisitDoctorId,
        LocalDateTime lastCompletedVisitAt) {
}
