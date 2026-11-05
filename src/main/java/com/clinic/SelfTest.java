package com.clinic;

import com.clinic.exception.BookingLimitExceededException;
import com.clinic.exception.ClinicException;
import com.clinic.exception.DoctorOnLeaveException;
import com.clinic.exception.FollowUpNotEligibleException;
import com.clinic.exception.InvalidStatusTransitionException;
import com.clinic.exception.OutsideWorkingHoursException;
import com.clinic.exception.PatientBlockedException;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.factory.AppointmentTypeFactory;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentStatus;
import com.clinic.model.AppointmentType;
import com.clinic.model.AppointmentTypeCode;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.model.Receptionist;
import com.clinic.model.User;
import com.clinic.model.VisitRecord;
import com.clinic.scheduling.TimeRange;
import com.clinic.service.PricingQuote;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self-test harness that proves the important rules without a test framework (Gson must stay
 * the only third-party dependency). It opens a throwaway data folder with a fixed clock
 * (Monday 2 November 2026, 08:00), runs the scenarios and prints a PASS/FAIL line per check.
 *
 * <pre>
 * java -cp target/clinic-app.jar com.clinic.SelfTest
 * </pre>
 *
 * Exits with status 1 when any check fails.
 */
public final class SelfTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 11, 2, 8, 0);
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("clinic-selftest-");
        ZoneId zone = ZoneId.systemDefault();
        Clock clock = Clock.fixed(FIXED_NOW.atZone(zone).toInstant(), zone);
        ClinicApp app = ClinicApp.open(dataDirectory, clock);

        User receptionist = app.users.findAllReceptionists().get(0);
        Patient grace = app.users.findPatient(6).orElseThrow();
        Patient ibrahim = app.users.findPatient(7).orElseThrow();
        Patient chinelo = app.users.findPatient(8).orElseThrow();
        Patient tunde = app.users.findPatient(9).orElseThrow();
        Patient sarah = app.users.findPatient(10).orElseThrow();
        Patient david = app.users.findPatient(13).orElseThrow();
        Patient ngozi = app.users.findPatient(14).orElseThrow();
        Patient peter = app.users.findPatient(15).orElseThrow();
        Doctor bello = app.users.findDoctor(2).orElseThrow();
        Doctor okafor = app.users.findDoctor(3).orElseThrow();
        Doctor silva = app.users.findDoctor(5).orElseThrow();
        LocalDate today = FIXED_NOW.toLocalDate();

        // ---------------------------------------------------------- value objects
        TimeRange first = new TimeRange(today.atTime(10, 0), today.atTime(10, 30));
        TimeRange touching = new TimeRange(today.atTime(10, 30), today.atTime(11, 0));
        TimeRange overlap = new TimeRange(today.atTime(10, 15), today.atTime(10, 45));
        check("TimeRange: touching intervals do not overlap", !first.overlaps(touching));
        check("TimeRange: overlapping intervals are detected", first.overlaps(overlap));

        AppointmentType consultation = AppointmentTypeFactory.forCode(AppointmentTypeCode.CONSULTATION);
        AppointmentType followUp = AppointmentTypeFactory.forCode(AppointmentTypeCode.FOLLOWUP);
        AppointmentType emergency = AppointmentTypeFactory.forCode(AppointmentTypeCode.EMERGENCY);
        check("types: consultation 30 min / 5000",
                consultation.getDurationMinutes() == 30 && consultation.getBaseFee().intValue() == 5000);
        check("types: follow-up 15 min / 3000",
                followUp.getDurationMinutes() == 15 && followUp.getBaseFee().intValue() == 3000);
        check("types: emergency 20 min / 10000 and may use the break",
                emergency.getDurationMinutes() == 20 && emergency.getBaseFee().intValue() == 10000
                        && emergency.mayUseBreak() && emergency.ignoresDailyCapacity()
                        && emergency.sameDayOnly() && !emergency.requiresMinimumNotice());

        com.clinic.service.BillingService standaloneBilling = new com.clinic.service.BillingService(
                new com.clinic.factory.PricingPolicyFactory());
        PricingQuote standardQuote = standaloneBilling.quote(grace, AppointmentTypeCode.CONSULTATION, today);
        PricingQuote insuredQuote = standaloneBilling.quote(ibrahim, AppointmentTypeCode.CONSULTATION, today);
        PricingQuote seniorQuote = standaloneBilling.quote(tunde, AppointmentTypeCode.CONSULTATION, today);
        check("pricing: standard patient pays 5000", standardQuote.patientShare().intValue() == 5000);
        check("pricing: insured patient pays 1500, insurer 3500",
                insuredQuote.patientShare().intValue() == 1500 && insuredQuote.insurerShare().intValue() == 3500);
        check("pricing: senior patient pays 4000", seniorQuote.patientShare().intValue() == 4000);

        // ---------------------------------------------------------- booking rules
        LocalDateTime firstFree = app.scheduling.freeSlots(2, today, AppointmentTypeCode.CONSULTATION).get(0);
        Appointment booked = app.booking.book(ibrahim.getId(), bello.getId(), firstFree,
                AppointmentTypeCode.CONSULTATION, ibrahim);
        check("book: first free slot booked with a fee split",
                booked.getStatus() == AppointmentStatus.BOOKED
                        && booked.getPatientShare().intValue() == 1500
                        && booked.getInsurerShare().intValue() == 3500);
        expect("book: overlapping slot is rejected with suggestions", SlotUnavailableException.class,
                () -> app.booking.book(chinelo.getId(), bello.getId(), firstFree,
                        AppointmentTypeCode.CONSULTATION, chinelo));

        expect("book: normal appointment inside the break is rejected", OutsideWorkingHoursException.class,
                () -> app.booking.book(chinelo.getId(), bello.getId(), today.atTime(13, 0),
                        AppointmentTypeCode.CONSULTATION, chinelo));
        Appointment emergencyBooking = app.booking.book(chinelo.getId(), bello.getId(), today.atTime(13, 0),
                AppointmentTypeCode.EMERGENCY, chinelo);
        check("book: emergency may use the break", emergencyBooking.getType() == AppointmentTypeCode.EMERGENCY);

        LocalDate leaveDay = today.plusDays(21);
        expect("book: leave day is rejected", DoctorOnLeaveException.class,
                () -> app.booking.book(chinelo.getId(), silva.getId(), leaveDay.atTime(9, 0),
                        AppointmentTypeCode.CONSULTATION, chinelo));
        expect("book: past slot is rejected", SlotUnavailableException.class,
                () -> app.booking.book(chinelo.getId(), bello.getId(), today.minusDays(1).atTime(10, 0),
                        AppointmentTypeCode.CONSULTATION, chinelo));
        expect("book: more than 60 days ahead is rejected", SlotUnavailableException.class,
                () -> app.booking.book(chinelo.getId(), bello.getId(), today.plusDays(61).atTime(10, 0),
                        AppointmentTypeCode.CONSULTATION, chinelo));
        expect("book: less than one hour notice is rejected", SlotUnavailableException.class,
                () -> app.booking.book(chinelo.getId(), okafor.getId(), today.atTime(8, 45),
                        AppointmentTypeCode.CONSULTATION, chinelo));

        // follow-up eligibility (Grace completed a consultation with Dr. Bello 8 days ago)
        Appointment follow = app.booking.book(grace.getId(), bello.getId(), today.plusDays(1).atTime(9, 0),
                AppointmentTypeCode.FOLLOWUP, grace);
        check("follow-up: allowed within 14 days of a completed visit", follow.getType() == AppointmentTypeCode.FOLLOWUP);
        expect("follow-up: rejected without a completed visit with the same doctor",
                FollowUpNotEligibleException.class,
                () -> app.booking.book(peter.getId(), bello.getId(), today.plusDays(1).atTime(12, 0),
                        AppointmentTypeCode.FOLLOWUP, peter));

        // patient limit: Grace already holds the follow-up, add two more, the fourth fails
        app.booking.book(grace.getId(), bello.getId(), today.atTime(12, 0),
                AppointmentTypeCode.CONSULTATION, grace);
        expect("book: fourth active upcoming appointment is rejected", BookingLimitExceededException.class,
                () -> app.booking.book(grace.getId(), bello.getId(), today.atTime(11, 30),
                        AppointmentTypeCode.CONSULTATION, grace));

        // cancellation with late fee
        Appointment lateCancel = app.booking.cancel(booked.getId(), "changed plans", receptionist);
        check("cancel: late fee is 20% of the base fee",
                lateCancel.getStatus() == AppointmentStatus.CANCELLED
                        && lateCancel.getLateCancellationFee().intValue() == 1000);

        // reschedule: occupied target fails atomically, free target succeeds
        Appointment movable = app.booking.book(sarah.getId(), bello.getId(), today.plusDays(2).atTime(14, 0),
                AppointmentTypeCode.CONSULTATION, sarah);
        LocalDateTime before = movable.getStart();
        expect("reschedule: occupied target is rejected", SlotUnavailableException.class,
                () -> app.booking.reschedule(movable.getId(), today.plusDays(1).atTime(10, 0), sarah));
        check("reschedule: failed attempt left the original booking untouched",
                app.booking.findById(movable.getId()).orElseThrow().getStart().equals(before));
        Appointment moved = app.booking.reschedule(movable.getId(), today.plusDays(2).atTime(16, 0), sarah);
        check("reschedule: free target moved the appointment", moved.getStart().equals(today.plusDays(2).atTime(16, 0)));

        // no-show block after three no-shows within 90 days
        Optional<Appointment> pendingNoShow = app.appointments.findByPatient(tunde.getId()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED && a.getStart().isBefore(FIXED_NOW))
                .findFirst();
        check("seed: Tunde has a pending past appointment for the no-show demo", pendingNoShow.isPresent());
        if (pendingNoShow.isPresent()) {
            Appointment noShow = app.booking.markNoShow(pendingNoShow.get().getId(), receptionist);
            check("no-show: status is NO_SHOW", noShow.getStatus() == AppointmentStatus.NO_SHOW);
            Patient blocked = app.users.findPatient(tunde.getId()).orElseThrow();
            check("no-show: patient is blocked after 3 no-shows in 90 days", blocked.isBlocked());
            expect("no-show: blocked patient cannot book", PatientBlockedException.class,
                    () -> app.booking.book(tunde.getId(), bello.getId(), today.plusDays(3).atTime(10, 0),
                            AppointmentTypeCode.CONSULTATION, tunde));
            app.auth.unlock(tunde.getId(), receptionist);
            check("unblock: receptionist lifts the block", !app.users.findPatient(tunde.getId()).orElseThrow().isBlocked());
        }

        // waiting list offer after a cancellation
        var entry = app.waitlistService.join(ngozi.getId(), bello.getId(), today, today,
                com.clinic.model.TimeWindow.ANY, ngozi);
        Appointment cancelledForWaitlist = app.booking.cancel(
                app.appointments.findByDoctorAndDate(bello.getId(), today).stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.BOOKED
                                && a.getStart().equals(today.atTime(15, 30)))
                        .findFirst().orElseThrow().getId(),
                "freed for the waiting list", receptionist);
        var offered = app.waitlistService.findActiveOfferFor(bello.getId(), cancelledForWaitlist.getStart());
        check("waitlist: cancellation creates an offer for the first matching entry",
                offered.isPresent() && offered.get().getId() == entry.getId());
        Appointment fromOffer = app.waitlistService.accept(entry.getId(), ngozi);
        check("waitlist: accepting the offer books the slot",
                fromOffer.getStart().equals(cancelledForWaitlist.getStart())
                        && fromOffer.getPatientId() == ngozi.getId());

        // visit record and lifecycle guards
        Appointment visitAppointment = app.booking.book(david.getId(), bello.getId(), today.atTime(11, 0),
                AppointmentTypeCode.CONSULTATION, receptionist);
        expect("lifecycle: cannot complete a BOOKED appointment", InvalidStatusTransitionException.class,
                () -> app.booking.complete(visitAppointment.getId(), app.users.findDoctor(2).orElseThrow()));
        app.booking.checkIn(visitAppointment.getId(), receptionist);
        Optional<User> doctor = Optional.of(app.users.findDoctor(2).orElseThrow());
        VisitRecord record = app.visitService.addNote(visitAppointment.getId(), doctor.orElseThrow(),
                "Patient is stable.", "Hypertension", List.of("Amlodipine 5mg"));
        check("visits: note saved for a checked-in appointment", record.getId() > 0);
        app.booking.complete(visitAppointment.getId(), doctor.orElseThrow());
        check("lifecycle: checked-in appointment completed",
                app.booking.findById(visitAppointment.getId()).orElseThrow().getStatus() == AppointmentStatus.COMPLETED);

        // authentication hardening
        User stored = app.users.findPatient(7).orElseThrow();
        check("auth: password is stored as a salted hash, never plain text",
                stored.getPasswordHash() != null && !stored.getPasswordHash().contains("patient123")
                        && stored.getPasswordSalt() != null && !stored.getPasswordSalt().isBlank());

        // ---------------------------------------------------------- concurrency
        LocalDateTime contended = today.atTime(16, 0);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int patientId : new int[] {sarah.getId(), peter.getId()}) {
            pool.submit(() -> {
                try {
                    start.await();
                    app.booking.book(patientId, bello.getId(), contended, AppointmentTypeCode.CONSULTATION,
                            receptionist);
                    successes.incrementAndGet();
                } catch (SlotUnavailableException ex) {
                    rejections.incrementAndGet();
                } catch (Exception ex) {
                    failures.add("concurrency: unexpected " + ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
            failures.add("concurrency: threads did not finish");
        }
        check("concurrency: exactly one of two simultaneous bookings succeeds",
                successes.get() == 1 && rejections.get() == 1);

        // ---------------------------------------------------------- persistence
        long appointmentCount = app.appointments.findAll().size();
        int maxId = app.appointments.findAll().stream().mapToInt(Appointment::getId).max().orElse(0);
        check("events: activity log written", Files.exists(dataDirectory.resolve("activity.log"))
                && Files.size(dataDirectory.resolve("activity.log")) > 0);

        ClinicApp reopened = ClinicApp.open(dataDirectory, clock);
        check("restart: appointments survive a restart", reopened.appointments.findAll().size() == appointmentCount);
        check("restart: users survive a restart",
                reopened.users.findAll().size() == app.users.findAll().size());
        check("restart: inbox messages survive a restart", reopened.inbox.unreadCount(ibrahim.getId()) > 0);
        Appointment afterRestart = reopened.booking.book(peter.getId(), bello.getId(),
                today.plusDays(3).atTime(14, 0), AppointmentTypeCode.CONSULTATION, receptionist);
        check("restart: ids continue after the last saved value", afterRestart.getId() > maxId);

        // ---------------------------------------------------------- summary
        System.out.println();
        System.out.println("SelfTest finished: " + passed + " passed, " + failures.size() + " failed.");
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            failures.forEach(failure -> System.out.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("ALL SELF-TEST CHECKS PASSED");
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failures.add(label);
            System.out.println("FAIL  " + label);
        }
    }

    private static void expect(String label, Class<? extends Throwable> expected, ThrowingRunnable action) {
        try {
            action.run();
            failures.add(label + " (no exception thrown)");
            System.out.println("FAIL  " + label + " (no exception thrown)");
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                passed++;
                System.out.println("PASS  " + label + " -> " + thrown.getClass().getSimpleName()
                        + ": " + thrown.getMessage());
            } else {
                failures.add(label + " (expected " + expected.getSimpleName() + " but got "
                        + thrown.getClass().getSimpleName() + ": " + thrown.getMessage() + ")");
                System.out.println("FAIL  " + label + " (unexpected " + thrown.getClass().getSimpleName() + ")");
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
