# Console Clinic Appointment System

A command-line clinic appointment system written in Java 17 for small clinics that still book
patients by phone and keep a paper diary. The system enforces every booking rule automatically,
prices appointments with pluggable policies, tracks no-shows, offers cancelled slots to a
waiting list and keeps everything in JSON files so the data survives a restart.

There is **no GUI and no database**; the only third-party library is **Gson**.

```
===============================
  Console Clinic Appointment System
===============================
Data folder : C:\...\clinical_java_project\data
Clinic time : 2026-11-02 08:00
Loaded      : 4 doctors, 10 patients, 17 appointments
...
clinic> login 2 doctor123
Welcome, Dr. Bello (General Practice)!
You have 0 unread message(s). Type INBOX.
clinic[DOCTOR]> myappointments
...
```

---

## 1. Requirements

| Requirement | Version |
|---|---|
| Java | 17 or newer (developed and verified on JDK 25 with `--release 17`) |
| Maven | 3.9 or newer |
| Third-party library | only `com.google.code.gson:gson:2.11.0` |

No database, no server, no GUI. Console input/output only.

## 2. Build and run

```bash
cd clinical_java_project
mvn clean package
java -jar target/clinic-app.jar
```

The first run creates the `data/` folder, writes the JSON files and seeds the demo data
(4 doctors, 10 patients, 17 appointments in different states, plus visit records).
Later runs load the existing data.

Useful options:

```bash
java -jar target/clinic-app.jar --data=demo-data          # use another data folder
java -jar target/clinic-app.jar --now=2026-11-02T08:00    # pin the clock for a reproducible demo
java -jar target/clinic-app.jar --help
```

`--now` makes the whole system behave as if it were Monday 2 November 2026, 08:00. All seeded
dates are relative to that moment, so the demo in `docs/DEMO.md` always works.

During development you can also run:

```bash
mvn exec:java
```

### Demo accounts (created on first run)

| User id | Role | Password |
|---|---|---|
| 1 | receptionist (Amina Yusuf) | `admin123` |
| 2 | Dr. Bello, General Practice | `doctor123` |
| 3 | Dr. Okafor, Pediatrics | `doctor123` |
| 4 | Dr. Chen, Cardiology | `doctor123` |
| 5 | Dr. Silva, Dermatology | `doctor123` |
| 6-15 | patients (Grace Adeyemi, Ibrahim Musa, ...) | `patient123` |

New accounts created with `patient add` / `doctor add` get a random one-time password that the
receptionist must note down; only a salted SHA-256 hash is stored.

---

## 3. Commands

The console is command-driven (no numbered menus). Command words and quoted arguments work,
e.g. `visit note 4 "patient reports headache"`. Invalid input prints a friendly message and
never crashes the program. `HELP` shows only the commands allowed for the current role.

### 3.1 Available to everyone (before or after login)

| Command | Description |
|---|---|
| `login <userId> <password>` | Log in. Three consecutive failures lock the account. |
| `help` | List the commands allowed for the current role. |
| `exit` | Quit (data is already saved after every change). |

### 3.2 Any logged-in user

| Command | Description |
|---|---|
| `logout` | End the session. |
| `inbox` | Show your notifications (also announced at login), then mark them read. |
| `slots <doctorId> <date> [--type=CONSULTATION]` | Day grid with free/booked/break/past slots and capacity use. |
| `doctor list` | List doctors, specialties and schedules. |

### 3.3 Patient commands

| Command | Description |
|---|---|
| `book <doctorId> <dateTime> <CONSULTATION\|FOLLOWUP\|EMERGENCY>` | Book for yourself. |
| `reschedule <appointmentId> <newDateTime>` | Move your appointment atomically. |
| `cancel <appointmentId> [--reason="..."]` | Cancel (free up to 24h before, otherwise 20% fee). |
| `myappointments` | All your appointments with status and fee. |
| `waitlist join <doctorId> <fromDate> <toDate> [--time=MORNING\|AFTERNOON\|ANY]` | Join the waiting list. |
| `waitlist accept <entryId>` | Accept an offered cancelled slot. |
| `waitlist decline <entryId>` | Decline an offer (the next patient is offered). |
| `waitlist leave <entryId>` | Leave the waiting list. |
| `waitlist list` | Your waiting-list entries. |
| `history <patientId>` | Your visit records (patients may only read their own). |

