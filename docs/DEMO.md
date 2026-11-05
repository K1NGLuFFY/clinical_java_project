# 5-minute demo script

The demo is fully reproducible because the clock is pinned to **Monday 2 November 2026, 08:00**
and the seed data is generated relative to that moment.

```powershell
# from the project root
mvn clean package
java -jar target/clinic-app.jar --data=demo-data --now=2026-11-02T08:00
```

The same three sessions can be piped automatically (this is what the runner does):

```powershell
powershell -ExecutionPolicy Bypass -File demo/run-demos.ps1
```

`demo/run-demos.ps1` builds the jar, deletes `demo-data/`, then runs
`demo/01-booking-waitlist.txt`, `demo/02-noshow-block.txt` and `demo/03-visits-followup.txt`
**as three separate JVM processes against the same data folder**. That is the "restart with data
intact" part of the demo: appointments created in demo 1 are still there in demo 2 and 3, and ids
keep counting.

> When input is piped (scripted demo) the `clinic>` prompt is suppressed so the output below is
> exactly what the runner prints; in an interactive terminal the prompt is shown.

Demo logins:

| Login | Password | Who |
|---|---|---|
| `1` | `admin123` | Amina Yusuf, receptionist |
| `2` | `doctor123` | Dr. Bello, General Practice |
| `7` | `patient123` | Ibrahim Musa, INSURED |
| `8` | `patient123` | Chinelo Eze, STANDARD |
| `9` | `patient123` | Tunde Balogun, SENIOR (3 no-shows) |
| `12` | `patient123` | Fatima Bello, STANDARD |

---

## 1. Slot grid and booking rules

```
login 1 admin123
slots 2 2026-11-02
Dr. Bello (General Practice) - Monday 2026-11-02
[09:00 BOOKED]  [09:15 BOOKED]  09:30  09:45  10:00  10:15  10:30  10:45  11:00  11:15  11:30  11:45  12:00
12:15  12:30  12:45  [13:00-14:00 BREAK]  14:00  14:15  14:30  14:45  15:00  15:15  [15:30 BOOKED]
[15:45 BOOKED]  16:00  16:15  16:30  16:45
Free consultation slots: 24 of 28     Daily capacity used: 2 of 20
```

Point out: 15-minute slots, booked cells, the merged break range, and the capacity line. `of 28`
is the number of 15-minute cells in the 09:00-17:00 working day minus the four break cells.

## 2. Overlap rejection with suggestions

```
book 2 2026-11-02T10:00 CONSULTATION --patient=7
Booked appointment #18: Ibrahim Musa with Dr. Bello (General Practice) on Monday 2026-11-02 at 10:00-10:30 (Consultation).
Patient pays 1,500, insurer 3,500.

book 2 2026-11-02T10:15 CONSULTATION --patient=8
Slot unavailable: Dr. Bello is already booked at 2026-11-02T10:15.
Next free slots: 2026-11-02T10:30, 2026-11-02T10:45, 2026-11-02T11:00
```

Point out: the INSURED patient is split 30/70 automatically by the pricing strategy; the
overlapping slot is refused and the next three free slots are suggested.

## 3. Late cancellation fee

```
login 7 patient123
cancel 18 --reason="changed plans"
Cancelled appointment #18. Late cancellation fee: 1,000 (20% of 5,000).
```

The appointment was inside 24 hours of its start, so 20% of the base fee is charged. A
cancellation more than 24 hours before the start is free.

## 4. Waiting list: cancelled slot offered and accepted

```
login 8 patient123
waitlist join 2 2026-11-02 2026-11-02 --time=AFTERNOON
Joined the waiting list as entry #1: Dr. Bello (General Practice), 2026-11-02 to 2026-11-02, time window AFTERNOON.
logout

login 1 admin123
cancel 10 --reason="freed for the waiting list"
Cancelled appointment #10. Late cancellation fee: 1,000 (20% of 5,000).

logout
login 8 patient123
inbox
2026-11-02 08:00  You joined the waiting list for Dr. Bello (General Practice) from 2026-11-02 to 2026-11-02.
2026-11-02 08:00  A slot on 2026-11-02T15:30 with Dr. Bello (General Practice) is held for you until
                  2026-11-02T10:00 (waiting-list entry #1). Accept it with 'waitlist accept 1'.

waitlist accept 1
Accepted waiting-list entry #1. Booked appointment #19 with Dr. Bello (General Practice) on Monday 2026-11-02 at 15:30-16:00.
```

