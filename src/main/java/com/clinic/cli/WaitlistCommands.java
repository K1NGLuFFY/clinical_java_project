package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.Patient;
import com.clinic.model.TimeWindow;
import com.clinic.model.WaitlistEntry;
import com.clinic.util.Formats;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Patient waiting-list commands: {@code waitlist join}, {@code accept}, {@code leave},
 * {@code decline} and {@code list}.
 */
final class WaitlistJoinCommand extends CommandSupport {

    WaitlistJoinCommand() {
        super("waitlist join", PATIENT_ONLY,
                "waitlist join <doctorId> <fromDate> <toDate> [--time=MORNING|AFTERNOON|ANY]",
                "Ask to be offered the first matching free slot", "wl join");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        Patient patient = ctx.auth().requirePatient(user(ctx));
        int doctorId = requireInt(command, 0, "doctorId");
        LocalDate from = requireDate(command, 1, "fromDate");
        LocalDate to = requireDate(command, 2, "toDate");
        TimeWindow window = parseWindow(command.flag("time"));
        WaitlistEntry entry = ctx.waitlist().join(patient.getId(), doctorId, from, to, window, patient);
        System.out.println("Joined the waiting list as entry #" + entry.getId() + ": "
                + doctorDisplay(ctx, entry.getDoctorId()) + ", "
                + Formats.date(entry.getFromDate()) + " to " + Formats.date(entry.getToDate())
                + ", time window " + entry.getTimeWindow().name() + ".");
    }
}

/** Confirms the active offer of a waiting-list entry and books the held slot. */
final class WaitlistAcceptCommand extends CommandSupport {

    WaitlistAcceptCommand() {
        super("waitlist accept", PATIENT_ONLY, "waitlist accept <entryId>",
                "Accept an offered slot and book it", "wl accept");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        Patient patient = ctx.auth().requirePatient(user(ctx));
        int entryId = requireInt(command, 0, "entryId");
        Appointment appointment = ctx.waitlist().accept(entryId, patient);
        System.out.println("Accepted waiting-list entry #" + entryId
                + ". Booked appointment #" + appointment.getId()
                + " with " + doctorDisplay(ctx, appointment.getDoctorId())
                + " on " + Formats.displayDate(appointment.getStart().toLocalDate())
                + " at " + appointmentWindow(appointment) + ".");
    }
}

/** Removes a waiting-list entry that has not been offered a slot yet. */
final class WaitlistLeaveCommand extends CommandSupport {

    WaitlistLeaveCommand() {
        super("waitlist leave", PATIENT_ONLY, "waitlist leave <entryId>",
                "Leave the waiting list (cancel your entry)", "wl leave");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        Patient patient = ctx.auth().requirePatient(user(ctx));
        int entryId = requireInt(command, 0, "entryId");
        ctx.waitlist().leave(entryId, patient);
        System.out.println("Waiting-list entry #" + entryId + " has been cancelled.");
    }
}

/** Declines an active offer so the slot is offered to the next waiting patient. */
final class WaitlistDeclineCommand extends CommandSupport {

    WaitlistDeclineCommand() {
        super("waitlist decline", PATIENT_ONLY, "waitlist decline <entryId>",
                "Decline an offered slot so it goes to the next patient", "wl decline");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        Patient patient = ctx.auth().requirePatient(user(ctx));
        int entryId = requireInt(command, 0, "entryId");
        ctx.waitlist().decline(entryId, patient);
        System.out.println("Declined the offer on waiting-list entry #" + entryId + ".");
    }
}

/** Lists the patient's own waiting-list entries. */
final class WaitlistListCommand extends CommandSupport {

    WaitlistListCommand() {
        super("waitlist list", PATIENT_ONLY, "waitlist list",
                "List your waiting-list entries and any active offer", "wl list", "waitlist");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        Patient patient = ctx.auth().requirePatient(user(ctx));
        List<WaitlistEntry> entries = new ArrayList<>(ctx.waitlist().listForPatient(patient.getId()));
        if (entries.isEmpty()) {
            System.out.println("You are not on any waiting list.");
            return;
        }
        entries.sort(Comparator.comparing(WaitlistEntry::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        System.out.println("Your waiting-list entries (" + entries.size() + "):");
        Table table = new Table("#", "Doctor", "Dates", "Window", "Status", "Offer");
        for (WaitlistEntry entry : entries) {
            String offer = entry.getOfferedStart() == null
                    ? ""
                    : Formats.displayDateTime(entry.getOfferedStart())
                            + (entry.getOfferExpiresAt() == null
                                    ? "" : " until " + Formats.displayDateTime(entry.getOfferExpiresAt()));
            table.addRow("#" + entry.getId(),
                    doctorDisplay(ctx, entry.getDoctorId()),
                    Formats.date(entry.getFromDate()) + " to " + Formats.date(entry.getToDate()),
                    entry.getTimeWindow().name(),
                    entry.getStatus().name(),
                    offer);
        }
        table.print();
    }
}
