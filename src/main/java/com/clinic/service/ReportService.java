package com.clinic.service;

import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.InvalidInputException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.WorkingDay;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Receptionist reports: daily schedule, utilisation, no-show rate, revenue and blocked patients.
 *
 * <p>All percentages are rounded HALF_UP to one decimal and money is rounded HALF_UP to two
 * decimals. The current date comes from the injected {@link Clock}.</p>
 */
public class ReportService {

    private static final Set<AppointmentStatus> BILLABLE = Set.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW);

    private static final Set<AppointmentStatus> OCCUPYING = Set.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED);

    private static final Comparator<Appointment> BY_START =
            Comparator.comparing(Appointment::getStart, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final VisitRecordRepository visits;
    private final Clock clock;

    public ReportService(UserRepository users, AppointmentRepository appointments,
                         VisitRecordRepository visits, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.visits = visits;
        this.clock = clock;
    }

    /**
     * Daily schedule of one doctor, ordered by start time.
     *
     * @param doctorId doctor whose day is reported
     * @param date     requested date
     * @return the doctor's appointments for that day
     * @throws DoctorNotFoundException when no doctor has this id
     */
    public DailyScheduleReport daily(int doctorId, LocalDate date) throws DoctorNotFoundException {
        Objects.requireNonNull(date, "date");
        Doctor doctor = users.findDoctor(doctorId).orElseThrow(
                () -> new DoctorNotFoundException("No doctor with id " + doctorId + "."));
        List<Appointment> day = appointments.findByDoctorAndDate(doctorId, date).stream()
                .sorted(BY_START)
                .toList();
        return new DailyScheduleReport(doctor, date, day);
    }

    /**
     * Utilisation of every doctor over an inclusive date range.
     *
     * <p>Available minutes are the scheduled working hours minus the daily break for every day
     * the doctor works and is not on leave. Booked minutes are the durations of the
     * BOOKED / CHECKED_IN / COMPLETED appointments in the range.</p>
     *
     * @param from first date (inclusive)
     * @param to   last date (inclusive)
     * @return one row per doctor, ordered by doctor name
     * @throws InvalidInputException when the range is invalid
     * @throws ClinicException       when the data cannot be read
     */
    public List<UtilisationReport> utilisation(LocalDate from, LocalDate to) throws ClinicException {
        validateRange(from, to);
        List<Appointment> inRange = appointmentsInRange(from, to);
        List<Doctor> doctors = sortedDoctors();
        List<UtilisationReport> reports = new ArrayList<>(doctors.size());
        for (Doctor doctor : doctors) {
            int availableMinutes = availableMinutes(doctor, from, to);
            int bookedMinutes = 0;
            for (Appointment appointment : inRange) {
                if (appointment.getDoctorId() == doctor.getId()
                        && appointment.getStatus() != null
                        && OCCUPYING.contains(appointment.getStatus())) {
                    bookedMinutes += minutesOf(appointment);
                }
            }
            reports.add(new UtilisationReport(doctor, bookedMinutes, availableMinutes,
                    percent(bookedMinutes, availableMinutes)));
        }
        return reports;
    }

    /**
     * No-show statistics over an inclusive date range.
     *
     * <p>Cancelled appointments are excluded from the denominator; every other status counts.
     * The per-doctor rows only contain doctors with at least one appointment in the range.</p>
     *
     * @param from first date (inclusive)
     * @param to   last date (inclusive)
     * @return overall and per-doctor no-show rates plus the currently blocked patients
     * @throws InvalidInputException when the range is invalid
     * @throws ClinicException       when the data cannot be read
     */
    public NoShowReport noShow(LocalDate from, LocalDate to) throws ClinicException {
        validateRange(from, to);
        List<Appointment> considered = appointmentsInRange(from, to).stream()
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .toList();
        long total = considered.size();
        long noShows = considered.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.NO_SHOW)
                .count();

        Map<Integer, long[]> perDoctorCounts = new LinkedHashMap<>();
        for (Appointment appointment : considered) {
            long[] counts = perDoctorCounts.computeIfAbsent(appointment.getDoctorId(), key -> new long[2]);
            counts[0]++;
            if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
                counts[1]++;
            }
        }
        List<NoShowReport.DoctorNoShow> perDoctor = new ArrayList<>();
        for (Doctor doctor : sortedDoctors()) {
            long[] counts = perDoctorCounts.get(doctor.getId());
            if (counts == null) {
                continue;
            }
            perDoctor.add(new NoShowReport.DoctorNoShow(doctor, counts[0], counts[1],
                    percent(counts[1], counts[0])));
        }
        return new NoShowReport(from, to, total, noShows, percent(noShows, total),
                perDoctor, blockedPatients());
    }

    /**
     * Revenue over an inclusive date range.
     *
     * <p>Patient shares and insurer shares of BOOKED / CHECKED_IN / COMPLETED / NO_SHOW
     * appointments are summed; late cancellation fees are added on top. {@code totalCollected}
     * is what patients paid (patient shares plus late fees).</p>
     *
     * @param from first date (inclusive)
     * @param to   last date (inclusive)
     * @return the revenue breakdown
     * @throws InvalidInputException when the range is invalid
     * @throws ClinicException       when the data cannot be read
     */
    public RevenueReport revenue(LocalDate from, LocalDate to) throws ClinicException {
        validateRange(from, to);
        List<Appointment> inRange = appointmentsInRange(from, to);
        BigDecimal patientFees = BigDecimal.ZERO;
        BigDecimal insurerShare = BigDecimal.ZERO;
        BigDecimal lateFees = BigDecimal.ZERO;
        for (Appointment appointment : inRange) {
            if (appointment.getStatus() != null && BILLABLE.contains(appointment.getStatus())) {
                patientFees = patientFees.add(nullSafe(appointment.getPatientShare()));
                insurerShare = insurerShare.add(nullSafe(appointment.getInsurerShare()));
            }
            lateFees = lateFees.add(nullSafe(appointment.getLateCancellationFee()));
        }
        patientFees = money(patientFees);
        insurerShare = money(insurerShare);
        lateFees = money(lateFees);
        BigDecimal totalCollected = money(patientFees.add(lateFees));
        return new RevenueReport(from, to, inRange.size(), patientFees, insurerShare,
                lateFees, totalCollected);
    }

    /**
     * Every patient who is currently blocked, ordered by name.
     *
     * @return blocked patients
     */
    public List<Patient> blockedPatients() {
        return users.findAllPatients().stream()
                .filter(Patient::isBlocked)
                .sorted(Comparator.comparing(ReportService::nameOf, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * All patients, sorted by name (receptionist directory).
     *
     * @return every registered patient, ordered by name
     */
    public List<Patient> patients() {
        return users.findAllPatients().stream()
                .sorted(Comparator.comparing(ReportService::nameOf, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Patient lookup for display names.
     *
     * @param patientId patient id
     * @return the patient, or empty when unknown
     */
    public Optional<Patient> findPatient(int patientId) {
        return users.findPatient(patientId);
    }

    // ---------------------------------------------------------------- helpers

    private void validateRange(LocalDate from, LocalDate to) throws InvalidInputException {
        if (from == null || to == null) {
            throw new InvalidInputException("Both report dates are required.");
        }
        if (from.isAfter(to)) {
            throw new InvalidInputException(
                    "The start date " + from + " must not be after the end date " + to + ".");
        }
    }

    private List<Appointment> appointmentsInRange(LocalDate from, LocalDate to) {
        List<Appointment> stored = appointments.findBetween(from, to);
        if (stored == null) {
            return List.of();
        }
        return stored.stream()
                .filter(Objects::nonNull)
                .filter(appointment -> withinRange(appointment, from, to))
                .toList();
    }

    private static boolean withinRange(Appointment appointment, LocalDate from, LocalDate to) {
        if (appointment.getStart() == null) {
            return false;
        }
        LocalDate date = appointment.getStart().toLocalDate();
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private List<Doctor> sortedDoctors() {
        return users.findAllDoctors().stream()
                .sorted(Comparator.comparing(ReportService::nameOf, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static int availableMinutes(Doctor doctor, LocalDate from, LocalDate to) {
        int total = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!doctor.worksOn(date.getDayOfWeek()) || doctor.isOnLeave(date)) {
                continue;
            }
            Optional<WorkingDay> scheduled = doctor.workingDay(date.getDayOfWeek());
            if (scheduled.isEmpty()) {
                continue;
            }
            WorkingDay day = scheduled.get();
            if (day.getStart() == null || day.getEnd() == null || !day.getEnd().isAfter(day.getStart())) {
                continue;
            }
            int minutes = (int) Duration.between(day.getStart(), day.getEnd()).toMinutes();
            if (day.hasBreak() && day.getBreakEnd().isAfter(day.getBreakStart())) {
                minutes -= (int) Duration.between(day.getBreakStart(), day.getBreakEnd()).toMinutes();
            }
            total += Math.max(minutes, 0);
        }
        return total;
    }

    private static int minutesOf(Appointment appointment) {
        if (appointment.getStart() == null || appointment.getEnd() == null) {
            return 0;
        }
        return (int) Duration.between(appointment.getStart(), appointment.getEnd()).toMinutes();
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return nullSafe(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullSafe(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static String nameOf(Doctor doctor) {
        return doctor.getName() == null ? "" : doctor.getName();
    }

    private static String nameOf(Patient patient) {
        return patient.getName() == null ? "" : patient.getName();
    }
}