### 3.4 Doctor commands

| Command | Description |
|---|---|
| `myappointments` | Today's schedule. |
| `complete <appointmentId>` | Finish a checked-in visit (CHECKED_IN → COMPLETED). |
| `visit note <appointmentId> "<text>" [--diagnosis=..] [--rx=item;item]` | Add notes/diagnosis/prescriptions; corrections become amendments. |
| `history <patientId>` | History of a patient you have seen. |

### 3.5 Receptionist commands

| Command | Description |
|---|---|
| `book <doctorId> <dateTime> <TYPE> --patient=<patientId>` | Book for a patient. |
| `reschedule <appointmentId> <newDateTime>` / `cancel <appointmentId>` | Manage any appointment. |
| `patient add <name> <dob> <STANDARD\|INSURED> [--insurer=..]` | Register a patient (prints the initial password). |
| `patient unblock <id>` | Lift a no-show booking block. |
| `patient list` | All patients. |
| `doctor add <name> <specialty>` | Register a doctor (prints the initial password). |
| `doctor schedule <id> <day> <start> <end> [--break=13:00-14:00]` | Set/replace one weekday. |
| `doctor leave <id> <fromDate> [<toDate>]` | Add leave (single day or inclusive range). |
| `checkin <appointmentId>` | Check the patient in on the day. |
| `noshow <appointmentId>` | Mark a no-show more than 15 minutes after the start. |
| `report daily <doctorId> <date>` | Daily schedule. |
| `report utilisation [--from=.. --to=..]` | Booked minutes / available minutes per doctor. |
| `report noshow [--from=.. --to=..]` | No-show rates per doctor and overall + blocked patients. |
| `report revenue [--from=.. --to=..]` | Patient fees, insurer share, late-cancellation fees, collected. |

Dates are ISO `yyyy-MM-dd`; date-times are `yyyy-MM-ddTHH:mm` (`2026-11-02T10:00`).
Day names are `MONDAY`...`SUNDAY`. Times are 24h `HH:mm`.

### 3.6 Example session

```
$ java -jar target/clinic-app.jar --data=demo-data --now=2026-11-02T08:00
...
clinic> login 2 doctor123
Welcome, Dr. Bello (General Practice)!
You have 0 unread message(s). Type INBOX.
clinic[DOCTOR]> slots 2 2026-11-02
Dr. Bello (General Practice) - Monday 2026-11-02
[09:00 BOOKED]  [09:15 BOOKED]  09:30  09:45  10:00  ...  [13:00-14:00 BREAK]  ...
Free consultation slots: 24 of 28     Daily capacity used: 2 of 20
clinic[DOCTOR]> myappointments
...
```

---

## 4. Business rules implemented

### 4.1 Scheduling and booking

* 15-minute slot grid; every appointment starts on a slot boundary.
* The appointment must fit inside the doctor's working hours, outside the break (unless it is an
  Emergency), and not on a leave day.
* It must not overlap another active appointment of the doctor, nor another active appointment
  of the same patient (even with another doctor).
* It must be in the future, at least 1 hour ahead (Emergency exempt) and at most 60 days ahead.
* A patient may hold at most 3 active upcoming appointments; a blocked patient cannot book.
* The doctor's daily capacity cannot be exceeded by normal types; Emergency ignores it.
* When a slot is unavailable the system suggests the next free slots for the doctor.

### 4.2 Appointment types

| Type | Duration | Base fee | Special rules |
|---|---|---|---|
| Consultation | 30 min | 5,000 | standard rules |
| FollowUp | 15 min | 3,000 | only within 14 days of a COMPLETED visit with the same doctor |
| Emergency | 20 min | 10,000 | same-day only, may use the break, ignores daily capacity, no minimum notice |

