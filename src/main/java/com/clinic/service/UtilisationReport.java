package com.clinic.service;

import com.clinic.model.Doctor;
import java.math.BigDecimal;

/**
 * Utilisation of one doctor over a date range: booked minutes divided by the working minutes
 * the schedule makes available.
 */
public record UtilisationReport(Doctor doctor, int bookedMinutes, int availableMinutes,
                                BigDecimal utilisationPercent) {
}