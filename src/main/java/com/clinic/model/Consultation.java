package com.clinic.model;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * A standard consultation: 30 minutes of the doctor's time for a list price of 5000.
 *
 * <p>The class is stateless apart from its immutable constants, so a single shared instance
 * is exposed by {@code com.clinic.factory.AppointmentTypeFactory}.</p>
 */
public final class Consultation extends AppointmentType {

    private static final Duration DURATION = Duration.ofMinutes(30);
    private static final BigDecimal BASE_FEE = new BigDecimal("5000");

    @Override
    public AppointmentTypeCode getCode() {
        return AppointmentTypeCode.CONSULTATION;
    }

    @Override
    public Duration getDuration() {
        return DURATION;
    }

    @Override
    public BigDecimal getBaseFee() {
        return BASE_FEE;
    }
}