Duration, fee and eligibility live in the type classes, not in the booking service.

### 4.3 Lifecycle

```
BOOKED ──checkin──▶ CHECKED_IN ──complete──▶ COMPLETED
   │
   ├──cancel──▶ CANCELLED
   └──noshow──▶ NO_SHOW
```

COMPLETED, CANCELLED and NO_SHOW are final. Any other transition throws
`InvalidStatusTransitionException`.

### 4.4 Cancellations, reschedules, no-shows

* Cancelling at least 24 hours before the start is free; inside 24 hours a late fee of 20% of
  the base fee is applied automatically.
* A reschedule validates the new slot first and only then replaces the old booking, so a failed
  reschedule leaves the patient with the original appointment.
* Three no-shows within 90 days block the patient from booking until a receptionist runs
  `patient unblock <id>`. The no-show history is kept after unblocking.
* Every cancelled (or freed) slot immediately triggers the waiting list.

### 4.5 Pricing strategies

| Category | Rule | Consultation example |
|---|---|---|
| STANDARD | patient pays the base fee | patient 5,000 |
| INSURED | insurer covers 70%, patient 30% | patient 1,500, insurer 3,500 |
| SENIOR (65+) | 20% discount | patient 4,000 |

`BillingService` has **no if/else on the category**: `PricingPolicyFactory` looks the strategy up
in an `EnumMap` and the service calls `patientShare` / `insurerShare`. All money is `BigDecimal`
stored as strings in JSON.

> Design note: `SENIOR` is derived from the date of birth at pricing time (65 or older), so a
> senior who registered as INSURED is billed with the senior discount rather than insurance.
> This follows the brief's pricing table, where SENIOR is its own category.

### 4.6 Waiting list

* A patient joins with a doctor, a date range and an optional time window (mornings/afternoons).
* When a slot is freed, the first matching entry (FIFO) is offered the slot and holds it for
  2 hours, or until 1 hour before the slot if that is sooner. The slot cannot be booked by
  anybody else while the offer is active.
* Accept books the appointment; decline or expiry passes the offer to the next matching entry.
* Expiry is lazy (no background threads): it runs at startup and before every command.

### 4.7 Visit records

* A doctor adds notes, a diagnosis and prescriptions to a CHECKED_IN or COMPLETED appointment.
* Records are append-only: a correction is a new amendment with a timestamp and the doctor's
  name; nothing is deleted or overwritten.
* Patients see their own history; doctors see the history of patients they have treated;
  receptionists see appointment data but not clinical notes.

### 4.8 Notifications and audit trail (Observer pattern)

Every event (booked, rescheduled, cancelled, slot offered, offer expired, no-show, patient
blocked, ...) is published on an `EventBus`. Two listeners are registered:

* `NotificationInbox` stores per-user messages (persisted, shown at login and with `inbox`);
* `ActivityLog` appends every event to `data/activity.log`.

---

## 5. Architecture

```
com.clinic
├── Main / ClinicApp      composition root (manual dependency injection)
├── model/                entities and enums
├── service/              use cases (booking, scheduling, auth, waiting list, billing, visits, reports)
├── scheduling/           TimeRange, SlotFinder, ScheduleRules (pure slot arithmetic)
├── pricing/              PricingPolicy + StandardPricing, InsuredPricing, SeniorPricing
├── event/                ClinicEvent, EventBus, NotificationInbox, ActivityLog
├── repository/           repository interfaces + JSON implementations + SequenceStore + JsonSupport
├── factory/              UserFactory, AppointmentTypeFactory, PricingPolicyFactory
├── cli/                  CommandParser, Command, CommandRegistry, CommandContext, ConsoleUI, commands
├── exception/            ClinicException + custom exceptions
└── util/                 PasswordHasher, Formats, Validators
```

Patterns used:

* **Strategy** – `PricingPolicy` implementations, selected by `PricingPolicyFactory`.
* **Factory** – users, appointment types and pricing policies are rebuilt from the `type` field
  stored in JSON.
