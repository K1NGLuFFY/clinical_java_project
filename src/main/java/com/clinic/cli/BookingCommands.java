package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.Role;
import com.clinic.model.User;
import com.clinic.service.BillingService;
import com.clinic.util.Formats;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Appointment lifecycle commands that patients and receptionists use:
 * {@code book}, {@code reschedule}, {@code cancel} and {@code myappointments}.
 */
final class BookCommand extends CommandSupport {

    BookCommand() {
        super("book", PATIENT_OR_RECEPTIONIST,
                "book <doctorId> <dateTime> <CONSULTATION|FOLLOWUP|EMERGENCY>",
                "Book an appointment (receptionists also pass --patient=<id>)");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int doctorId = requireInt(command, 0, "doctorId");
        LocalDateTime start = requireDateTime(command, 1, "dateTime");
        AppointmentTypeCode type = AppointmentTypeCode.parse(
                requirePositional(command, 2, "CONSULTATION|FOLLOWUP|EMERGENCY"));
        int patientId;
        if (actor.getRole() == Role.PATIENT) {
            patientId = actor.getId();
        } else if (actor.getRole() == Role.RECEPTIONIST) {
            String rawPatient = command.flag("patient");
            if (rawPatient == null || rawPatient.isBlank()) {
                throw new InvalidInputException(
                        "Receptionists must pass --patient=<id>. Usage: " + usage());
            }
            patientId = parseInt(rawPatient, "patient");
        } else {
            throw new UnauthorizedActionException(
                    "This command is not available for the " + actor.getRole() + " role.");
        }
        Appointment appointment = ctx.booking().book(patientId, doctorId, start, type, actor);
        System.out.println("Booked appointment #" + appointment.getId() + ": "
                + patientName(ctx, appointment.getPatientId())
                + " with " + doctorDisplay(ctx, appointment.getDoctorId())
                + " on " + Formats.displayDate(appointment.getStart().toLocalDate())
                + " at " + appointmentWindow(appointment)
                + " (" + typeName(appointment.getType()) + ").");
        StringBuilder payment = new StringBuilder("Patient pays ")
                .append(Formats.money(appointment.getPatientShare()));
        BigDecimal insurerShare = appointment.getInsurerShare();
        if (insurerShare != null && insurerShare.signum() > 0) {
            payment.append(", insurer ").append(Formats.money(insurerShare));
        }
        payment.append('.');
        System.out.println(payment);
    }
}

/** Moves an appointment to a new date-time, keeping the same doctor and type. */
final class RescheduleCommand extends CommandSupport {

    RescheduleCommand() {
        super("reschedule", PATIENT_OR_RECEPTIONIST,
                "reschedule <appointmentId> <newDateTime>",
                "Move an existing appointment to a new date and time", "move");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        LocalDateTime newStart = requireDateTime(command, 1, "newDateTime");
        LocalDateTime previousStart = ctx.booking().findById(appointmentId)
                .map(Appointment::getStart).orElse(null);
        Appointment appointment = ctx.booking().reschedule(appointmentId, newStart, actor);
        String from = previousStart == null ? "?" : iso(previousStart);
        System.out.println("Rescheduled appointment #" + appointment.getId()
                + " from " + from + " to " + iso(appointment.getStart()) + ".");
    }
}

/** Cancels an appointment; the service decides whether the 20% late fee applies. */
final class CancelCommand extends CommandSupport {

    CancelCommand() {
        super("cancel", PATIENT_OR_RECEPTIONIST, "cancel <appointmentId> [--reason=\"...\"]",
                "Cancel an appointment and show any late cancellation fee");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        int appointmentId = requireInt(command, 0, "appointmentId");
        String reason = command.flag("reason");
        Appointment appointment = ctx.booking().cancel(appointmentId, reason, actor);
        BigDecimal fee = appointment.getLateCancellationFee();
        if (fee != null && fee.signum() > 0) {
            System.out.println("Cancelled appointment #" + appointment.getId()
                    + ". Late cancellation fee: " + Formats.money(fee)
                    + " (" + lateFeePercent() + "% of "
                    + Formats.money(appointment.getBaseFee()) + ").");
        } else {
            System.out.println("Cancelled appointment #" + appointment.getId() + ". No fee.");
        }
    }
}

/** Patients see their whole diary; doctors see today's schedule. */
final class MyAppointmentsCommand extends CommandSupport {

    MyAppointmentsCommand() {
        super("myappointments", PATIENT_OR_DOCTOR, "myappointments",
                "Patients: all your appointments. Doctors: today's schedule", "appointments");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User actor = user(ctx);
        if (actor instanceof Patient patient) {
            showPatientAppointments(ctx, patient);
        } else if (actor instanceof Doctor doctor) {
            showDoctorSchedule(ctx, doctor);
        } else {
            throw new UnauthorizedActionException(
                    "This command is not available for the " + actor.getRole() + " role.");
        }
    }

    private void showPatientAppointments(CommandContext ctx, Patient patient) throws ClinicException {
        List<Appointment> appointments =
                new ArrayList<>(ctx.booking().appointmentsForPatient(patient.getId(), patient));
        appointments.sort(Comparator.comparing(Appointment::getStart,
                Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())));
        if (appointments.isEmpty()) {
            System.out.println("You have no appointments.");
            return;
        }
        System.out.println("Your appointments (" + appointments.size() + "):");
        Table table = new Table("#", "When", "Doctor", "Type", "Status", "Patient pays")
                .rightAlign(5);
        for (Appointment appointment : appointments) {
            table.addRow("#" + appointment.getId(),
                    Formats.date(appointment.getStart().toLocalDate()) + " "
                            + appointmentWindow(appointment),
                    doctorDisplay(ctx, appointment.getDoctorId()),
                    typeName(appointment.getType()),
                    appointmentStatus(appointment),
                    patientPaid(appointment));
        }
        table.print();
    }

    /**
     * Amount the patient actually pays: a cancelled appointment is not charged its share, only
     * an optional late-cancellation fee is recorded separately, so the column shows 0.
     */
    private static String patientPaid(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return Formats.money(BigDecimal.ZERO);
        }
        return Formats.money(appointment.getPatientShare());
    }

    private void showDoctorSchedule(CommandContext ctx, Doctor doctor) throws ClinicException {
        LocalDate today = LocalDate.now(ctx.clock());
        List<Appointment> appointments = ctx.scheduling().dailySchedule(doctor.getId(), today);
        System.out.println("Today's schedule for " + doctor.getDisplayName()
                + " - " + Formats.displayDate(today));
        if (appointments == null || appointments.isEmpty()) {
            System.out.println("No appointments today.");
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
    }
}
