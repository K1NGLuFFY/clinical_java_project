package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.User;
import com.clinic.service.CreatedAccount;
import com.clinic.util.Formats;
import java.time.LocalDate;
import java.util.List;

/**
 * Receptionist patient commands: {@code patient add}, {@code patient unblock} and
 * {@code patient list}.
 */
final class PatientAddCommand extends CommandSupport {

    PatientAddCommand() {
        super("patient add", RECEPTIONIST_ONLY,
                "patient add \"<name>\" <dob> <STANDARD|INSURED> [--insurer=<name>]",
                "Register a patient and print the generated initial password", "addpatient");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        List<String> positionals = command.positionals();
        if (positionals.size() < 3) {
            throw new InvalidInputException(
                    "Missing required arguments <name> <dob> <STANDARD|INSURED>. Usage: " + usage());
        }
        String categoryRaw = positionals.get(positionals.size() - 1);
        String dateOfBirthRaw = positionals.get(positionals.size() - 2);
        String name = String.join(" ", positionals.subList(0, positionals.size() - 2)).trim();
        if (name.isEmpty()) {
            throw new InvalidInputException("The patient name must not be empty. Usage: " + usage());
        }
        LocalDate dateOfBirth = Formats.parseDate(dateOfBirthRaw);
        PatientCategory category = parseCategory(categoryRaw);
        String insurer = command.flag("insurer");
        if (category == PatientCategory.INSURED && (insurer == null || insurer.isBlank())) {
            throw new InvalidInputException(
                    "Insured patients need an insurer: add --insurer=<name>. Usage: " + usage());
        }
        CreatedAccount account = ctx.auth().createPatient(name, dateOfBirth, category, insurer, actor);
        System.out.println("Created patient account #" + account.user().getId() + ": "
                + account.user().getDisplayName() + " (" + category + ").");
        System.out.println("Initial password: " + account.initialPassword());
        System.out.println("Please note this password now - it is shown only once.");
    }
}

/** Lifts the block/lock of a patient account so the patient can book again. */
final class PatientUnblockCommand extends CommandSupport {

    PatientUnblockCommand() {
        super("patient unblock", RECEPTIONIST_ONLY, "patient unblock <patientId>",
                "Remove a patient's block or locked account", "unblock");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int patientId = requireInt(command, 0, "patientId");
        ctx.auth().unlock(patientId, actor);
        System.out.println("Patient #" + patientId + " (" + patientName(ctx, patientId)
                + ") has been unblocked.");
    }
}

/** Lists every registered patient with category, insurer and block state. */
final class PatientListCommand extends CommandSupport {

    PatientListCommand() {
        super("patient list", RECEPTIONIST_ONLY, "patient list",
                "List every registered patient", "patients");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) {
        List<Patient> patients = ctx.reports().patients();
        if (patients == null || patients.isEmpty()) {
            System.out.println("No patients registered.");
            return;
        }
        System.out.println("Patients (" + patients.size() + "):");
        Table table = new Table("Id", "Name", "Date of birth", "Category", "Insurer", "Status")
                .rightAlign(0);
        for (Patient patient : patients) {
            table.addRow(patient.getId(),
                    patient.getName(),
                    Formats.date(patient.getDateOfBirth()),
                    patient.getCategory() == null ? "" : patient.getCategory().name(),
                    patient.getInsurer() == null || patient.getInsurer().isBlank()
                            ? "-" : patient.getInsurer(),
                    patient.isBlocked() ? "BLOCKED" : "active");
        }
        table.print();
    }
}
