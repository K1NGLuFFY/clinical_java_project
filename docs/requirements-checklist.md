# Requirements checklist (brief → implementation evidence)

This maps every section of the project brief to the code and to a way to verify it.

| Brief requirement | Implementation | Evidence / how to verify |
|---|---|---|
| Console Java app, Java 17+, Maven, Gson only | `pom.xml` (`--release 17`, Gson with its annotation-only transitive dep excluded), `com.clinic.Main` | `mvn clean package`, `java -jar target/clinic-app.jar` |
| Three roles extending abstract `User`; login by id+password | `model/User`, `Receptionist`, `Doctor`, `Patient`, `Role`; `AuthService.login` | `login 2 doctor123` |
| SHA-256 password hash + per-user salt, never plain text | `util/PasswordHasher`, `factory/UserFactory`; JSON stores `passwordHash` + `passwordSalt` | open `data/users.json` |
| 3 failed logins lock the account; only receptionist unlocks | `AuthService.login/unlock`; `data/users.json` lock fields | 3 wrong `login` attempts, then `patient unblock <id>` |
| Default receptionist on first run | `SeedData` (Amina Yusuf, `admin123`); `AuthService.ensureDefaultReceptionist` safety net | fresh `data/` folder banner |
| Role-filtered commands | `cli/Command.allowedRoles`, `CommandRegistry.visibleTo`, `ConsoleUI.isAllowed` | `help` as logged-out / receptionist / doctor / patient |
| Doctor weekly schedule with break + leave + 15-minute slots + daily capacity | `model/Doctor`, `WorkingDay`, `LeavePeriod`; `scheduling/ScheduleRules`, `SlotFinder`; `doctor schedule` / `doctor leave` | `slots 2 2026-11-02`, `doctor schedule` |
| Emergency may exceed capacity, use break, same day, no notice | `model/Emergency`, `SlotFinder`, `ScheduleRules.checkBookingWindow` | SelfTest: "emergency may use the break"; try `book 2 2026-11-02T13:00 EMERGENCY` |
| Appointment types own duration/fee/eligibility | `model/AppointmentType` + `Consultation`, `FollowUp`, `Emergency`; `factory/AppointmentTypeFactory` | SelfTest type checks |
| Follow-up only within 14 days of COMPLETED visit with same doctor | `FollowUp.checkEligibility(BookingContext)`; `BookingService.book` builds the context | SelfTest pass/reject checks |
| Working hours, break, leave, overlap (doctor and patient), notice, 60-day horizon, 3 active limit, blocked patient | `BookingService.book` validation chain | SelfTest checks; demo overlap rejection |
| Next 3 free slots suggested on conflict | `SlotUnavailableException` + `SlotFinder.suggest` | demo: `book ... 10:15` → "Next free slots: ..." |
| Status lifecycle with final states and `InvalidStatusTransitionException` | `model/Appointment.transitionTo` | SelfTest: cannot complete a BOOKED appointment |
| Free cancellation ≥24h, otherwise 20% late fee | `BookingService.cancel` + `BillingService.lateCancellationFee` | demo late cancellation: 1,000 |
| Atomic reschedule, never leaves patient without appointment | `BookingService.reschedule` validates first, then mutates | SelfTest: occupied target rejected, original untouched |
| 3 no-shows in 90 days blocks booking; history kept | `BookingService.markNoShow`, `Patient.block/recordNoShow`, `AuthService.unlock` | demo no-show block + unblock |
| Strategy pricing with BigDecimal, no if/else on category | `pricing/PricingPolicy` + `StandardPricing`, `InsuredPricing`, `SeniorPricing`, `PricingPolicyFactory`, `BillingService` | SelfTest pricing splits; demo 1,500/3,500 |
| Waiting list: date range + time window, FIFO offer, 2h hold cap, expiry at startup and each command, accept/decline | `WaitlistService`, `model/WaitlistEntry`; `ConsoleUI` expiry hook | demo: offer + accept |
| Visit records append-only, amendments, role visibility | `VisitService.addNote/historyFor`, `model/VisitRecord` | demo: visit note + history; amend by adding another note |
| Observer events (booked, rescheduled, cancelled, offered, expired, no-show, blocked) with inbox + activity log | `event/EventBus`, `ClinicEvent`, `NotificationInbox`, `ActivityLog`; `data/activity.log` | demo inbox; `data/activity.log` |
| Reports: daily schedule, utilisation, no-show rate + blocked list, revenue | `ReportService`, DTO records | `report daily/utilisation/noshow/revenue` |
| Command interface, quoted args, help filtered by role, no crash on invalid input | `cli/CommandParser`, `ParsedCommand`, `CommandRegistry`, `ConsoleUI`, command classes | pipe `demo/*.txt`; try malformed commands |
| JSON persistence via Gson in `data/`, ISO dates, money strings, `type` fields rebuilt by factories | `repository/JsonSupport`, `Json*Repository`, `UserFactory.fromJson` | inspect `data/*.json` |
| Load at startup, save after every change, temp-file-then-move, ids continue, missing file created, corrupt file clean exit | `JsonSupport` atomic write/read helpers, `SequenceStore` | SelfTest restart checks; rename a JSON file to corrupt it |
| Thread-safe booking (exactly one of two concurrent bookings wins) | `ReentrantLock` in `BookingService`; synchronized repositories | `java -cp target/clinic-app.jar com.clinic.SelfTest` concurrency check |
| Layering: CLI → services → repository interfaces; JSON only in `repository`; Clock injected; no bare `LocalDateTime.now()` | package structure, `ClinicApp` composition root, `java.time.Clock` everywhere | grep check; `--now` option proves clock injection |
| Custom exceptions caught in one place | `exception/*`, `ConsoleUI.handle` | malformed/blocked commands |
| Seed data: ≥4 doctors, 10 patients (insured + senior), ≥15 appointments in various states | `com.clinic.SeedData` | fresh run banner: 4 doctors, 10 patients, 17 appointments |
| README, UML + lifecycle diagrams, Git history, 5-minute demo | `README.md`, `docs/uml.md`, `docs/DEMO.md`, `demo/`, Git commits | this folder |

## Bonus ideas from the brief

* **Patient reminders at login** — the login message reports unread inbox messages, and every
  booked/cancelled/offered event lands in the inbox (a reminder is the unread message).
* **Export of a patient's history to JSON** — not implemented; the history is already stored as
  JSON in `data/visit-records.json` and printed by `history <patientId>`.
* **Recurring appointments / weekly calendar view / automatic assignment** — not implemented.
