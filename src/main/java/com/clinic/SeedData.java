package com.clinic;

import com.clinic.exception.ClinicException;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.PatientCategory;
import com.clinic.model.Receptionist;
import com.clinic.model.VisitRecord;
import com.clinic.model.WorkingDay;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import com.clinic.scheduling.TimeRange;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.factory.UserFactory;
import com.clinic.service.BillingService;
import com.clinic.service.PricingQuote;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the demo data set required by the brief on the first run:
 * one receptionist, four doctors with different schedules, ten patients (insured and senior
 * included) and sixteen appointments in various lifecycle states, three of them with visit
 * records so follow-up bookings can be demonstrated.
 *
 * <p>All dates are relative to the injected clock, so the demo stays valid whenever the
 * project is run. Seeding writes repositories directly (it is data import, not a booking
 * transaction) and therefore does not publish events.</p>
 */
public final class SeedData {

    public static final String RECEPTIONIST_PASSWORD = "admin123";
    public static final String DOCTOR_PASSWORD = "doctor123";
    public static final String PATIENT_PASSWORD = "patient123";

    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final VisitRecordRepository visits;
    private final BillingService billing;
    private final Clock clock;

    private final List<Appointment> placed = new ArrayList<>();
    private LocalDate today;
    private LocalDateTime now;

    public SeedData(UserRepository users, AppointmentRepository appointments, VisitRecordRepository visits,
                    BillingService billing, Clock clock) {
        this.users = users;
        this.appointments = appointments;
        this.visits = visits;
        this.billing = billing;
        this.clock = clock;
    }

    /** Seeds only when the data folder has no users yet. Returns true when data was created. */
    public boolean seedIfEmpty() throws ClinicException {
        if (!users.findAll().isEmpty() || !appointments.findAll().isEmpty()) {
            return false;
        }
        this.now = LocalDateTime.now(clock);
        this.today = now.toLocalDate();

        seedReceptionist();
        List<Doctor> doctors = seedDoctors();
        List<Patient> patients = seedPatients();
        seedAppointments(doctors, patients);
        return true;
    }

    private void seedReceptionist() throws ClinicException {
        Receptionist receptionist = UserFactory.newReceptionist(
                users.nextId(), "Amina Yusuf", RECEPTIONIST_PASSWORD, now);
        users.save(receptionist);
    }

    private List<Doctor> seedDoctors() throws ClinicException {
        List<Doctor> doctors = new ArrayList<>();

        Doctor bello = UserFactory.newDoctor(users.nextId(), "Bello", "General Practice", 20,
                DOCTOR_PASSWORD, now);
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            bello.setWorkingDay(new WorkingDay(day, LocalTime.of(9, 0), LocalTime.of(17, 0),
                    LocalTime.of(13, 0), LocalTime.of(14, 0)));
        }
        users.save(bello);
        doctors.add(bello);

