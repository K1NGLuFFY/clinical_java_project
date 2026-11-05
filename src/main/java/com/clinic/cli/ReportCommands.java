package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.model.Appointment;
import com.clinic.model.Patient;
import com.clinic.service.DailyScheduleReport;
import com.clinic.service.NoShowReport;
import com.clinic.service.RevenueReport;
import com.clinic.service.UtilisationReport;
import com.clinic.util.Formats;
import java.time.LocalDate;
import java.util.List;

/**
 * Receptionist reports: {@code report daily}, {@code report utilisation}, {@code report noshow}
 * and {@code report revenue}. All values come from {@link com.clinic.service.ReportService};
 * the commands only lay them out as aligned tables.
 */
final class ReportDailyCommand extends CommandSupport {

    ReportDailyCommand() {
        super("report daily", RECEPTIONIST_ONLY, "report daily <doctorId> <date>",
                "Print one doctor's appointment list for a day", "dailyreport");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        int doctorId = requireInt(command, 0, "doctorId");
        LocalDate date = requireDate(command, 1, "date");
        DailyScheduleReport report = ctx.reports().daily(doctorId, date);
        System.out.println("Daily schedule for " + report.doctor().getDisplayName()
                + " - " + Formats.displayDate(report.date()));
        List<Appointment> appointments = report.appointments();
        if (appointments == null || appointments.isEmpty()) {
            System.out.println("No appointments.");
            return;
        }
        Table table = new Table("#", "Time", "Patient", "Type", "Status");
        for (Appointment appointment : appointments) {
            table.addRow("#" + appointment.getId(),
                    appointmentWindow(appointment),
                    patientName(ctx, appointment.getPatientId()),
                    typeName(appointment.getType()),
                    appointmentStatus(appointment));
        }
        table.print();
        System.out.println("Appointments: " + appointments.size());
    }
}

/** Utilisation per doctor over a date range; defaults to the last seven days. */
final class ReportUtilisationCommand extends CommandSupport {

    private static final int DEFAULT_DAYS = 7;

    ReportUtilisationCommand() {
        super("report utilisation", RECEPTIONIST_ONLY,
                "report utilisation [--from=YYYY-MM-DD --to=YYYY-MM-DD]",
                "Booked versus available minutes per doctor (default: last 7 days)",
                "utilisation", "utilization");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        LocalDate to = command.flag("to") == null || command.flag("to").isBlank()
                ? LocalDate.now(ctx.clock()) : Formats.parseDate(command.flag("to"));
        LocalDate from = command.flag("from") == null || command.flag("from").isBlank()
                ? to.minusDays(DEFAULT_DAYS - 1L) : Formats.parseDate(command.flag("from"));
        requireOrdered(from, to);
        List<UtilisationReport> rows = ctx.reports().utilisation(from, to);
        System.out.println("Utilisation " + Formats.date(from) + " to " + Formats.date(to));
        if (rows == null || rows.isEmpty()) {
            System.out.println("No utilisation data for this period.");
            return;
        }
        Table table = new Table("Doctor", "Booked min", "Available min", "Utilisation")
                .rightAlign(1, 2, 3);
        for (UtilisationReport row : rows) {
            table.addRow(row.doctor().getDisplayName(), row.bookedMinutes(),
                    row.availableMinutes(), percent(row.utilisationPercent()));
        }
        table.print();
    }
}

/** No-show counts and rate per doctor plus the currently blocked patients; defaults to 90 days. */
final class ReportNoShowCommand extends CommandSupport {

    private static final int DEFAULT_DAYS = 90;

    ReportNoShowCommand() {
        super("report noshow", RECEPTIONIST_ONLY,
                "report noshow [--from=YYYY-MM-DD --to=YYYY-MM-DD]",
                "No-show counts, rates per doctor and blocked patients (default: last 90 days)",
                "noshows");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        LocalDate to = command.flag("to") == null || command.flag("to").isBlank()
                ? LocalDate.now(ctx.clock()) : Formats.parseDate(command.flag("to"));
        LocalDate from = command.flag("from") == null || command.flag("from").isBlank()
                ? to.minusDays(DEFAULT_DAYS - 1L) : Formats.parseDate(command.flag("from"));
        requireOrdered(from, to);
        NoShowReport report = ctx.reports().noShow(from, to);
        System.out.println("No-show report " + Formats.date(from) + " to " + Formats.date(to));
        Table summary = new Table("Metric", "Value").rightAlign(1);
        summary.addRow("Appointments", report.totalAppointments());
        summary.addRow("No-shows", report.noShows());
        summary.addRow("Overall no-show rate", percent(report.overallRatePercent()));
        summary.print();
        List<NoShowReport.DoctorNoShow> perDoctor = report.perDoctor();
        if (perDoctor != null && !perDoctor.isEmpty()) {
            System.out.println();
            System.out.println("Per doctor:");
            Table table = new Table("Doctor", "Appointments", "No-shows", "Rate")
                    .rightAlign(1, 2, 3);
            for (NoShowReport.DoctorNoShow row : perDoctor) {
                table.addRow(row.doctor().getDisplayName(), row.appointments(), row.noShows(),
                        percent(row.ratePercent()));
            }
            table.print();
        }
        List<Patient> blocked = report.blockedPatients();
        if (blocked != null && !blocked.isEmpty()) {
            System.out.println();
            System.out.println("Blocked patients:");
            Table table = new Table("Id", "Name", "Date of birth", "Category").rightAlign(0);
            for (Patient patient : blocked) {
                table.addRow(patient.getId(), patient.getName(),
                        Formats.date(patient.getDateOfBirth()),
                        patient.getCategory() == null ? "" : patient.getCategory().name());
            }
            table.print();
        }
    }
}

/** Money collected in a date range; defaults to the current month up to today. */
final class ReportRevenueCommand extends CommandSupport {

    ReportRevenueCommand() {
        super("report revenue", RECEPTIONIST_ONLY,
                "report revenue [--from=YYYY-MM-DD --to=YYYY-MM-DD]",
                "Patient fees, insurer share and late cancellation fees (default: this month)",
                "revenue");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        LocalDate to = command.flag("to") == null || command.flag("to").isBlank()
                ? LocalDate.now(ctx.clock()) : Formats.parseDate(command.flag("to"));
        LocalDate from = command.flag("from") == null || command.flag("from").isBlank()
                ? to.withDayOfMonth(1) : Formats.parseDate(command.flag("from"));
        requireOrdered(from, to);
        RevenueReport report = ctx.reports().revenue(from, to);
        System.out.println("Revenue " + Formats.date(from) + " to " + Formats.date(to));
        Table table = new Table("Metric", "Amount").rightAlign(1);
        table.addRow("Appointments", report.appointmentCount());
        table.addRow("Patient fees", Formats.money(report.patientFees()));
        table.addRow("Insurer share", Formats.money(report.insurerShare()));
        table.addRow("Late cancellation fees", Formats.money(report.lateCancellationFees()));
        table.addRow("Total collected", Formats.money(report.totalCollected()));
        table.print();
    }
}
