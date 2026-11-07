"""Build the project documentation DOCX from the implementation in this repository."""
from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
IMAGES = os.path.join(PROJECT, "docs", "images")
OUT = os.path.join(PROJECT, "docs", "Clinic_Appointment_System_Documentation.docx")

sys.path.insert(0, HERE)
import docx_helpers as H  # noqa: E402


def image(name: str) -> str:
    return os.path.join(IMAGES, name)


def build() -> None:
    doc = H.setup_document()
    H.set_properties(doc,
                     "Console Clinic Appointment System - Project Documentation",
                     "Design, implementation and verification of a Java console clinic appointment system",
                     "Clinic Appointment System project")
    H.add_header_footer(doc, "Console Clinic Appointment System")
    team = [
        ("Onah Debeiwu Chrysanctus", "Student1639236"),
        ("Okeke Chiemerie Miracle", "Student1647235"),
        ("[NAME PENDING]", "Student1627019"),
    ]
    H.add_cover(doc, [
        ("Course", "[Course / Module]"),
        ("Instructor", "[Instructor]"),
        ("Submission", "[Submission date]"),
        ("Version", "1.0"),
        ("Repository", "clinical_java_project"),
    ], team=team)
    H.add_toc(doc, [
        ("1", "Introduction"),
        ("2", "Requirements"),
        ("3", "System architecture"),
        ("4", "Domain model"),
        ("5", "Scheduling engine"),
        ("6", "Booking and lifecycle management"),
        ("7", "Pricing and billing"),
        ("8", "Waiting list"),
        ("9", "Visit records and patient history"),
        ("10", "Events, notifications and audit trail"),
        ("11", "Reports"),
        ("12", "Persistence and data integrity"),
        ("13", "Command-line interface"),
        ("14", "Testing and verification"),
        ("15", "Design patterns and OOP principles"),
        ("16", "Quality, limitations and future work"),
        ("17", "Build, run and demo"),
        ("A", "Appendix A - Package and class inventory"),
        ("B", "Appendix B - Persisted JSON examples"),
        ("C", "Appendix C - Verification log excerpts"),
    ])

    # ------------------------------------------------------------------ 1 introduction
    H.heading(doc, "1  Introduction")
    H.add_table(doc, ["Team member", "Student ID"],
                [[name, student_id] for name, student_id in team],
                widths=[9.0, 7.0], font_size=9.5)
    H.heading(doc, "1.1  Background", 2)
    H.para(doc, "Small clinics often book patients by telephone and record the appointments in a paper diary "
                "or a shared spreadsheet. That works for a handful of patients a day, but growth exposes the "
                "same problems again and again: patients are double-booked with one doctor, appointments are "
                "placed during lunch or on a doctor's leave day, cancelled slots stay empty, patients who "
                "repeatedly fail to attend are not tracked, fees are calculated differently at every desk, and "
                "clinical notes end up scattered across files. The clinic has no reliable view of a doctor's day "
                "and the manager cannot tell how busy each doctor really is.")
    H.para(doc, "This project replaces the paper diary with a command-line system in Java. Every booking rule "
                "is enforced by the software, fees are calculated by pricing strategies, cancelled slots are "
                "offered to a waiting list, no-shows are tracked, visit notes are append-only, and all data is "
                "stored in JSON files so it survives restarts. There is deliberately no GUI and no database: the "
                "central challenge is time-slot logic, the object-oriented design, and the integrity of the data.")

    H.heading(doc, "1.2  Objectives", 2)
    H.bullets(doc, [
        "Model the clinic domain with inheritance and polymorphism: an abstract User with three roles, and an "
        "abstract AppointmentType with three concrete types that own their duration, fee and eligibility rules.",
        "Implement the scheduling engine: 15-minute slots, working hours, breaks, leave days, daily capacity, "
        "overlap detection, minimum notice and a 60-day booking horizon.",
        "Enforce the appointment lifecycle and every booking rule, including late-cancellation fees, atomic "
        "rescheduling, no-show tracking and automatic blocking.",
        "Add a waiting list that offers freed slots first-in-first-out with a time-limited hold, and a pricing "
        "subsystem based on the Strategy pattern.",
        "Persist everything as JSON with atomic writes, recoverable ids and clear failure on corrupt data, while "
        "keeping concurrent bookings consistent.",
        "Provide a role-filtered, command-driven console that never crashes on invalid input and a set of "
        "reports for the receptionist.",
    ])

    H.heading(doc, "1.3  Scope", 2)
    H.para(doc, "In scope: a single clinic, three roles (receptionist, doctor, patient), 15-minute booking "
                "slots, consultation/follow-up/emergency appointment types, insurance and senior pricing, a "
                "waiting list, visit records, notifications, an audit log and four receptionist reports. The "
                "application is a console program built with Maven; Gson is the only third-party library and "
                "JSON files are the only data store.")
    H.para(doc, "Out of scope: graphical user interface, relational database, network access, payments, "
                "e-mail/SMS delivery, multi-clinic tenancy and user self-registration for doctors.")

    H.heading(doc, "1.4  Document structure", 2)
    H.para(doc, "Section 2 lists the requirements. Sections 3 to 4 describe the architecture and the domain "
                "model. Sections 5 to 11 explain each subsystem: scheduling, booking, pricing, waiting list, "
                "visits, events and reports. Section 12 covers persistence and data integrity, section 13 the "
                "command-line interface, section 14 the verification performed, section 15 the design patterns, "
                "and sections 16 to 17 the limitations and the build/run instructions. The appendices contain "
                "the package inventory, JSON examples and verification-log excerpts.")

    # ------------------------------------------------------------------ 2 requirements
    H.heading(doc, "2  Requirements")
    H.heading(doc, "2.1  Functional requirements", 2)
    H.add_table(doc,
                ["ID", "Requirement", "Where it is implemented"],
                [
                    ["F1", "Three roles authenticate with a user id and password; passwords are hashed; three failed logins lock the account.", "AuthService, PasswordHasher, User"],
                    ["F2", "A default receptionist exists on first run and can unlock accounts.", "SeedData, AuthService.ensureDefaultReceptionist / unlock"],
                    ["F3", "Doctors have a weekly schedule with optional breaks, leave periods and a daily capacity.", "Doctor, WorkingDay, LeavePeriod, SchedulingService"],
                    ["F4", "Appointments are booked on a 15-minute grid inside working hours, outside breaks and leave, without overlaps, within the notice/horizon rules.", "BookingService, ScheduleRules, SlotFinder"],
                    ["F5", "Appointment types own duration, fee and eligibility (consultation, follow-up, emergency).", "AppointmentType + Consultation/FollowUp/Emergency"],
                    ["F6", "Fees use Standard/Insured/Senior pricing strategies; late cancellation charges 20% of the base fee.", "PricingPolicy implementations, PricingPolicyFactory, BillingService"],
                    ["F7", "Appointments follow a strict lifecycle and invalid transitions are rejected.", "Appointment.transitionTo, AppointmentStatus"],
                    ["F8", "Rescheduling is atomic; cancelled/rescheduled slots are offered to the waiting list.", "BookingService.reschedule/cancel, WaitlistService"],
                    ["F9", "Three no-shows in 90 days block a patient until a receptionist unblocks them.", "BookingService.markNoShow, Patient, AuthService.unlock"],
                    ["F10", "A FIFO waiting list with date range, time window, 2-hour offers and lazy expiry.", "WaitlistService, WaitlistEntry, WaitlistStatus"],
                    ["F11", "Visit notes are append-only; amendments are stored as new records; visibility depends on the role.", "VisitService, VisitRecord"],
                    ["F12", "Events feed a persisted notification inbox and an activity log; receptionists get four reports.", "EventBus, NotificationInbox, ActivityLog, ReportService"],
                ],
                widths=[1.1, 10.1, 5.4])

    H.heading(doc, "2.2  Non-functional requirements", 2)
    H.add_table(doc,
                ["ID", "Requirement", "Implementation"],
                [
                    ["N1", "Java 17 or newer, built with Maven.", "pom.xml: maven.compiler.release=17, Maven 3.9"],
                    ["N2", "Gson is the only third-party library.", "Single dependency; Gson's annotation-only transitive dependency is excluded"],
                    ["N3", "No database and no GUI; JSON files in data/.", "com.clinic.repository.Json*Repository"],
                    ["N4", "java.time everywhere; services never call LocalDateTime.now() directly.", "Injected java.time.Clock; --now demo option"],
                    ["N5", "Never use double/float for money; use BigDecimal.", "All money fields and calculations"],
                    ["N6", "Data must survive a crash or restart without corruption; ids continue.", "Atomic temp-file writes, SequenceStore"],
                    ["N7", "Concurrent bookings for one slot must not both succeed.", "ReentrantLock in BookingService, synchronized repositories"],
                    ["N8", "Invalid input must never crash the console; help must be role-filtered.", "Command parser, ConsoleUI exception boundary, Command.allowedRoles"],
                ],
                widths=[1.1, 8.4, 7.1])

    H.heading(doc, "2.3  Assumptions and interpretations", 2)
    H.bullets(doc, [
        "The clinic operates in one time zone. Date-time values are stored as local ISO-8601 strings.",
        "Slots are fixed 15-minute boundaries; an appointment occupies [start, start + duration).",
        "SENIOR is derived from the date of birth at the appointment date (65+). It takes precedence over the "
        "registered INSURED category, following the brief's pricing table where SENIOR is its own category.",
        "The daily capacity counts BOOKED, CHECKED_IN and COMPLETED appointments; only emergency appointments may "
        "exceed it.",
        "Revenue reports count patient shares for BOOKED/CHECKED_IN/COMPLETED/NO_SHOW appointments plus "
        "late-cancellation fees. The insurer share is reported separately.",
        "The no-show rate divides recorded no-shows by appointments in the period that were not cancelled.",
    ])

    # ------------------------------------------------------------------ 3 architecture
    H.heading(doc, "3  System architecture")
    H.heading(doc, "3.1  Layered design", 2)
    H.para(doc, "The application is organised in four layers with a strict dependency direction. The CLI parses "
                "text and formats output, but contains no business rules. The service layer implements the use "
                "cases and depends only on repository interfaces. The repository implementations own Gson and the "
                "file system. The model package is shared by all layers and contains entities and enums only.")
    H.add_figure(doc, image("architecture.png"),
                 "Figure 1 - Layered architecture and the direction of dependencies.")
    H.para(doc, "Supporting packages sit beside the service layer: scheduling contains pure slot arithmetic, "
                "pricing contains the pricing strategies, event contains the observer infrastructure, and factory "
                "contains the object factories used when creating and loading domain objects. ClinicApp and Main "
                "form the composition root: they are the only place where concrete JSON classes are constructed "
                "and injected into the services (manual dependency injection).")

    H.heading(doc, "3.2  Package responsibilities", 2)
    H.add_table(doc,
                ["Package", "Responsibility", "Key types"],
                [
                    ["model", "Domain entities and enums shared by every layer", "User, Doctor, Patient, Appointment, AppointmentType, WorkingDay, LeavePeriod, WaitlistEntry, VisitRecord"],
                    ["service", "Use cases and transaction boundaries", "AuthService, SchedulingService, BookingService, WaitlistService, BillingService, VisitService, ReportService"],
                    ["scheduling", "Pure time arithmetic and availability rules", "TimeRange, SlotFinder, ScheduleRules, SlotCell, SlotState"],
                    ["pricing", "Fee-splitting strategies", "PricingPolicy, StandardPricing, InsuredPricing, SeniorPricing"],
                    ["event", "Observer infrastructure and listeners", "ClinicEvent, EventType, EventBus, EventListener, NotificationInbox, ActivityLog"],
                    ["repository", "Persistence interfaces and JSON implementations", "UserRepository, AppointmentRepository, JsonSupport, SequenceStore, Json*Repository"],
                    ["factory", "Creation and polymorphic reconstruction", "UserFactory, AppointmentTypeFactory, PricingPolicyFactory"],
                    ["cli", "Command parsing, authorisation gate, presentation", "ConsoleUI, CommandParser, CommandRegistry, CommandContext, 31 command classes"],
                    ["exception", "Checked, user-facing errors", "ClinicException and 18 subclasses"],
                    ["util", "Small shared utilities", "PasswordHasher, Formats, Validators"],
                ],
                widths=[2.2, 6.2, 8.2], font_size=8.2)

    H.heading(doc, "3.3  Composition root and dependency injection", 2)
    H.para(doc, "ClinicApp.open(Path dataDirectory, Clock clock) creates the data folder, builds the configured "
                "Gson instance and the repositories, then constructs the services in dependency order. The two "
                "cyclic collaborators - booking and waiting list - are linked with setters after construction: "
                "BookingService.setWaitlistService(...) lets a cancellation trigger an offer, and "
                "WaitlistService.setBookingGateway(BookingService) lets an accepted offer go through the normal "
                "booking validation. Finally the event listeners are registered on the EventBus, the seed data is "
                "loaded on a fresh folder, expired offers are processed, and a CommandContext is returned for the "
                "console.")
    H.para(doc, "The Clock is a constructor argument of every component that needs time. This makes the system "
                "deterministic for testing and enables the --now=2026-11-02T08:00 option used by the demo, without "
                "any test-only branch inside the business logic.")

    H.heading(doc, "3.4  Example request flow", 2)
    H.numbered(doc, [
        "ConsoleUI reads a line and CommandParser turns it into a canonical command name, positionals and flags.",
        "CommandRegistry resolves the longest matching command name and ConsoleUI checks the command's allowed roles.",
        "The command object calls a service method; it never touches repositories or JSON directly.",
        "The service validates the request, mutates the model, calls repository save methods (which persist "
        "atomically) and publishes domain events.",
        "Command objects format the returned domain object; ConsoleUI catches every ClinicException and prints a "
        "single friendly line.",
    ])

    # ------------------------------------------------------------------ 4 domain model
    H.heading(doc, "4  Domain model")
    H.heading(doc, "4.1  Users and roles", 2)
    H.para(doc, "User is abstract and holds the identity and security state shared by every role: id, name, "
                "password hash and salt, failed-login counter, lock flag and creation time. getRole() is the only "
                "abstract method, so every subclass identifies itself as RECEPTIONIST, DOCTOR or PATIENT. The three "
                "subclasses add role-specific data: a doctor's specialty, weekly schedule, leave periods and daily "
                "capacity; a patient's date of birth, registered category, insurer, blocked flag and no-show "
                "history; a receptionist needs no extra data.")
    H.add_figure(doc, image("uml_users.png"),
                 "Figure 2 - User hierarchy with the doctor's schedule composition.")
    H.para(doc, "Passwords are never stored in plain text. UserFactory creates an account with a random 16-byte "
                "salt and stores SHA-256(salt + password), Base64 encoded. Authentication compares the candidate "
                "hash in constant time with MessageDigest.isEqual. A failed login increments the counter; the third "
                "consecutive failure sets locked=true and persists it. A successful login resets the counter. Only "
                "a receptionist can call AuthService.unlock, which clears both the login lock and (for patients) "
                "the no-show booking block - the no-show history itself is never deleted.")
    H.add_code(doc,
               "public void recordFailedLogin(int maxAttempts) {\n"
               "    failedLoginAttempts++;\n"
               "    if (failedLoginAttempts >= maxAttempts) {\n"
               "        locked = true;\n"
               "    }\n"
               "}",
               "Listing 1 - Lockout rule in User (AuthService supplies MAX_FAILED_ATTEMPTS = 3).")

    H.heading(doc, "4.2  Doctors, schedules and leave", 2)
    H.para(doc, "A doctor's weekly schedule is a map from DayOfWeek to WorkingDay. A WorkingDay stores start and "
                "end time plus an optional break; LeavePeriod is an inclusive date range with covers(date). "
                "The model exposes the queries the scheduling engine needs - worksOn(day), workingDay(day), "
                "isOnLeave(date) - and keeps the data serialisable: the JSON writer stores enum names as map keys "
                "and ISO times as strings. The doctor's dailyCapacity limits non-emergency appointments per day; "
                "emergency appointments are the only type allowed to exceed it.")
    H.add_code(doc,
               "{\n"
               "  \"MONDAY\": {\n"
               "    \"day\": \"MONDAY\",\n"
               "    \"start\": \"09:00\",\n"
               "    \"end\": \"17:00\",\n"
               "    \"breakStart\": \"13:00\",\n"
               "    \"breakEnd\": \"14:00\"\n"
               "  }\n"
               "}",
               "Listing 2 - One weekday in data/doctors.json (the weeklySchedule map of Dr. Bello).")

    H.heading(doc, "4.3  Appointment types", 2)
    H.para(doc, "AppointmentType is the second abstract hierarchy. Each concrete type answers four questions: "
                "how long does the appointment last, what is the base fee, which scheduling exceptions apply "
                "(break use, daily capacity, same-day-only, minimum notice), and is the patient eligible. "
                "Consultation and Emergency inherit the default eligibility check; FollowUp overrides it to require "
                "a COMPLETED appointment with the same doctor within the previous 14 days. The rationale is the open/"
                "closed principle: adding a fourth appointment type does not require editing the booking service - "
                "a new subclass plus one factory entry is enough.")
    H.add_figure(doc, image("uml_appointments.png"),
                 "Figure 3 - AppointmentType hierarchy, fee/duration and the factory that returns the singletons.")
    H.add_table(doc,
                ["Type", "Duration", "Base fee", "Special rules"],
                [
                    ["Consultation", "30 min", "5,000", "Standard booking rules; default eligibility."],
                    ["FollowUp", "15 min", "3,000", "Only within 14 days of a COMPLETED visit with the same doctor; the doctor/date come from the most recent completed appointment."],
                    ["Emergency", "20 min", "10,000", "Same-day only, may use the break, ignores daily capacity, no minimum notice; still cannot be in the past or overlap."],
                ],
                widths=[2.6, 1.8, 1.8, 10.4])

    H.heading(doc, "4.4  Appointment and lifecycle", 2)
    H.para(doc, "An Appointment stores the patient, doctor, type code, start/end, status, the money breakdown "
                "captured at booking time (base fee, patient share, insurer share), any late-cancellation fee, "
                "timestamps and an optional cancellation reason. The end time is derived from the appointment type "
                "when the booking is created, then stored, so the diary can be rendered without rebuilding type "
                "objects. Status changes go through transitionTo(status, at), which knows the legal transitions and "
                "throws InvalidStatusTransitionException otherwise. The lifecycle is therefore enforced in one place, "
                "not in each service method.")
    H.add_figure(doc, image("lifecycle.png"),
                 "Figure 4 - Appointment lifecycle; COMPLETED, CANCELLED and NO_SHOW are final.")
    H.add_table(doc,
                ["From", "To", "Trigger", "Rule"],
                [
                    ["BOOKED", "CHECKED_IN", "receptionist checkin", "Only on the local date of the appointment."],
                    ["CHECKED_IN", "COMPLETED", "doctor complete", "Only the doctor who owns the appointment."],
                    ["BOOKED", "CANCELLED", "patient or receptionist cancel", "Before the start; 20% fee inside 24 hours."],
                    ["BOOKED", "NO_SHOW", "receptionist noshow", "At least 15 minutes after the start."],
                    ["final", "any", "-", "InvalidStatusTransitionException."],
                ],
                widths=[2.2, 2.4, 4.6, 7.4])

    H.heading(doc, "4.5  Supporting entities", 2)
    H.add_table(doc,
                ["Entity", "Purpose", "Important fields"],
                [
                    ["WaitlistEntry", "A patient's interest in a doctor/date range/time window", "status, offeredStart, offeredType, offerExpiresAt"],
                    ["VisitRecord", "Append-only clinical note", "appointmentId, notes, diagnosis, prescriptions, amendmentOf"],
                    ["Notification", "One inbox message for a user", "userId, message, category, read"],
                    ["Enums", "Closed value sets", "Role, AppointmentStatus, AppointmentTypeCode, PatientCategory, TimeWindow, WaitlistStatus"],
                ],
                widths=[3.0, 6.6, 7.0])

    # ------------------------------------------------------------------ 5 scheduling
    H.heading(doc, "5  Scheduling engine")
    H.heading(doc, "5.1  Time intervals and overlap", 2)
    H.para(doc, "Every scheduling rule reduces to one primitive: do two intervals overlap? TimeRange is a "
                "half-open interval [start, end) with overlaps(other) implemented as start < other.end && "
                "other.start < end. Half-open semantics mean that 10:00-10:30 and 10:30-11:00 do not overlap, "
                "which is exactly what a clinic needs: one appointment may start when the previous one ends. The "
                "same class handles breaks, leave, double bookings and slot search, so there is one place where "
                "this logic can be tested and trusted.")
    H.add_code(doc,
               "public boolean overlaps(TimeRange other) {\n"
               "    return start.isBefore(other.end) && other.start.isBefore(end);\n"
               "}",
               "Listing 3 - The overlap primitive in scheduling/TimeRange.java.")
    H.add_table(doc,
                ["Range A", "Range B", "Overlaps?", "Why"],
                [
                    ["10:00-10:30", "10:30-11:00", "No", "Touching edges; half-open intervals."],
                    ["10:00-10:30", "10:15-10:45", "Yes", "Shared instant 10:15."],
                    ["10:00-11:00", "10:15-10:30", "Yes", "B is inside A."],
                    ["09:00-09:15", "10:00-10:30", "No", "Disjoint."],
                ],
                widths=[3.6, 3.6, 2.2, 7.2])

    H.heading(doc, "5.2  Slot generation", 2)
    H.para(doc, "Slots are generated every 15 minutes from the working day start until the appointment would no "
                "longer fit. A candidate start is accepted only if all of the following hold: the doctor works that "
                "date and is not on leave; the appointment range fits inside the working hours; it does not overlap "
                "the break unless the type may use it; it starts strictly in the future; the notice and 60-day "
                "horizon rules pass; no blocking appointment (BOOKED/CHECKED_IN) of the doctor overlaps; and the "
                "daily capacity is not exhausted for a type that respects capacity.")
    H.add_code(doc,
               "for (LocalDateTime start = dayStart;\n"
               "     !start.plus(type.getDuration()).isAfter(dayEnd);\n"
               "     start = start.plusMinutes(SLOT_MINUTES)) {\n"
               "    if (isFreeAt(doctor, start, type, doctorAppointments, now)) {\n"
               "        slots.add(start);\n"
               "    }\n"
               "}",
               "Listing 4 - Slot scan in SlotFinder.freeSlots (simplified).")
    H.para(doc, "The day is also rendered as a grid of 15-minute cells for the slots command. Each cell becomes "
                "BOOKED, CHECKED_IN, BREAK, PAST or FREE; consecutive break cells are merged into one range for "
                "display. The command prints the free count against the number of non-break cells and the capacity "
                "used against the doctor's capacity.")
    H.add_figure(doc, image("terminal_slots.png"),
                 "Figure 5 - slots command output: booked cells, merged break range, free-slot count and capacity use.")

    H.heading(doc, "5.3  Suggestions", 2)
    H.para(doc, "When a booking is rejected because the slot is taken, the service asks SlotFinder.suggest for the "
                "next free start times. The scan starts at the requested time, walks the day forward in 15-minute "
                "steps and then continues into following days, skipping days off and leave days, until three "
                "suggestions are collected or the 60-day horizon is reached. Emergency appointments are same-day "
                "only, so their search is limited to today. Suggestions are returned in ascending order and are "
                "included in SlotUnavailableException for the CLI to display.")
    H.add_figure(doc, image("terminal_booking.png"),
                 "Figure 6 - An overlapping booking is rejected with three concrete suggestions.")

    H.heading(doc, "5.4  Booking window and minimum notice", 2)
    H.para(doc, "The booking window is checked by ScheduleRules.checkBookingWindow: the start must be after now; "
                "an emergency must start today; a normal appointment must be within 60 days; and a type that "
                "requires minimum notice must start at least one hour after now. Emergency is the only type that "
                "sets requiresMinimumNotice() to false. Every violation produces a SlotUnavailableException with a "
                "sentence that tells the user exactly which rule failed.")

    # ------------------------------------------------------------------ 6 booking
    H.heading(doc, "6  Booking and lifecycle management")
    H.heading(doc, "6.1  Validation pipeline", 2)
    H.para(doc, "BookingService.book is the most important method in the project. It first identifies the actor "
                "(a patient may only book for themselves, a receptionist may book for any patient, a doctor may not "
                "book), then runs a fixed, ordered chain of checks. Each check throws a specific checked exception. "
                "Because the order is fixed, the message the user sees is the most relevant one; for example a "
                "blocked patient is told about the block rather than about an overlap.")
    H.add_figure(doc, image("booking_pipeline.png"),
                 "Figure 7 - The twelve booking checks and the exception each one raises.")
    H.para(doc, "After the checks the service asks BillingService for a PricingQuote, creates the Appointment with "
                "a new id, stores the fee split, saves it through the repository (which persists to disk), publishes "
                "APPOINTMENT_BOOKED on the event bus and returns the appointment for the CLI to render. A failed "
                "check leaves no state behind because nothing is mutated before all checks pass.")

    H.heading(doc, "6.2  Thread safety", 2)
    H.para(doc, "Two receptionists can try to book the same free slot at the same moment. If both requests read "
                "the diary before either writes, both could succeed. BookingService therefore serialises every write "
                "operation (book, cancel, reschedule, check-in, complete, no-show, and reservation of a wait-list "
                "offer) with a single ReentrantLock. The second request re-checks the slot after the first one has "
                "been saved and fails with SlotUnavailableException. Repositories synchronise their own reads and "
                "writes as a second line of defence.")
    H.add_code(doc,
               "bookingLock.lock();\n"
               "try {\n"
               "    // validate against the current state, mutate, save, publish\n"
               "} finally {\n"
               "    bookingLock.unlock();\n"
               "}",
               "Listing 5 - Write operations run under one lock (BookingService).")
    H.para(doc, "The self-test proves the property with two threads that start simultaneously on the same slot: "
                "exactly one booking succeeds and the other receives SlotUnavailableException.")

    H.heading(doc, "6.3  Cancellation and late fee", 2)
    H.para(doc, "A cancellation is allowed only while the appointment is BOOKED and before its start time; "
                "afterwards the appointment is a no-show or a completed visit, and CancellationNotAllowedException "
                "is thrown. If the cancellation happens less than 24 hours before the start, BillingService computes "
                "20% of the base fee and the amount is stored on the appointment for the revenue report. The freed "
                "slot is immediately offered to the waiting list. The event message includes the fee, and the CLI "
                "prints it.")
    H.add_code(doc,
               "Cancelled appointment #18. Late cancellation fee: 1,000 (20% of 5,000).",
               "Listing 6 - Late-cancellation output (consultation base fee 5,000).")

    H.heading(doc, "6.4  Rescheduling", 2)
    H.para(doc, "Rescheduling is one atomic operation. The service loads the appointment, checks ownership and the "
                "BOOKED status, and validates the new start time with the same rule chain used for a new booking, "
                "excluding the appointment itself from the overlap checks. Only when the new slot is valid does the "
                "service change start and end, save the appointment and publish APPOINTMENT_RESCHEDULED. The old "
                "slot is then offered to the waiting list. If validation fails, no field is modified - the patient "
                "still has the original appointment. There is no late fee for rescheduling.")

    H.heading(doc, "6.5  Check-in, completion and no-shows", 2)
    H.para(doc, "The receptionist checks a patient in on the day of the appointment; the doctor completes the visit "
                "after adding notes. A no-show can be recorded only on a BOOKED appointment and only at least 15 "
                "minutes after the start. Recording it appends the timestamp to the patient's no-show history, "
                "publishes NO_SHOW_RECORDED, and counts the no-shows in the last 90 days. When the count reaches "
                "three, the patient is blocked (Patient.block) and PATIENT_BLOCKED is published. A blocked patient "
                "cannot book; a receptionist lifts the block with patient unblock, which clears the block flag but "
                "leaves the history intact.")

    # ------------------------------------------------------------------ 7 pricing
    H.heading(doc, "7  Pricing and billing")
    H.heading(doc, "7.1  Strategy pattern", 2)
    H.para(doc, "Pricing is the second place where the open/closed principle matters. PricingPolicy is a small "
                "interface with category(), patientShare(baseFee) and insurerShare(baseFee). StandardPricing returns "
                "the full fee, InsuredPricing splits 30/70, and SeniorPricing applies a 20% discount. "
                "PricingPolicyFactory registers one policy per PatientCategory in an EnumMap and BillingService looks "
                "the policy up by category: there is no if/else on the category anywhere in the billing code.")
    H.add_figure(doc, image("uml_pricing.png"),
                 "Figure 8 - Pricing policies, factory lookup and the billing service.")

    H.heading(doc, "7.2  Fees and worked examples", 2)
    H.add_table(doc,
                ["Category", "Rule", "Consultation 5,000", "Insured 30/70 example"],
                [
                    ["STANDARD", "Patient pays the base fee", "Patient 5,000 / insurer 0", "-"],
                    ["INSURED", "Insurer covers 70%, patient 30%", "Patient 1,500 / insurer 3,500", "Follow-up 3,000 -> 900 / 2,100"],
                    ["SENIOR (65+)", "20% discount on the base fee", "Patient 4,000 / insurer 0", "Follow-up 3,000 -> 2,400 / 0"],
                ],
                widths=[2.8, 5.2, 4.8, 3.8])
    H.para(doc, "The split invariant is maintained by construction: InsuredPricing rounds the insurer share to two "
                "decimals with HALF_UP and computes patientShare as base minus insurer, so the two amounts always add "
                "up to the base fee. A five thousand consultation therefore always produces 1,500 + 3,500; an odd "
                "base would still be split exactly.")
    H.para(doc, "Money is BigDecimal everywhere and is stored in JSON as a plain string. Amounts are normalised to "
                "scale 2 with RoundingMode.HALF_UP when they are calculated. There is no double or float in the "
                "project, not even for percentages: the revenue report also uses BigDecimal and rounds once at the "
                "presentation boundary.")

    H.heading(doc, "7.3  Late cancellation and revenue", 2)
    H.para(doc, "BillingService.lateCancellationFee(type) returns 20% of the type's base fee. The value is stored in "
                "the appointment's lateCancellationFee field, so the revenue report can sum it directly. A "
                "cancellation outside 24 hours stores zero and prints no fee.")

    # ------------------------------------------------------------------ 8 waiting list
    H.heading(doc, "8  Waiting list")
    H.heading(doc, "8.1  Entries and matching", 2)
    H.para(doc, "A patient joins the waiting list with a doctor, an inclusive date range and an optional time "
                "window (MORNING before 12:00, AFTERNOON from 12:00, or ANY). A patient may hold at most one open "
                "entry per doctor. Entries are considered in FIFO order - creation timestamp first, then id - and an "
                "entry matches a freed slot when the slot date is inside the range, the time window matches, and the "
                "type of the freed appointment is still bookable at that slot.")
    H.add_figure(doc, image("waitlist_flow.png"),
                 "Figure 9 - Offer lifecycle: FIFO scan, timed hold, and the three outcomes.")

    H.heading(doc, "8.2  Offers, holds and expiry", 2)
    H.para(doc, "When a slot is freed, WaitlistService.onSlotFreed finds the first matching entry and creates an "
                "offer. The hold ends at the earlier of two moments: two hours after the offer, or one hour before "
                "the slot starts. This protects the clinic from holding a slot too close to its start. If the "
                "resulting expiry is not in the future (for example the slot starts in 30 minutes), the entry is "
                "skipped and the next matching entry is tried.")
    H.add_table(doc,
                ["Freed slot", "Offer created at", "Naive 2h hold", "Cut-off", "Actual expiry"],
                [
                    ["15:30", "08:00", "10:00", "14:30", "10:00 (2h wins)"],
                    ["09:00", "08:00", "10:00", "08:00", "08:00 - cannot be offered"],
                    ["15:30", "14:00", "16:00", "14:30", "14:30 (cut-off wins)"],
                ],
                widths=[3.0, 3.4, 3.2, 2.8, 4.2])
    H.para(doc, "While an offer is active the slot is logically reserved: BookingService rejects a booking for that "
                "exact slot by a different patient, even though no provisional appointment is written. If the patient "
                "accepts, WaitlistService calls BookingGateway.bookReservedSlot, which runs the normal booking "
                "validation and creates the appointment; the entry is marked ACCEPTED. If the patient declines, or "
                "the offer expires, the entry is finalised and the slot is offered to the next matching entry.")
    H.para(doc, "Expiry is lazy: WaitlistService.expireOffers runs at startup and before each command (ConsoleUI), "
                "so the system needs no background thread. This matches the brief and keeps the program easy to "
                "reason about: all state changes happen on the command thread.")

    # ------------------------------------------------------------------ 9 visits
    H.heading(doc, "9  Visit records and patient history")
    H.para(doc, "VisitRecord is append-only. A doctor adds notes, an optional diagnosis and optional prescriptions "
                "to a CHECKED_IN or COMPLETED appointment. If a record already exists for that appointment, the new "
                "entry is an amendment: it receives a new id, a timestamp, the doctor's name and amendmentOf pointing "
                "at the record it corrects. Nothing is deleted or overwritten, which satisfies the clinical "
                "requirement that history must remain auditable. The visit view orders records by recordedAt and "
                "labels amendments.")
    H.add_table(doc,
                ["Role", "Appointment data", "Clinical notes"],
                [
                    ["Patient", "Own appointments only", "Own history only"],
                    ["Doctor", "Own daily schedule; patients they have seen", "History of patients they have treated"],
                    ["Receptionist", "All appointments, schedules and reports", "Refused: receptionists must not see clinical notes"],
                ],
                widths=[3.2, 7.0, 6.4])
    H.para(doc, "Follow-up eligibility is derived from the same history. The service looks for the patient's most "
                "recent COMPLETED appointment with the requested doctor and passes its completion time and doctor id "
                "into BookingContext. FollowUp.checkEligibility then accepts the booking only when the slot starts at "
                "most 14 days after that completion. This keeps the rule inside the type class, where the brief "
                "requires it.")

    # ------------------------------------------------------------------ 10 events
    H.heading(doc, "10  Events, notifications and audit trail")
    H.para(doc, "The Observer pattern decouples the business services from the two things that must happen after "
                "every state change. Services publish an immutable ClinicEvent on the EventBus; any number of "
                "listeners can react. The bus iterates over a CopyOnWriteArrayList and isolates listener failures, "
                "so a broken notification can never abort a booking.")
    H.add_table(doc,
                ["Event", "Published when", "Recipients"],
                [
                    ["APPOINTMENT_BOOKED", "A booking (including one from a waiting-list offer) succeeds", "Patient, doctor"],
                    ["APPOINTMENT_RESCHEDULED", "An appointment is moved", "Patient, doctor"],
                    ["APPOINTMENT_CANCELLED", "An appointment is cancelled (message includes late fee)", "Patient, doctor"],
                    ["APPOINTMENT_CHECKED_IN / COMPLETED", "Receptionist checks in / doctor completes", "Patient, doctor"],
                    ["WAITLIST_JOINED / SLOT_OFFERED / OFFER_EXPIRED", "Waiting-list entry and offer lifecycle", "Patient (and doctor for offers)"],
                    ["NO_SHOW_RECORDED / PATIENT_BLOCKED", "No-show is recorded / third no-show blocks", "Patient, doctor"],
                    ["VISIT_RECORDED", "A note or amendment is saved", "Patient, doctor"],
                ],
                widths=[5.2, 7.6, 3.8], font_size=8.2)
    H.para(doc, "NotificationInbox is the first listener. It writes one persisted Notification per recipient, so the "
                "inbox survives restarts; the login message reports the unread count and the inbox command prints the "
                "messages and marks them read. ActivityLog is the second listener and appends one line per event to "
                "data/activity.log in the form timestamp | EVENT_TYPE | message, giving the manager an audit trail "
                "that is independent of the JSON snapshots.")

    # ------------------------------------------------------------------ 11 reports
    H.heading(doc, "11  Reports")
    H.para(doc, "ReportService answers the four questions the manager asks. All reports accept a date range and "
                "return immutable record objects (DailyScheduleReport, UtilisationReport, NoShowReport, "
                "RevenueReport), which the CLI formats as aligned tables.")
    H.add_table(doc,
                ["Report", "Definition"],
                [
                    ["Daily schedule", "All appointments of one doctor on one date, ordered by start time, with patient, type and status."],
                    ["Utilisation", "booked minutes / available minutes per doctor over a range. Available minutes are working hours minus break minutes for every day the doctor works and is not on leave; booked minutes are the durations of BOOKED/CHECKED_IN/COMPLETED appointments."],
                    ["No-show rate", "no-shows / appointments in the range that were not cancelled, per doctor and overall, plus the list of currently blocked patients."],
                    ["Revenue", "Patient shares (BOOKED/CHECKED_IN/COMPLETED/NO_SHOW) + late-cancellation fees; insurer share reported separately; total collected = patient fees + late fees."],
                ],
                widths=[3.0, 13.6])
    H.para(doc, "For example, Dr. Bello works five days a week, 09:00-17:00 with a one-hour break, which is 420 "
                "available minutes per day and 2,100 in a week. If 180 minutes are booked in that week, utilisation "
                "is 180 / 2,100 = 8.6%. The no-show report deliberately excludes cancelled appointments from the "
                "denominator, because a cancelled appointment cannot produce a no-show.")
    H.add_code(doc,
               "No-show report 2026-08-05 to 2026-11-02\n"
               "Appointments              8\n"
               "No-shows                  3\n"
               "Overall no-show rate  37.5%\n"
               "\n"
               "Blocked patients:\n"
               "Id  Name           Date of birth  Category\n"
               " 9  Tunde Balogun  1955-11-02     STANDARD",
               "Listing 7 - Representative no-show report from the demo data.")

    # ------------------------------------------------------------------ 12 persistence
    H.heading(doc, "12  Persistence and data integrity")
    H.heading(doc, "12.1  Files", 2)
    H.add_table(doc,
                ["File", "Content"],
                [
                    ["data/users.json", "Receptionists and patients, each with a type discriminator"],
                    ["data/doctors.json", "Doctors with specialty, weekly schedule, leave and capacity (type = DOCTOR)"],
                    ["data/appointments.json", "Appointments with status, fees and timestamps"],
                    ["data/waitlist.json", "Waiting-list entries and offers"],
                    ["data/visit-records.json", "Append-only clinical records"],
                    ["data/notifications.json", "Persisted inbox messages"],
                    ["data/sequences.json", "Last used id per entity (user, appointment, waitlist, visit, notification)"],
                    ["data/activity.log", "Append-only audit trail (one event per line)"],
                ],
                widths=[4.4, 12.2])

    H.heading(doc, "12.2  Representation", 2)
    H.para(doc, "JsonSupport.createGson configures pretty-printing and registers type adapters for LocalDate, "
                "LocalTime, LocalDateTime, DayOfWeek and BigDecimal. Dates and times are ISO-8601 strings, money is a "
                "plain string, enums are stored by name, and every polymorphic object stores a type field. On load, "
                "UserFactory.fromJson reads the type and asks Gson for the matching class, so the object graph is "
                "restored exactly; appointments store their type code and the factory supplies the duration and fee "
                "rules when the diary is interpreted.")
    H.add_code(doc,
               "{\n"
               "  \"id\": 18,\n"
               "  \"patientId\": 7,\n"
               "  \"doctorId\": 2,\n"
               "  \"type\": \"CONSULTATION\",\n"
               "  \"start\": \"2026-11-02T10:00:00\",\n"
               "  \"end\": \"2026-11-02T10:30:00\",\n"
               "  \"status\": \"BOOKED\",\n"
               "  \"baseFee\": \"5000.00\",\n"
               "  \"patientShare\": \"1500.00\",\n"
               "  \"insurerShare\": \"3500.00\"\n"
               "}",
               "Listing 8 - An appointment from data/appointments.json (fields trimmed for brevity).")

    H.heading(doc, "12.3  Atomic writes and crash safety", 2)
    H.para(doc, "A crash during a write must never leave a half-written JSON file. Every repository writes the "
                "complete new content to a sibling temporary file, flushes it, and then moves it over the real file "
                "with StandardCopyOption.ATOMIC_MOVE and REPLACE_EXISTING (falling back to a non-atomic move on file "
                "systems without atomic rename). The sequence store persists the next id before returning it, so ids "
                "never repeat after a restart.")
    H.add_code(doc,
               "Files.writeString(temp, json, StandardCharsets.UTF_8);\n"
               "Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,\n"
               "        StandardCopyOption.ATOMIC_MOVE);",
               "Listing 9 - Atomic write pattern used by every Json*Repository.")

    H.heading(doc, "12.4  Loading, missing files and corruption", 2)
    H.para(doc, "Repositories load their file eagerly in the constructor. A missing file is created with an empty "
                "array (or an empty object for sequences) and treated as an empty repository; a fresh data folder is "
                "seeded with the demo data set. A corrupt file is never partially loaded: JsonSupport catches the "
                "parse error and throws StorageException with the file name, the parser message and a hint to fix or "
                "delete the file. Main prints 'Cannot start the clinic system: ...' and exits with code 1 rather than "
                "running with incomplete data. This was verified by writing an invalid users.json and starting the "
                "program (see Appendix C).")
    H.para(doc, "Repositories return defensive copies of their lists so a caller cannot accidentally mutate the "
                "in-memory store while a save iterates over it, and all saves are synchronized, which complements the "
                "coarse booking lock in the service layer.")

    # ------------------------------------------------------------------ 13 CLI
    H.heading(doc, "13  Command-line interface")
    H.heading(doc, "13.1  Parsing and command objects", 2)
    H.para(doc, "The console is command-driven rather than menu-driven. CommandParser splits a line on whitespace, "
                "honours double quotes so that visit note 9 \"patient reports headache\" is one argument, and accepts "
                "--key=value flags as well as --key value. CommandRegistry resolves the longest matching canonical "
                "name (for example waitlist join), which keeps multi-word commands unambiguous. Every command is a "
                "class implementing Command with name, aliases, allowed roles, usage, summary and execute; the "
                "registry is also the source for role-filtered help. Scripted input can contain comment lines "
                "starting with #, and prompts are suppressed when stdin is not a terminal so the demo scripts "
                "produce clean output.")
    H.add_table(doc,
                ["Command", "Roles", "Purpose"],
                [
                    ["login / logout / help / exit", "all (login/help/exit before login)", "Session, help, exit"],
                    ["inbox", "all logged in", "Read notifications and mark them read"],
                    ["slots <doctorId> <date> [--type=..]", "all", "Day grid with free/booked/break cells and totals"],
                    ["book <doctorId> <dateTime> <TYPE>", "patient, receptionist", "Book an appointment; receptionists pass --patient=<id>"],
                    ["reschedule / cancel", "patient, receptionist", "Move or cancel; late fee shown"],
                    ["myappointments", "patient, doctor", "Patient's list or doctor's day schedule"],
                    ["waitlist join/accept/leave/decline/list", "patient", "Waiting-list lifecycle"],
                    ["patient add/unblock/list", "receptionist", "Register patients, lift blocks, list patients"],
                    ["doctor add/schedule/leave/list", "receptionist (list: all)", "Register doctors, edit schedule, add leave"],
                    ["checkin / noshow", "receptionist", "On-the-day check-in and no-show recording"],
                    ["complete", "doctor", "CHECKED_IN -> COMPLETED"],
                    ["visit note <id> \"<text>\" [--diagnosis=..] [--rx=..]", "doctor", "Append a note or amendment"],
                    ["history <patientId>", "patient, doctor", "Clinical history with role-based visibility"],
                    ["report daily/utilisation/noshow/revenue", "receptionist", "Four management reports"],
                ],
                widths=[6.6, 4.2, 5.8], font_size=8.2)

    H.heading(doc, "13.2  Error handling", 2)
    H.para(doc, "Services throw checked ClinicException subclasses; ConsoleUI catches them in one place and prints "
                "'Error: <message>'. Unexpected runtime errors are caught too, so a malformed command can never end "
                "the session. Argument errors append the usage line. The table below lists the exception types and a "
                "representative trigger.")
    H.add_table(doc,
                ["Exception", "Raised when"],
                [
                    ["AuthenticationException / AccountLockedException", "Wrong credentials / third consecutive failure"],
                    ["UnauthorizedActionException", "A role tries an action it may not perform"],
                    ["DoctorNotFound / PatientNotFound / AppointmentNotFound / WaitlistEntryNotFound", "Unknown id"],
                    ["InvalidInputException", "Malformed date, time, number, type code, category or blank field"],
                    ["SlotUnavailableException", "Overlap, past time, notice/horizon violation, capacity, active hold; carries suggestions"],
                    ["OutsideWorkingHoursException / DoctorOnLeaveException", "Slot outside working hours or inside a break / on leave"],
                    ["PatientBlockedException", "Blocked patient tries to book"],
                    ["BookingLimitExceededException", "A patient would hold a fourth active appointment"],
                    ["FollowUpNotEligibleException", "Follow-up without a recent completed visit with the same doctor"],
                    ["InvalidStatusTransitionException / CancellationNotAllowedException", "Illegal lifecycle change / cancellation after start"],
                    ["StorageException", "Missing folder cannot be created, file is corrupt, or a write fails"],
                ],
                widths=[7.2, 9.4], font_size=8.2)

    H.heading(doc, "13.3  Example sessions", 2)
    H.add_figure(doc, image("terminal_noshow.png"),
                 "Figure 10 - No-show block, receptionist unblock and the no-show report.")
    H.add_figure(doc, image("terminal_selftest.png"),
                 "Figure 11 - The self-test finishes with 40 passing checks.")

    # ------------------------------------------------------------------ 14 testing
    H.heading(doc, "14  Testing and verification")
    H.heading(doc, "14.1  Approach", 2)
    H.para(doc, "The brief restricts the project to Gson as the only third-party library, so JUnit was not added. "
                "Instead the project ships com.clinic.SelfTest, a plain Java harness with a tiny assertion helper. It "
                "opens a throwaway data folder with a fixed clock (Monday 2 November 2026, 08:00), drives the real "
                "services against the real JSON repositories, and prints one PASS/FAIL line per check. It exits with "
                "status 1 if anything fails, which makes it usable in CI as well. The harness covers value objects, "
                "pricing, every booking rule, lifecycle guards, waiting list, no-show block, authentication, "
                "concurrency and restart persistence.")
    H.add_table(doc,
                ["Area", "What is proven"],
                [
                    ["Time arithmetic", "Half-open overlap semantics; touching intervals do not overlap"],
                    ["Types and pricing", "30/15/20-minute durations, 5,000/3,000/10,000 fees, flags; Standard/Insured/Senior splits"],
                    ["Booking rules", "Overlap rejection with suggestions, break/leave/window/notice/capacity/horizon/limit/blocked cases"],
                    ["Follow-up", "Accepted within 14 days of a completed visit; rejected otherwise"],
                    ["Cancellation", "Late fee = 20% of the base fee"],
                    ["Rescheduling", "Occupied target rejected and original untouched; free target moves the appointment"],
                    ["No-show block", "Third no-show blocks the patient; receptionist unblock restores booking"],
                    ["Waiting list", "Cancellation creates an offer for the first matching entry; accept books the slot"],
                    ["Lifecycle", "COMPLETED -> CHECKED_IN and similar illegal changes are rejected"],
                    ["Authentication", "Password stored as salted hash, never plain text"],
                    ["Concurrency", "Two threads on one slot: exactly one success, one rejection"],
                    ["Persistence", "Activity log written; users/appointments/inbox survive a restart; ids continue"],
                ],
                widths=[4.2, 12.4])

    H.heading(doc, "14.2  Build and run verification", 2)
    H.para(doc, "A clean Maven build (mvn clean package) completes successfully and produces the runnable fat jar "
                "target/clinic-app.jar. A fresh data folder is created on the first run and seeded with four doctors, "
                "ten patients and seventeen appointments; subsequent runs load the same files. The self-test was run "
                "from the packaged jar and finished with 'SelfTest finished: 40 passed, 0 failed.'")

    H.heading(doc, "14.3  Scripted demo", 2)
    H.para(doc, "demo/run-demos.ps1 builds the jar, deletes demo-data/ and runs three scripted sessions as three "
                "separate JVM processes against the same folder. This single runner demonstrates overlap rejection with "
                "suggestions, the insurance split, a late cancellation fee, a waiting-list offer and acceptance, the "
                "no-show block and unblock, check-in, visit note, completion, follow-up booking, and finally restart "
                "persistence with ids continuing. docs/DEMO.md describes the expected output for each step.")
    H.add_figure(doc, image("terminal_booking.png"),
                 "Figure 12 - The overlap rejection and fee split as printed by the console.")
    H.para(doc, "The runner also exercises the restart requirement: the first session starts with 17 seeded "
                "appointments, the second loads 19 and the third loads 20; after the third session the data folder "
                "contains 21 appointments and the sequence file records the last ids.")

    H.heading(doc, "14.4  Corruption and failure behaviour", 2)
    H.para(doc, "Replacing data/users.json with an invalid JSON document and starting the program produces the "
                "message 'Cannot start the clinic system: Corrupt data file ... Fix or delete the file to start again.' "
                "and exit code 1; no partial data is loaded. Deleting the whole data folder is safe: the folder and "
                "all files are recreated and seeded on the next start. A missing optional file (for example "
                "notifications.json) is created empty without touching the rest of the data.")

    H.heading(doc, "14.5  Limits of the verification", 2)
    H.bullets(doc, [
        "The self-test covers services and persistence, not terminal rendering; console presentation was checked by "
        "the scripted demo.",
        "There is no fuzzing or property-based testing; malformed input was tested through the CLI smoke sessions.",
        "There is no load test; the data scale assumed by the brief (a small clinic) makes whole-file JSON rewrites "
        "acceptable.",
        "The scripts and outputs in this document were produced on Windows with Java 25 targeting Java 17.",
    ])

    # ------------------------------------------------------------------ 15 patterns
    H.heading(doc, "15  Design patterns and OOP principles")
    H.add_table(doc,
                ["Pattern / principle", "Where", "Why it matters here"],
                [
                    ["Inheritance", "User -> Receptionist/Doctor/Patient; AppointmentType -> Consultation/FollowUp/Emergency", "One identity and security model; one scheduling contract for all types"],
                    ["Polymorphism", "type.getDuration(), checkEligibility, pricing policies", "The booking service treats all types and categories uniformly"],
                    ["Strategy", "PricingPolicy and its three implementations", "Fee rules are interchangeable and closed to modification"],
                    ["Factory", "UserFactory, AppointmentTypeFactory, PricingPolicyFactory", "Rebuild polymorphic objects from JSON type fields; centralise creation"],
                    ["Observer", "EventBus with NotificationInbox and ActivityLog", "Notifications and auditing are decoupled from booking logic"],
                    ["Repository", "Repository interfaces + Json* implementations", "Services do not know about Gson or files"],
                    ["Command", "One class per CLI command, CommandRegistry", "Each command owns its usage, roles and argument parsing"],
                    ["Injection", "java.time.Clock and all services", "Deterministic time, testable services, --now demo mode"],
                    ["Encapsulation", "Appointment.transitionTo, Patient.block/unblock, WorkingDay helpers", "Rules live with the data they protect"],
                    ["Single responsibility", "scheduling, pricing, event, repository packages", "Each package has one reason to change"],
                    ["Open/closed", "new appointment type or pricing category", "Add a class and a factory entry; do not edit booking or billing"],
                    ["Dependency inversion", "services depend on repository interfaces and EventBus", "Persistence and notification delivery can be replaced independently"],
                ],
                widths=[3.4, 5.6, 7.6], font_size=8.2)
    H.para(doc, "The most visible design decision is that the two hardest rule sets - appointment-type behaviour and "
                "fee calculation - are polymorphic rather than conditional. The booking service asks an object for "
                "its rules instead of asking 'what type is this?'. That is what makes the system extensible and keeps "
                "the rule chain readable.")

    # ------------------------------------------------------------------ 16 quality
    H.heading(doc, "16  Quality, limitations and future work")
    H.heading(doc, "16.1  Metrics", 2)
    H.add_table(doc,
                ["Metric", "Value"],
                [
                    ["Java source files (main)", "110"],
                    ["Lines of Java (main)", "9,216"],
                    ["Packages", "11 (including the composition-root package)"],
                    ["CLI commands", "31 canonical commands plus aliases"],
                    ["Self-test checks", "40, all passing"],
                    ["Seed data", "4 doctors, 10 patients, 17 appointments, 3 visit records"],
                    ["Third-party runtime libraries", "1 (Gson)"],
                ],
                widths=[7.0, 9.6])
    H.heading(doc, "16.2  Known limitations", 2)
    H.bullets(doc, [
        "The booking lock is coarse (one lock for all writes). This is simple and correct for a single-clinic console "
        "application but would serialise unrelated doctors in a high-throughput service.",
        "Every save rewrites the whole JSON file. That is acceptable for the data size in the brief but would not "
        "scale to thousands of appointments.",
        "There is no password-change command and no password policy beyond the generated initial password.",
        "SENIOR pricing takes precedence over INSURED for patients aged 65+; this is a documented interpretation, not "
        "a rule from the brief.",
        "The waiting list offers the slot as soon as it is freed; there is no patient ranking beyond FIFO.",
        "The console is UTF-8 but the visual layout assumes a monospaced terminal; very narrow windows wrap lines.",
    ])
    H.heading(doc, "16.3  Future work", 2)
    H.bullets(doc, [
        "Recurring appointments (for example every week for four weeks) and a calendar-style weekly view.",
        "Automatic assignment of a doctor within a specialty when the patient has no preference.",
        "Reminders for appointments within 24 hours delivered through the inbox at login (already partly covered by "
        "the unread count) and optionally by e-mail.",
        "Export a patient's history to JSON or PDF from the console.",
        "A migration to JDBC/SQLite behind the same repository interfaces (the layering already allows this), plus "
        "optimistic locking to replace the global write lock.",
        "JUnit tests for the value objects and a CI workflow that runs the SelfTest harness on every push.",
    ])
    H.heading(doc, "16.4  Lessons learned", 2)
    H.para(doc, "The biggest lesson was the value of getting TimeRange.overlaps right before anything else. Almost "
                "every rule - breaks, leave, double booking, slot search, waiting-list matching - depends on it, and "
                "a single off-by-one at the interval boundary would have produced subtle, hard-to-find bugs. The "
                "second lesson was that injecting the Clock from the start made the whole system testable and made the "
                "reproducible demo possible; the third was that enforcing the lifecycle inside Appointment rather than "
                "in the services removed a whole class of inconsistent state changes.")

    # ------------------------------------------------------------------ 17 build
    H.heading(doc, "17  Build, run and demo")
    H.heading(doc, "17.1  Prerequisites", 2)
    H.bullets(doc, [
        "Java 17 or newer (verified with JDK 25 using --release 17).",
        "Apache Maven 3.9 or newer.",
        "Windows PowerShell for the demo runner (the application itself is platform independent).",
    ])
    H.heading(doc, "17.2  Commands", 2)
    H.add_code(doc,
               "cd clinical_java_project\n"
               "mvn clean package\n"
               "java -jar target/clinic-app.jar\n"
               "\n"
               "# reproducible demo state (pins the clock to Monday 2 November 2026, 08:00)\n"
               "java -jar target/clinic-app.jar --data=demo-data --now=2026-11-02T08:00\n"
               "\n"
               "# automated verification\n"
               "java -cp target/clinic-app.jar com.clinic.SelfTest\n"
               "\n"
               "# scripted three-session demo against one data folder\n"
               "powershell -ExecutionPolicy Bypass -File demo/run-demos.ps1",
               "Listing 10 - Build, run and verification commands.")
    H.heading(doc, "17.3  Demo accounts", 2)
    H.add_table(doc,
                ["User id", "Role", "Password"],
                [
                    ["1", "Receptionist (Amina Yusuf)", "admin123"],
                    ["2-5", "Doctors (Bello, Okafor, Chen, Silva)", "doctor123"],
                    ["6-15", "Patients (Grace Adeyemi, Ibrahim Musa, ...)", "patient123"],
                ],
                widths=[2.6, 9.0, 5.0])
    H.para(doc, "New doctors and patients receive a random one-time password when a receptionist registers them; "
                "only its salted hash is stored. See README.md for the full command reference and docs/DEMO.md for "
                "the presentation script.")
    H.heading(doc, "17.4  Troubleshooting", 2)
    H.bullets(doc, [
        "Corrupt data: the program prints the file name and the parser message, then exits. Fix the JSON or delete "
        "the data folder to start from the seeded state.",
        "Want a clean demo: delete data/ (or demo-data/) and run with --now=2026-11-02T08:00.",
        "Maven cannot download plugins: the build needs network access on the first run; afterwards the local "
        "repository cache is sufficient.",
    ])

    # ------------------------------------------------------------------ appendices
    H.page_break(doc)
    H.heading(doc, "Appendix A  Package and class inventory")
    H.add_table(doc,
                ["Package", "Files", "Classes and responsibilities"],
                [
                    ["com.clinic", "4", "Main (entry point), ClinicApp (composition root), SeedData (demo data), SelfTest (harness)"],
                    ["model", "21", "User, Receptionist, Doctor, Patient, Appointment, AppointmentType, Consultation, FollowUp, Emergency, WorkingDay, LeavePeriod, WaitlistEntry, VisitRecord, Notification, BookingContext, Role, AppointmentStatus, AppointmentTypeCode, PatientCategory, TimeWindow, WaitlistStatus"],
                    ["service", "14", "AuthService, SchedulingService, BookingService, WaitlistService, BillingService, VisitService, ReportService + BookingGateway, CreatedAccount, PricingQuote, 4 report records"],
                    ["scheduling", "5", "TimeRange, SlotFinder, ScheduleRules, SlotCell, SlotState"],
                    ["pricing", "4", "PricingPolicy, StandardPricing, InsuredPricing, SeniorPricing"],
                    ["event", "6", "ClinicEvent, EventType, EventBus, EventListener, NotificationInbox, ActivityLog"],
                    ["repository", "14", "6 interfaces, JsonSupport, SequenceStore, 6 JSON implementations"],
                    ["factory", "3", "UserFactory, AppointmentTypeFactory, PricingPolicyFactory"],
                    ["cli", "18", "ConsoleUI, CommandParser, CommandRegistry, CommandContext, ParsedCommand, CommandSupport, Table, Command + 31 command implementations"],
                    ["exception", "18", "ClinicException and 17 specific exceptions"],
                    ["util", "3", "PasswordHasher, Formats, Validators"],
                ],
                widths=[2.6, 1.4, 12.6], font_size=8)

    H.heading(doc, "Appendix B  Persisted JSON examples")
    H.para(doc, "The snippets below are taken from the seeded data folder after a demo run; long records are "
                "trimmed, but the field names, types and formats are exactly what the program writes.")
    H.add_code(doc,
               "// data/users.json (first record of each kind)\n"
               "{ \"type\": \"RECEPTIONIST\", \"id\": 1, \"name\": \"Amina Yusuf\",\n"
               "  \"passwordHash\": \"...\", \"passwordSalt\": \"...\", \"locked\": false }\n"
               "{ \"type\": \"PATIENT\", \"id\": 7, \"name\": \"Ibrahim Musa\",\n"
               "  \"dateOfBirth\": \"1993-11-02\", \"category\": \"INSURED\",\n"
               "  \"insurer\": \"AXA Health\", \"blocked\": false, \"noShowHistory\": [] }",
               "Listing 11 - Polymorphic user records.")
    H.add_code(doc,
               "// data/sequences.json\n"
               "{ \"user\": 15, \"appointment\": 21, \"waitlist\": 1, \"visit\": 4, \"notification\": 23 }",
               "Listing 12 - Id sequences continue from the last saved values.")
    H.add_code(doc,
               "// data/activity.log\n"
               "2026-11-02T08:00:00 | APPOINTMENT_BOOKED | Appointment #18 booked with Dr. Bello for 2026-11-02T10:00 (Consultation).\n"
               "2026-11-02T08:00:00 | APPOINTMENT_CANCELLED | Appointment #18 with Dr. Bello on 2026-11-02T10:00 was cancelled (changed plans). A late cancellation fee of 1,000 applies.",
               "Listing 13 - Audit trail lines.")

    H.heading(doc, "Appendix C  Verification log excerpts")
    H.add_code(doc,
               "$ mvn clean package\n"
               "[INFO] Building Console Clinic Appointment System 1.0.0\n"
               "[INFO] BUILD SUCCESS\n"
               "\n"
               "$ java -jar target/clinic-app.jar --data=demo-data --now=2026-11-02T08:00\n"
               "Loaded      : 4 doctors, 10 patients, 17 appointments\n"
               "Seed data   : created on this first run\n"
               "\n"
               "$ java -cp target/clinic-app.jar com.clinic.SelfTest\n"
               "PASS  concurrency: exactly one of two simultaneous bookings succeeds\n"
               "PASS  restart: ids continue after the last saved value\n"
               "SelfTest finished: 40 passed, 0 failed.\n"
               "ALL SELF-TEST CHECKS PASSED",
               "Listing 14 - Build, fresh start and self-test.")
    H.add_code(doc,
               "$ java -jar target/clinic-app.jar --data=broken-data\n"
               "Cannot start the clinic system: Corrupt data file '...\\users.json':\n"
               "Expected ':' at line 1 column 9 path $.this. Fix or delete the file to start again.\n"
               "exit code: 1",
               "Listing 15 - Corrupt data is reported clearly and the program exits cleanly.")

    doc.save(OUT)
    print("saved", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    build()
