package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.User;
import com.clinic.model.WaitlistEntry;

/**
 * Narrow port the waiting list uses to confirm an accepted offer. Implemented by
 * {@link BookingService}; the setter wiring in {@code Main} breaks the otherwise cyclic
 * dependency between booking and waiting list.
 */
public interface BookingGateway {

    /** Books the slot that was reserved by an accepted offer and marks the entry accepted. */
    Appointment bookReservedSlot(WaitlistEntry entry, User actor) throws ClinicException;
}