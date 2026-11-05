package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.User;
import com.clinic.model.VisitRecord;
import com.clinic.util.Formats;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Clinical commands: {@code visit note} (doctors only) and {@code history} (patient or the
 * doctor who treated them). Receptionists are refused by the service for history because they
 * must not read clinical notes.
 */
final class VisitNoteCommand extends CommandSupport {

    VisitNoteCommand() {
        super("visit note", DOCTOR_ONLY,
                "visit note <appointmentId> \"<text>\" [--diagnosis=<text>] [--rx=<item;item>]",
                "Record or amend the clinical note of a checked-in appointment", "note");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        String notes = requirePositional(command, 1, "notes");
        String diagnosis = command.flag("diagnosis");
        List<String> prescriptions = splitItems(command.flag("rx"));
        VisitRecord record = ctx.visits().addNote(appointmentId, actor, notes, diagnosis,
                prescriptions);
        String suffix = record.isAmendment()
                ? "(amendment of #" + record.getAmendmentOf() + ")"
                : "(original note)";
        System.out.println("Saved visit record #" + record.getId()
                + " for appointment #" + appointmentId + " " + suffix + ".");
    }
}

/** Shows the append-only clinical history of one patient. */
final class HistoryCommand extends CommandSupport {

    HistoryCommand() {
        super("history", PATIENT_OR_DOCTOR, "history <patientId>",
                "Show a patient's visit records (clinical notes)", "visits");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int patientId = requireInt(command, 0, "patientId");
        List<VisitRecord> records = new ArrayList<>(ctx.visits().historyFor(patientId, actor));
        records.sort(Comparator.comparing(VisitRecord::getRecordedAt,
                Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())));
        System.out.println("History for " + patientName(ctx, patientId)
                + " (" + records.size() + " record(s))");
        for (int index = 0; index < records.size(); index++) {
            VisitRecord record = records.get(index);
            System.out.println("#" + record.getId() + " "
                    + Formats.displayDateTime(record.getRecordedAt()) + "  "
                    + (record.getDoctorName() == null ? "" : record.getDoctorName()));
            if (record.getDiagnosis() != null && !record.getDiagnosis().isBlank()) {
                System.out.println("Diagnosis: " + record.getDiagnosis());
            }
            if (record.getNotes() != null && !record.getNotes().isBlank()) {
                System.out.println("Notes: " + record.getNotes());
            }
            if (record.getPrescriptions() != null && !record.getPrescriptions().isEmpty()) {
                System.out.println("Rx: " + String.join("; ", record.getPrescriptions()));
            }
            if (record.isAmendment()) {
                System.out.println("(amendment of #" + record.getAmendmentOf() + ")");
            }
            if (index < records.size() - 1) {
                System.out.println();
            }
        }
    }
}
