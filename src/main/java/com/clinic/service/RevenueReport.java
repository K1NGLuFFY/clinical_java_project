package com.clinic.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Revenue for a date range. {@code totalCollected} is what patients paid (patient shares plus
 * late cancellation fees); the insurer share is invoiced separately.
 */
public record RevenueReport(LocalDate from, LocalDate to, long appointmentCount,
                            BigDecimal patientFees, BigDecimal insurerShare,
                            BigDecimal lateCancellationFees, BigDecimal totalCollected) {
}