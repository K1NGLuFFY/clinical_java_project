package com.clinic.scheduling;

import com.clinic.model.AppointmentTypeCode;
import java.time.LocalDateTime;

/**
 * One 15 minute cell of a doctor's day, rendered by the {@code slots} command.
 *
 * @param start           start time of the cell
 * @param state           what occupies the cell
 * @param appointmentId   appointment occupying the cell, or null
 * @param appointmentType type of the occupying appointment, or null
 */
public record SlotCell(LocalDateTime start, SlotState state, Integer appointmentId,
                       AppointmentTypeCode appointmentType) {

    public boolean isFree() {
        return state == SlotState.FREE;
    }
}
