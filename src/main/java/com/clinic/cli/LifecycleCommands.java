package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.User;
import com.clinic.util.Formats;

/**
 * The appointment lifecycle commands beyond booking: {@code checkin}, {@code complete} and
 * {@code noshow}. The state machine itself lives in the service layer.
 */
final class CheckInCommand extends CommandSupport {

    CheckInCommand() {
        super("checkin", RECEPTIONIST_ONLY, "checkin <appointmentId>",
                "Check a booked patient in on the day of the appointment", "check-in");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        Appointment appointment = ctx.booking().checkIn(appointmentId, actor);
        System.out.println("Checked in appointment #" + appointment.getId() + " ("
                + patientName(ctx, appointment.getPatientId()) + ") at "
                + Formats.time(appointment.getStart().toLocalTime()) + ".");
    }
}

/** A doctor closes a checked-in visit, which allows the visit note to be amended later. */
final class CompleteCommand extends CommandSupport {

    CompleteCommand() {
        super("complete", DOCTOR_ONLY, "complete <appointmentId>",
                "Mark a checked-in appointment as completed", "finish");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        Appointment appointment = ctx.booking().complete(appointmentId, actor);
        System.out.println("Completed appointment #" + appointment.getId()
                + ". The visit is now closed.");
    }
}

/** Receptionists mark a patient who did not turn up, which feeds the no-show block. */
final class NoShowCommand extends CommandSupport {

    NoShowCommand() {
        super("noshow", RECEPTIONIST_ONLY, "noshow <appointmentId>",
                "Mark a patient as a no-show for a past appointment", "no-show");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        Appointment appointment = ctx.booking().markNoShow(appointmentId, actor);
        System.out.println("Marked appointment #" + appointment.getId() + " as NO_SHOW ("
                + patientName(ctx, appointment.getPatientId()) + ").");
    }
}
