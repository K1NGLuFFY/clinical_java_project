package com.clinic.service;

import com.clinic.model.Appointment;
import com.clinic.model.Doctor;
import java.time.LocalDate;
import java.util.List;

/** Daily schedule of one doctor, ordered by start time. */
public record DailyScheduleReport(Doctor doctor, LocalDate date, List<Appointment> appointments) {
}