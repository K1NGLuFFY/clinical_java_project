package com.clinic.model;

import com.clinic.exception.FollowUpNotEligibleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A short follow-up visit: 15 minutes for a list price of 3000.
 *
 * <p>A follow-up is only allowed when the patient has a completed visit with the same doctor
 * no earlier than the requested slot and no more than 14 days before it.</p>
 */
public final class FollowUp extends AppointmentType {

    /** A follow-up must start within this many days of the completed visit. */
    public static final int ELIGIBILITY_DAYS = 14;

    private static final Duration DURATION = Duration.ofMinutes(15);
    private static final BigDecimal BASE_FEE = new BigDecimal("3000");

    @Override
    public AppointmentTypeCode getCode() {
        return AppointmentTypeCode.FOLLOWUP;
    }

    @Override
    public Duration getDuration() {
        return DURATION;
    }

    @Override
    public BigDecimal getBaseFee() {
        return BASE_FEE;
    }

    /**
     * Validates that a follow-up is eligible.
     *
     * @param context booking context carrying the most recent completed visit, its doctor and
     *                the requested slot start
     * @throws FollowUpNotEligibleException when there is no completed visit with this doctor,
     *                                      when the slot starts before that visit, or when the
     *                                      slot starts more than 14 days after it
     */
    @Override
    public void checkEligibility(BookingContext context) throws FollowUpNotEligibleException {
        if (context == null) {
            throw new FollowUpNotEligibleException("A follow-up needs a completed visit with the same doctor "
                    + "within 14 days.");
        }
        LocalDateTime lastVisitAt = context.lastCompletedVisitAt();
        Integer lastVisitDoctorId = context.lastCompletedVisitDoctorId();
        LocalDateTime slotStart = context.slotStart();
        if (lastVisitAt == null || lastVisitDoctorId == null || lastVisitDoctorId != context.doctorId()) {
            throw new FollowUpNotEligibleException("A follow-up needs a completed visit with the same doctor "
                    + "within 14 days.");
        }
        if (slotStart == null || slotStart.isBefore(lastVisitAt)) {
            throw new FollowUpNotEligibleException("A follow-up needs a completed visit with the same doctor "
                    + "within 14 days, before the requested slot.");
        }
        if (slotStart.isAfter(lastVisitAt.plusDays(ELIGIBILITY_DAYS))) {
            throw new FollowUpNotEligibleException("A follow-up needs a completed visit with the same doctor "
                    + "within 14 days; the last visit was on " + lastVisitAt.toLocalDate() + ".");
        }
    }
}
