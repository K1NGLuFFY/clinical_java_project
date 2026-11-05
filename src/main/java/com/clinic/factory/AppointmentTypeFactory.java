package com.clinic.factory;

import com.clinic.exception.InvalidInputException;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Consultation;
import com.clinic.model.Emergency;
import com.clinic.model.FollowUp;
import java.util.List;
import java.util.Objects;

/**
 * Entry point for appointment types.
 *
 * <p>Every {@link AppointmentType} implementation is stateless, so the factory keeps a single
 * shared instance of each one instead of allocating a new object per booking.</p>
 */
public final class AppointmentTypeFactory {

    private static final AppointmentType CONSULTATION = new Consultation();
    private static final AppointmentType FOLLOW_UP = new FollowUp();
    private static final AppointmentType EMERGENCY = new Emergency();

    private AppointmentTypeFactory() {
    }

    /**
     * Returns the shared appointment type for a stored code.
     *
     * @param code appointment type code
     * @return the singleton type instance, never {@code null}
     */
    public static AppointmentType forCode(AppointmentTypeCode code) {
        Objects.requireNonNull(code, "Appointment type code is required.");
        return switch (code) {
            case CONSULTATION -> CONSULTATION;
            case FOLLOWUP -> FOLLOW_UP;
            case EMERGENCY -> EMERGENCY;
        };
    }

    /**
     * Parses a user supplied appointment type name.
     *
     * @param raw name typed by the user, for example {@code "follow-up"}
     * @return the matching appointment type
     * @throws InvalidInputException when the name is missing or not a known type
     */
    public static AppointmentType parse(String raw) throws InvalidInputException {
        return forCode(AppointmentTypeCode.parse(raw));
    }

    /**
     * All known appointment types in display order.
     *
     * @return an immutable list ordered CONSULTATION, FOLLOWUP, EMERGENCY
     */
    public static List<AppointmentType> all() {
        return List.of(CONSULTATION, FOLLOW_UP, EMERGENCY);
    }
}