* **Observer** – `EventBus` with `NotificationInbox` and `ActivityLog` listeners.
* **Repository** – services only know interfaces; JSON implementations are constructed in
  `ClinicApp`.
* **Command** – one class per CLI command, with usage and allowed roles, resolved by the
  `CommandRegistry`.
* **Template method-ish type hierarchy** – `AppointmentType` owns duration, fee, capacity/break
  flags and eligibility; subclasses only override what differs.
* **Inheritance/polymorphism** – abstract `User` (Receptionist, Doctor, Patient) and abstract
  `AppointmentType` (Consultation, FollowUp, Emergency).

Layering: the CLI talks to services only; services depend on repository interfaces; `ClinicApp`
is the only place that constructs JSON classes. `java.time.Clock` is injected everywhere and
`LocalDateTime.now()` is never called inside services or scheduling (which also makes the
`--now` demo mode possible).

`TimeRange.overlaps()` is the single overlap primitive used by breaks, leave, double bookings and
slot search. It is half-open: 10:00-10:30 and 10:30-11:00 do not overlap.

---

## 6. Data storage

All state lives in `data/`:

| File | Content |
|---|---|
| `data/users.json` | receptionists and patients (each with a `type` field) |
| `data/doctors.json` | doctors with schedule, leave and capacity (`type: "DOCTOR"`) |
| `data/appointments.json` | appointments with status, fees, timestamps |
| `data/waitlist.json` | waiting-list entries and offers |
| `data/visit-records.json` | append-only clinical records |
| `data/notifications.json` | inbox messages |
| `data/sequences.json` | last used ids per entity (ids continue after a restart) |
| `data/activity.log` | append-only event audit trail |

Formats: dates and times are ISO-8601 strings (`"2026-11-02T10:00:00"`), money is `BigDecimal`
stored as a string, enums are stored by name and every polymorphic object stores a `type` field.

Integrity:

* writes go to a temporary file first and are then moved over the real file (atomic move), so a
  crash cannot leave a half-written file;
* missing files are created on first run;
* a corrupt file produces a clear message (`Cannot start the clinic system: ...`) and the
  program exits cleanly instead of running with partial data;
* ids never repeat because the sequence is persisted with every allocation.

### Thread safety

Booking, cancelling and rescheduling run under one `ReentrantLock` in `BookingService`, and the
repositories are synchronized. Two requests for the same slot therefore cannot both succeed:
the second one is validated after the first one has been saved and gets
`SlotUnavailableException`. `SelfTest` proves this with two concurrent booking threads.

---

## 7. Error handling

Services throw custom checked exceptions: `DoctorNotFound`, `PatientNotFound`,
`AppointmentNotFound`, `WaitlistEntryNotFound`, `SlotUnavailable`, `OutsideWorkingHours`,
`DoctorOnLeave`, `PatientBlocked`, `BookingLimitExceeded`, `FollowUpNotEligible`,
`InvalidStatusTransition`, `CancellationNotAllowed`, `Authentication`, `AccountLocked`,
`UnauthorizedAction`, `InvalidInput` and `Storage`. The CLI catches `ClinicException` in one
place and prints `Error: <message>`; unexpected runtime errors are also caught so the session
never dies. Argument errors include the command usage.

---

## 8. Testing and demo

* `docs/DEMO.md` — a 5-minute live/recorded demo script covering overlap rejection, a late
  cancellation with fee, a waiting-list offer, a no-show block and a restart with intact data.
* `demo/*.txt` — ready-to-pipe command scripts used by `demo/run-demos.ps1`.
* `SelfTest` (`java -cp target/clinic-app.jar com.clinic.SelfTest`) — programmatic checks:
  slot arithmetic and overlap, pricing splits, booking rules, late fee, waiting list offer,
  no-show block, follow-up eligibility and concurrent booking.

Run the scripted session:

```powershell
mvn clean package
powershell -ExecutionPolicy Bypass -File demo/run-demos.ps1
```