Point out: FIFO offer, 2-hour hold (or until 1 hour before the slot, whichever is sooner),
delivered through the observer/notification inbox, expiry checked lazily (no threads).

## 5. No-show tracking, automatic block and receptionist unblock

This starts demo session 2 (a restart, same data folder).

```
login 1 admin123
noshow 6
Marked appointment #6 as NO_SHOW (Tunde Balogun).

login 9 patient123
book 2 2026-11-04T09:00 CONSULTATION
Error: Patient Tunde Balogun is blocked from booking because of repeated no-shows.
       Ask a receptionist to unblock the account.

login 1 admin123
report noshow
No-show report 2026-08-05 to 2026-11-02
Metric                Value
--------------------  -----
Appointments              8
No-shows                  3
Overall no-show rate  37.5%
...
Blocked patients:
Id  Name           Date of birth  Category
--  -------------  -------------  --------
 9  Tunde Balogun  1955-11-02     STANDARD

patient unblock 9
Patient #9 (Tunde Balogun) has been unblocked.

login 9 patient123
book 2 2026-11-04T09:00 CONSULTATION
Booked appointment #20: Tunde Balogun with Dr. Bello (General Practice) on Wednesday 2026-11-04 at 09:00-09:30 (Consultation).
Patient pays 4,000.
```

Point out: three no-shows within 90 days block booking; the receptionist lifts the block but the
no-show history is kept (visible in `demo-data/users.json`); the SENIOR patient pays 4,000.

## 6. Visit notes, completion, history and follow-up

This is demo session 3 (another restart).

```
login 1 admin123
checkin 9
Checked in appointment #9 (Fatima Bello) at 09:00.

login 2 doctor123
myappointments
Today's schedule for Dr. Bello (General Practice) - Monday 2026-11-02
#    Time         Patient       Type          Status
---  -----------  ------------  ------------  ----------
#9   09:00-09:30  Fatima Bello  Consultation  CHECKED_IN
...

visit note 9 "Patient reports headache for three days, no fever." --diagnosis="Tension headache" --rx="Paracetamol 500mg as needed"
Saved visit record #4 for appointment #9 (original note).

complete 9
Completed appointment #9. The visit is now closed.

login 12 patient123
history 12
History for Fatima Bello (1 record(s))
#4 2026-11-02 08:00  Dr. Bello
Diagnosis: Tension headache
Notes: Patient reports headache for three days, no fever.
Rx: Paracetamol 500mg as needed

book 2 2026-11-04T10:00 FOLLOWUP
Booked appointment #21: Fatima Bello with Dr. Bello (General Practice) on Wednesday 2026-11-04 at 10:00-10:15 (Follow-up).
Patient pays 3,000.
```

Point out: the lifecycle CHECKED_IN → COMPLETED, the append-only visit record, and the follow-up
type which is only available within 14 days of a COMPLETED visit with the same doctor (the
self-test also proves the rejection case). Adding a second note to the same appointment stores an
amendment instead of overwriting.

## 7. Restart with data intact

The three sessions are three separate JVMs on the same `demo-data/` folder. Demo 1 starts with the
17 seeded appointments; demo 2 loads 19 (appointments #18-#19 from demo 1 survive the restart) and
demo 3 loads 20. After the last session the data folder holds **21 appointments** (#18-#21 were
created across the three restarts), so a further restart reports:

```
Loaded      : 4 doctors, 10 patients, 21 appointments
```

Everything is in `demo-data/*.json` and `demo-data/activity.log`; stop the program, start it
again and `myappointments` / `inbox` / `report revenue` still show the same state.

## 8. Supporting evidence

```powershell
java -cp target/clinic-app.jar com.clinic.SelfTest
```

runs 40 programmatic checks covering slot arithmetic, pricing splits, every booking rule, the
late fee, atomic reschedule, the no-show block, waiting-list offer/accept, visit records,
authentication hardening, **two threads booking the same slot (exactly one succeeds)** and
**restart persistence + id continuation**. Expected ending:

```
SelfTest finished: 40 passed, 0 failed.
ALL SELF-TEST CHECKS PASSED
```

Useful extra commands during the demo: `report daily 2 2026-11-02`, `report utilisation`,
`report revenue`, `doctor list`, `patient list`, `help` (role-filtered).
