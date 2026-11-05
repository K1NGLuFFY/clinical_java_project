package com.clinic.model;

import com.clinic.exception.FollowUpNotEligibleException;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Discriminator stored in JSON so the factory can rebuild the right subclass.
 *
 * <p>Duration, base fee and eligibility rules belong to the appointment type, not to the
 * booking service. Concrete types are stateless singletons rebuilt by
 * {@code AppointmentTypeFactory}.</p>
 */
public abstract class AppointmentType {

    public abstract AppointmentTypeCode getCode();

    public abstract Duration getDuration();

    public abstract BigDecimal getBaseFee();

    /** Emergency appointments may be placed inside a break window. */
    public boolean mayUseBreak() {
        return false;
    }

    /** Emergency appointments do not count towards the doctor's daily capacity. */
    public boolean ignoresDailyCapacity() {
        return false;
    }

    /** Emergency appointments can only be booked for today. */
    public boolean sameDayOnly() {
        return false;
    }

    /** Emergency appointments ignore the one hour minimum notice rule. */
    public boolean requiresMinimumNotice() {
        return true;
    }

    /**
     * Type specific eligibility rules. Follow-up appointments override this to require a
     * completed visit with the same doctor within the last 14 days.
     */
    public void checkEligibility(BookingContext context) throws FollowUpNotEligibleException {
        // standard types have no extra eligibility rules
    }

    public int getDurationMinutes() {
        return (int) getDuration().toMinutes();
    }

    public String getDisplayName() {
        return switch (getCode()) {
            case CONSULTATION -> "Consultation";
            case FOLLOWUP -> "Follow-up";
            case EMERGENCY -> "Emergency";
        };
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + getDurationMinutes() + " min, "
                + getBaseFee().stripTrailingZeros().toPlainString() + ")";
    }
}