It builds the jar, runs the demo scripts against fresh `demo-data/` folders and prints the
evidence (including the JSON files after a restart).

---

## 9. Design decisions and assumptions

1. **Registration login ids.** Patients and doctors log in with their numeric id; the seeder uses
   readable initial passwords documented above. New accounts get a one-time generated password.
2. **SENIOR pricing** is derived from age at the appointment date and takes precedence over
   INSURED (see 4.5).
3. **Waiting-list offers reserve the slot** logically: while an offer is active, other patients
   cannot book that slot, but no provisional appointment is written until the offer is accepted.
4. **Completed appointment history** is used for FollowUp eligibility by completion time
   (`updatedAt` when the doctor completed the visit).
5. **Revenue** counts patient shares for BOOKED/CHECKED_IN/COMPLETED/NO_SHOW appointments plus
   late-cancellation fees; the insurer share is reported separately. `totalCollected` is what
   patients paid.
6. **No-show rate** divides recorded no-shows by appointments in the period that were not
   cancelled.
7. **`,000`-style money** is formatted with the JVM's default locale-independent US format
   (`5,000`, `1,500.50`) so the console output is stable.
8. **Reschedules** are cancellations only from the patient's point of view: no late fee is
   charged, but the freed slot is offered to the waiting list.
9. **Extra commands** beyond the brief: `complete`, `doctor list`, `patient list`,
   `waitlist list/decline`, `doctor view` are thin additions that make the required lifecycle and
   reports usable from the console; all required commands from the brief are present.
10. **Thread safety** is implemented with a single booking lock (coarse-grained but simple and
    correct for a console application); repositories also synchronize their writes.

---

## 10. Project layout

```
clinical_java_project/
├── pom.xml
├── README.md
├── docs/
│   ├── Clinic_Appointment_System_Documentation.docx   submission report (25 pages)
│   ├── Clinic_Appointment_System_Presentation.pptx    presentation deck (16 slides + speaker notes)
│   ├── requirements-checklist.md                      brief requirement -> code/evidence map
│   ├── uml.md                                         UML + lifecycle + package diagrams (Mermaid)
│   ├── DEMO.md                                        5-minute demo script with expected output
│   ├── images/                                        diagrams and terminal captures used by the DOCX/PPTX
│   └── build/                                         saved output excerpts for the verification checks
├── demo/               scripted demo sessions (pipable command files) + run-demos.ps1
└── src/main/java/com/clinic/...
```

The self-test harness lives in `src/main/java/com/clinic/SelfTest.java` on purpose: the brief allows
Gson as the only third-party library, so no test framework dependency is added.

## 11. Submission documentation and slides

The two files handed in for the written and oral parts are in `docs/`:

* **`docs/Clinic_Appointment_System_Documentation.docx`** - a 25-page report covering the
  problem, requirements, architecture, domain model, the scheduling/booking/pricing/waiting-list
  subsystems, persistence, CLI, verification, design patterns, limitations and build/run
  instructions, with 12 figures, 22 tables and code listings.
* **`docs/Clinic_Appointment_System_Presentation.pptx`** - a 16-slide 16:9 presentation with
  editable diagrams, real console captures and speaker notes on every slide (open the Notes pane
  while presenting or print the notes pages).

The DOCX cover and its Section 1 team table, plus the PPTX title and final slides, include:

| Team member | Student ID |
|---|---|
| Onah Debeiwu Chrysanctus | Student1639236 |
| Okeke Chiemerie Miracle | Student1647235 |
| Okonkwo Samuel chiemerie | Student1627019 |

Course: Java development; Instructor: Mr Kamsi; Submission date: 20th November 2026.

The detailed source-by-source guide is available at `docs/PROJECT_GUIDE.md` and the repository
root as `PROJECT_GUIDE.md`. The Office documents and diagrams are included as finalized files.

## 12. Git history

The repository contains incremental commits that follow the three milestones of the brief
(models/rules, services/persistence/CLI, visits/reports/polish). Use `git log --oneline` to see
the history.
