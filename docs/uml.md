# UML and design diagrams

These diagrams use [Mermaid](https://mermaid.js.org/) so they render directly on GitHub and in
most Markdown viewers. The same information can be pasted into <https://mermaid.live> and
exported as PNG/SVG for the report.

---

## 1. User hierarchy (inheritance)

```mermaid
classDiagram
    class User {
        <<abstract>>
        -int id
        -String name
        -String passwordHash
        -String passwordSalt
        -int failedLoginAttempts
        -boolean locked
        -LocalDateTime createdAt
        +getRole() Role
        +recordFailedLogin(int maxAttempts)
        +unlock()
        +hasPassword()
    }
    class Receptionist
    class Doctor {
        -String specialty
        -Map~DayOfWeek,WorkingDay~ weeklySchedule
        -List~LeavePeriod~ leavePeriods
        -int dailyCapacity
        +worksOn(DayOfWeek) boolean
        +isOnLeave(LocalDate) boolean
        +setWorkingDay(WorkingDay)
        +addLeave(LeavePeriod)
    }
    class Patient {
        -LocalDate dateOfBirth
        -PatientCategory category
        -String insurer
        -boolean blocked
        -List~LocalDateTime~ noShowHistory
        +ageOn(LocalDate) int
        +pricingCategoryOn(LocalDate) PatientCategory
        +recordNoShow(LocalDateTime)
        +noShowsSince(LocalDateTime) long
        +block(LocalDateTime)
    }
    class WorkingDay {
        -DayOfWeek day
        -LocalTime start
        -LocalTime end
        -LocalTime breakStart
        -LocalTime breakEnd
    }
    class LeavePeriod {
        -LocalDate from
        -LocalDate to
        +covers(LocalDate) boolean
    }

    User <|-- Receptionist
    User <|-- Doctor
    User <|-- Patient
    Doctor "1" o-- "*" WorkingDay : weekly schedule
    Doctor "1" o-- "*" LeavePeriod : leave days
```

---

## 2. Appointment type hierarchy (inheritance + polymorphism)

```mermaid
classDiagram
    class AppointmentType {
        <<abstract>>
        +getCode() AppointmentTypeCode
        +getDuration() Duration
        +getBaseFee() BigDecimal
        +mayUseBreak() boolean
        +ignoresDailyCapacity() boolean
        +sameDayOnly() boolean
        +requiresMinimumNotice() boolean
        +checkEligibility(BookingContext) void
    }
    class Consultation {
        30 minutes
        5,000 base fee
    }
    class FollowUp {
        15 minutes
        3,000 base fee
        checkEligibility(): completed visit with same doctor within 14 days
    }
    class Emergency {
        20 minutes
        10,000 base fee
        same day, may use break, ignores capacity, no minimum notice
    }
    class AppointmentTypeFactory {
        <<utility>>
        +forCode(AppointmentTypeCode) AppointmentType
        +parse(String) AppointmentType
    }
    class Appointment {
        -int id
        -int patientId
        -int doctorId
        -AppointmentTypeCode type
        -LocalDateTime start
        -LocalDateTime end
        -AppointmentStatus status
        -BigDecimal baseFee
        -BigDecimal patientShare
        -BigDecimal insurerShare
        -BigDecimal lateCancellationFee
        +transitionTo(AppointmentStatus, LocalDateTime)
        +overlaps(LocalDateTime, LocalDateTime) boolean
    }

    AppointmentType <|-- Consultation
    AppointmentType <|-- FollowUp
    AppointmentType <|-- Emergency
    AppointmentTypeFactory ..> AppointmentType : creates singletons
    Appointment --> AppointmentTypeCode : stores discriminator
```

---

## 3. Pricing strategies (Strategy pattern)

```mermaid
classDiagram
    class PricingPolicy {
        <<interface>>
        +category() PatientCategory
        +patientShare(BigDecimal baseFee) BigDecimal
        +insurerShare(BigDecimal baseFee) BigDecimal
    }
    class StandardPricing {
        patient pays 100%
    }
    class InsuredPricing {
        insurer 70%, patient 30%
    }
    class SeniorPricing {
        patient pays 80% (20% discount)
    }
    class PricingPolicyFactory {
        -Map~PatientCategory,PricingPolicy~ policies
        +forCategory(PatientCategory) PricingPolicy
    }
    class BillingService {
        +quote(Patient, AppointmentTypeCode, LocalDate) PricingQuote
        +lateCancellationFee(AppointmentTypeCode) BigDecimal
    }

    PricingPolicy <|.. StandardPricing
    PricingPolicy <|.. InsuredPricing
    PricingPolicy <|.. SeniorPricing
    PricingPolicyFactory o-- PricingPolicy
    BillingService --> PricingPolicyFactory : looks up strategy
```

The billing service contains no `if/else` on the patient category; it asks the factory for the
policy and calls `patientShare` / `insurerShare` (open/closed principle).

---

## 4. Appointment lifecycle (state diagram)

```mermaid
stateDiagram-v2
    [*] --> BOOKED : book(doctor, slot, type)
    BOOKED --> CHECKED_IN : checkin (receptionist, on the day)
    CHECKED_IN --> COMPLETED : complete (doctor)
    BOOKED --> CANCELLED : cancel before start
    BOOKED --> NO_SHOW : noshow (> 15 min after start)
    COMPLETED --> [*]
    CANCELLED --> [*]
    NO_SHOW --> [*]

    note right of COMPLETED
        Final states. Every other transition
        throws InvalidStatusTransitionException.
    end note
```

A cancellation inside 24 hours of the start time charges 20% of the type's base fee. A
reschedule validates the new slot first and only then replaces the old booking (atomic), and
the freed slot is offered to the waiting list.

---

## 5. Layering and patterns

```mermaid
flowchart TD
    CLI["cli · ConsoleUI, CommandParser,\nCommand classes (Command pattern)"] --> SVC
    SVC["service · AuthService, SchedulingService,\nBookingService, WaitlistService, BillingService,\nVisitService, ReportService"] --> REPO
    REPO["repository · interfaces + JSON implementations\n(Repository pattern)"] --> FILES[("data/*.json\ndata/activity.log")]
    SVC --> SCHED["scheduling · SlotFinder, ScheduleRules, TimeRange"]
    SVC --> PRICING["pricing · PricingPolicy strategies"]
    SVC --> EVENT["event · EventBus"]
    EVENT --> LISTENERS["NotificationInbox (Observer)\nActivityLog (Observer)"]
    SVC --> FACTORY["factory · UserFactory,\nAppointmentTypeFactory,\nPricingPolicyFactory"]
    FACTORY -.-> MODEL["model · entities + enums"]
    SCHED -.-> MODEL
```

Rules:

* the CLI talks to services only (never repositories or JSON classes);
* services depend on repository **interfaces**, not on the JSON implementations;
* `Main` / `ClinicApp` is the composition root and the only place that constructs concrete
  implementations (manual dependency injection);
* `Clock` is injected everywhere instead of calling `LocalDateTime.now()`.

---

## 6. Key runtime collaborators

```mermaid
sequenceDiagram
    actor R as Receptionist
    participant CLI
    participant B as BookingService
    participant S as SlotFinder/ScheduleRules
    participant A as AppointmentRepository
    participant W as WaitlistService
    participant E as EventBus

    R->>CLI: book 2 2026-11-02T10:00 CONSULTATION
    CLI->>B: book(patientId, doctorId, start, type, actor)
    B->>S: validate boundary, hours, break, leave, window, capacity
    S-->>B: ok
    B->>A: save(appointment)
    B->>E: publish(APPOINTMENT_BOOKED)
    E-->>W: NotificationInbox + ActivityLog update
    B-->>CLI: Appointment
    CLI-->>R: "Booked appointment #12 ..."

    R->>CLI: cancel 12
    CLI->>B: cancel(12, reason, actor)
    B->>A: save(status CANCELLED, late fee)
    B->>W: onSlotFreed(doctor, slot, type)
    W->>E: publish(WAITLIST_SLOT_OFFERED)
    B-->>CLI: cancelled appointment
```

---

## 7. Package structure (required by the brief)

```
com.clinic
├── model/       User, Receptionist, Doctor, Patient, Appointment, AppointmentType,
│                Consultation, FollowUp, Emergency, WorkingDay, LeavePeriod, WaitlistEntry,
│                VisitRecord, Notification, Role + enums
├── service/     AuthService, SchedulingService, BookingService, WaitlistService,
│                BillingService, VisitService, ReportService (+ DTO records)
├── scheduling/  TimeRange, SlotFinder, ScheduleRules, SlotCell, SlotState
├── pricing/     PricingPolicy + StandardPricing, InsuredPricing, SeniorPricing
├── event/       ClinicEvent, EventType, EventBus, EventListener, NotificationInbox, ActivityLog
├── repository/  repository interfaces + JSON implementations + SequenceStore + JsonSupport
├── factory/     UserFactory, AppointmentTypeFactory, PricingPolicyFactory
├── cli/         CommandParser, Command, CommandRegistry, CommandContext, ConsoleUI, commands
├── exception/   ClinicException + 18 custom exceptions
└── util/        PasswordHasher, Formats, Validators
```
