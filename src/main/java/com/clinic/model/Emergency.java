package com.clinic.model;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * An emergency visit: 20 minutes for a list price of 10000.
 *
 * <p>Emergency visits are the exception to several scheduling rules: they may be placed inside
 * the doctor's break, they do not count towards the doctor's daily capacity, they can only be
 * booked for the same day and they do not need the one hour minimum notice.</p>
 */
public final class Emergency extends AppointmentType {

    private static final Duration DURATION = Duration.ofMinutes(20);
    private static final BigDecimal BASE_FEE = new BigDecimal("10000");

    @Override
    public AppointmentTypeCode getCode() {
        return AppointmentTypeCode.EMERGENCY;
    }

    @Override
    public Duration getDuration() {
        return DURATION;
    }

    @Override
    public BigDecimal getBaseFee() {
        return BASE_FEE;
    }

    @Override
    public boolean mayUseBreak() {
        return true;
    }

    @Override
    public boolean ignoresDailyCapacity() {
        return true;
    }

    @Override
    public boolean sameDayOnly() {
        return true;
    }

    @Override
    public boolean requiresMinimumNotice() {
        return false;
    }
}