        Doctor okafor = UserFactory.newDoctor(users.nextId(), "Okafor", "Pediatrics", 16,
                DOCTOR_PASSWORD, now);
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            okafor.setWorkingDay(new WorkingDay(day, LocalTime.of(8, 0), LocalTime.of(14, 0)));
        }
        users.save(okafor);
        doctors.add(okafor);

        Doctor chen = UserFactory.newDoctor(users.nextId(), "Chen", "Cardiology", 12,
                DOCTOR_PASSWORD, now);
        for (DayOfWeek day : List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)) {
            chen.setWorkingDay(new WorkingDay(day, LocalTime.of(10, 0), LocalTime.of(18, 0),
                    LocalTime.of(12, 30), LocalTime.of(13, 30)));
        }
        users.save(chen);
        doctors.add(chen);

        Doctor silva = UserFactory.newDoctor(users.nextId(), "Silva", "Dermatology", 10,
                DOCTOR_PASSWORD, now);
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)) {
            silva.setWorkingDay(new WorkingDay(day, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        }
        // Leave far enough away that the demo bookings are not affected.
        silva.addLeave(new com.clinic.model.LeavePeriod(today.plusDays(21), today.plusDays(23)));
        users.save(silva);
        doctors.add(silva);

        return doctors;
    }

    private List<Patient> seedPatients() throws ClinicException {
        List<Patient> patients = new ArrayList<>();
        patients.add(patient("Grace Adeyemi", today.minusYears(41), PatientCategory.STANDARD, null));
        patients.add(patient("Ibrahim Musa", today.minusYears(33), PatientCategory.INSURED, "AXA Health"));
        patients.add(patient("Chinelo Eze", today.minusYears(29), PatientCategory.STANDARD, null));
        patients.add(patient("Tunde Balogun", today.minusYears(71), PatientCategory.STANDARD, null));
        patients.add(patient("Sarah Klein", today.minusYears(67), PatientCategory.INSURED, "Bupa"));
        patients.add(patient("Emeka Nwosu", today.minusYears(46), PatientCategory.INSURED, "Reliance HMO"));
        patients.add(patient("Fatima Bello", today.minusYears(9), PatientCategory.STANDARD, null));
        patients.add(patient("David Osei", today.minusYears(54), PatientCategory.STANDARD, null));
        patients.add(patient("Ngozi Ibe", today.minusYears(62), PatientCategory.INSURED, "Hygeia"));
        patients.add(patient("Peter Adamu", today.minusYears(37), PatientCategory.STANDARD, null));
        return patients;
    }

    private Patient patient(String name, LocalDate dateOfBirth, PatientCategory category, String insurer)
            throws ClinicException {
        Patient patient = UserFactory.newPatient(users.nextId(), name, dateOfBirth, category, insurer,
                PATIENT_PASSWORD, now);
        users.save(patient);
        return patient;
    }

    private void seedAppointments(List<Doctor> doctors, List<Patient> patients) throws ClinicException {
        Doctor bello = doctors.get(0);
        Doctor okafor = doctors.get(1);
        Doctor chen = doctors.get(2);
        Doctor silva = doctors.get(3);

        Patient grace = patients.get(0);
        Patient ibrahim = patients.get(1);
        Patient chinelo = patients.get(2);
        Patient tunde = patients.get(3);
        Patient sarah = patients.get(4);
        Patient emeka = patients.get(5);
        Patient fatima = patients.get(6);
        Patient david = patients.get(7);
        Patient ngozi = patients.get(8);
        Patient peter = patients.get(9);

        // Completed visits (source of follow-up eligibility and clinical history).
        place(grace, bello, today.minusDays(8), LocalTime.of(10, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.COMPLETED, true);
        place(ibrahim, okafor, today.minusDays(6), LocalTime.of(9, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.COMPLETED, true);
        place(chinelo, chen, today.minusDays(5), LocalTime.of(11, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.COMPLETED, true);

        // No-show history: two recorded no-shows for Tunde plus one pending appointment from
        // yesterday that the demo receptionist can mark, which reaches the 3-in-90-days block.
        place(tunde, bello, today.minusDays(3), LocalTime.of(14, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.NO_SHOW, false);
        place(tunde, okafor, today.minusDays(6), LocalTime.of(8, 30), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.NO_SHOW, false);
        place(tunde, bello, today.minusDays(1), LocalTime.of(15, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);

        // Cancellations: one late (20% fee) and one free.
        Appointment late = place(sarah, bello, today.minusDays(2), LocalTime.of(15, 0),
                AppointmentTypeCode.CONSULTATION, AppointmentStatus.CANCELLED, false);
        late.setCancelledAt(late.getStart().minusHours(3));
        late.setLateCancellationFee(billing.lateCancellationFee(late.getType()));
        appointments.save(late);

        place(emeka, okafor, today.minusDays(4), LocalTime.of(10, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.CANCELLED, false);

        // Today's diary (demo: check-in, visit note, completion).
        place(fatima, bello, today, LocalTime.of(9, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(david, bello, today, LocalTime.of(15, 30), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);

        // Upcoming appointments.
        place(peter, bello, today.plusDays(1), LocalTime.of(10, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(ngozi, bello, today.plusDays(1), LocalTime.of(11, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(grace, chen, today.plusDays(3), LocalTime.of(14, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(ibrahim, silva, today.plusDays(4), LocalTime.of(9, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(chinelo, silva, today.plusDays(5), LocalTime.of(10, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(sarah, bello, today.plusDays(6), LocalTime.of(10, 30), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
        place(emeka, chen, today.plusDays(7), LocalTime.of(16, 0), AppointmentTypeCode.CONSULTATION,
                AppointmentStatus.BOOKED, false);
    }

    private Appointment place(Patient patient, Doctor doctor, LocalDate preferredDate, LocalTime preferredTime,
                              AppointmentTypeCode typeCode, AppointmentStatus status, boolean addVisit)
            throws ClinicException {
        AppointmentType type = AppointmentTypeFactory.forCode(typeCode);
        LocalDate date = resolveDate(doctor, preferredDate);
        LocalTime time = resolveTime(doctor, date, preferredTime, type);
        LocalDateTime start = date.atTime(time);
        LocalDateTime end = start.plus(type.getDuration());
        LocalDateTime createdAt = start.isBefore(now) ? start.minusHours(6) : now;

        Appointment appointment = new Appointment(appointments.nextId(), patient.getId(), doctor.getId(),
                typeCode, start, end, createdAt);
        PricingQuote quote = billing.quote(patient, typeCode, date);
        appointment.applyCharges(quote.baseFee(), quote.patientShare(), quote.insurerShare());
        appointment.setStatus(status);
        switch (status) {
            case COMPLETED -> appointment.setUpdatedAt(end);
            case CANCELLED -> {
                appointment.setCancelledAt(start.minusHours(30));
                appointment.setUpdatedAt(start.minusHours(30));
                appointment.setCancellationReason("Cancelled by the patient.");
            }
            case NO_SHOW -> {
                appointment.setUpdatedAt(start.plusMinutes(20));
                patient.recordNoShow(start.plusMinutes(20));
                users.save(patient);
            }
            default -> {
                // BOOKED / CHECKED_IN keep the creation timestamps
            }
        }
        appointments.save(appointment);
        placed.add(appointment);
        if (addVisit && status == AppointmentStatus.COMPLETED) {
            addVisit(patient, doctor, appointment, typeCode);
        }
        return appointment;
    }

    private void addVisit(Patient patient, Doctor doctor, Appointment appointment,
                          AppointmentTypeCode typeCode) throws ClinicException {
        String notes = switch (typeCode) {
            case CONSULTATION -> "Routine consultation. Vitals stable, no acute complaints.";
            case FOLLOWUP -> "Follow-up review. Symptoms improving since the previous visit.";
            case EMERGENCY -> "Emergency assessment. Treated and stabilised.";
        };
        String diagnosis = switch (typeCode) {
            case CONSULTATION -> "Mild hypertension";
            case FOLLOWUP -> "Improving hypertension";
            case EMERGENCY -> "Acute migraine";
        };
        List<String> prescriptions = switch (typeCode) {
            case CONSULTATION -> List.of("Amlodipine 5mg once daily", "Paracetamol 500mg as needed");
            case FOLLOWUP -> List.of("Amlodipine 5mg once daily");
            case EMERGENCY -> List.of("Sumatriptan 50mg as needed");
        };
        VisitRecord record = new VisitRecord(visits.nextId(), appointment.getId(), patient.getId(),
                doctor.getId(), "Dr. " + doctor.getName(), notes, diagnosis, prescriptions,
                appointment.getEnd(), null);
        visits.save(record);
    }

    private LocalDate resolveDate(Doctor doctor, LocalDate preferred) {
        if (worksOn(doctor, preferred)) {
            return preferred;
        }
        boolean past = preferred.isBefore(today);
        for (int i = 1; i <= 10; i++) {
            LocalDate forward = preferred.plusDays(i);
            if (past && forward.isAfter(today)) {
                break;
            }
            if (worksOn(doctor, forward)) {
                return forward;
            }
        }
        for (int i = 1; i <= 14; i++) {
            LocalDate backward = preferred.minusDays(i);
            if (worksOn(doctor, backward)) {
                return backward;
            }
        }
        return preferred;
    }

    private boolean worksOn(Doctor doctor, LocalDate date) {
        return doctor.worksOn(date.getDayOfWeek()) && !doctor.isOnLeave(date);
    }

    private LocalTime resolveTime(Doctor doctor, LocalDate date, LocalTime preferred, AppointmentType type) {
        WorkingDay workingDay = doctor.workingDay(date.getDayOfWeek())
                .orElseThrow(() -> new IllegalStateException("No working day for " + date));
        LocalTime latestStart = workingDay.getEnd().minus(type.getDuration());
        LocalTime candidate = alignUp(preferred.isBefore(workingDay.getStart()) ? workingDay.getStart() : preferred);
        for (int attempt = 0; attempt < 200; attempt++) {
            if (candidate.isAfter(latestStart)) {
                break;
            }
            TimeRange range = new TimeRange(date.atTime(candidate), date.atTime(candidate).plus(type.getDuration()));
            if (fitsBreak(workingDay, range, type) && !overlapsPlaced(doctor, date, range)) {
                return candidate;
            }
            candidate = candidate.plusMinutes(15);
        }
        throw new IllegalStateException("Could not find a free seed slot for " + doctor.getName() + " on " + date);
    }

    private LocalTime alignUp(LocalTime time) {
        int minute = time.getMinute();
        int remainder = minute % 15;
        if (remainder == 0) {
            return time.withSecond(0).withNano(0);
        }
        return time.plusMinutes(15 - remainder).withSecond(0).withNano(0);
    }

    private boolean fitsBreak(WorkingDay workingDay, TimeRange range, AppointmentType type) {
        if (!workingDay.hasBreak() || type.mayUseBreak()) {
            return true;
        }
        TimeRange breakRange = new TimeRange(workingDay.breakStartOn(range.getStart().toLocalDate()),
                workingDay.breakEndOn(range.getStart().toLocalDate()));
        return !range.overlaps(breakRange);
    }

    private boolean overlapsPlaced(Doctor doctor, LocalDate date, TimeRange range) {
        for (Appointment appointment : placed) {
            if (appointment.getDoctorId() != doctor.getId()) {
                continue;
            }
            if (!appointment.getStart().toLocalDate().equals(date)) {
                continue;
            }
            if (!appointment.getStatus().isActive() && appointment.getStatus() != AppointmentStatus.COMPLETED) {
                continue;
            }
            if (appointment.overlaps(range.getStart(), range.getEnd())) {
                return true;
            }
        }
        return false;
    }
}
