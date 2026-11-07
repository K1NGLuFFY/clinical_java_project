"""Build the presentation deck for the clinic appointment system (python-pptx only)."""
from __future__ import annotations

import os
import sys

from PIL import Image
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
IMAGES = os.path.join(PROJECT, "docs", "images")
OUT = os.path.join(PROJECT, "docs", "Clinic_Appointment_System_Presentation.pptx")

BODY = "Calibri"
MONO = "Consolas"

NAVY = RGBColor(0x10, 0x2A, 0x43)
NAVY_SOFT = RGBColor(0x1E, 0x3A, 0x5F)
TEAL = RGBColor(0x0D, 0x6E, 0x76)
TEAL_BRIGHT = RGBColor(0x14, 0x9A, 0xA6)
TEAL_LIGHT = RGBColor(0xE0, 0xF2, 0xF1)
AMBER = RGBColor(0xC9, 0x8A, 0x04)
AMBER_LIGHT = RGBColor(0xFE, 0xF3, 0xC7)
RED = RGBColor(0xB9, 0x1C, 0x1C)
GREEN = RGBColor(0x15, 0x80, 0x3D)
GRAY = RGBColor(0x47, 0x55, 0x69)
GRAY_LIGHT = RGBColor(0xE2, 0xE8, 0xF0)
BG = RGBColor(0xF7, 0xF9, 0xFC)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
INK = RGBColor(0x1F, 0x29, 0x37)

SW, SH = 13.333, 7.5
M = 0.65


def image_size(path: str) -> tuple[float, float]:
    with Image.open(path) as handle:
        width, height = handle.size
    return width, height


def new_slide(prs: Presentation, background: RGBColor = BG):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = background
    return slide


def rect(slide, x, y, w, h, fill, line=None, shape=MSO_SHAPE.ROUNDED_RECTANGLE, radius=None):
    shape_obj = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    shape_obj.fill.solid()
    shape_obj.fill.fore_color.rgb = fill
    if line is None:
        shape_obj.line.fill.background()
    else:
        shape_obj.line.color.rgb = line
        shape_obj.line.width = Pt(1.25)
    shape_obj.shadow.inherit = False
    if radius is not None:
        try:
            shape_obj.adjustments[0] = radius
        except Exception:
            pass
    return shape_obj


def tb(slide, x, y, w, h, text, size=16, bold=False, color=INK, font=BODY,
       align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, spacing=1.0, space_after=0):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = 0
    frame.margin_right = 0
    frame.margin_top = 0
    frame.margin_bottom = 0
    frame.vertical_anchor = anchor
    lines = text.split("\n")
    for index, line in enumerate(lines):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.alignment = align
        paragraph.line_spacing = spacing
        paragraph.space_after = Pt(space_after)
        run = paragraph.add_run()
        run.text = line
        run.font.name = font
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.color.rgb = color
    return box


def bullets(slide, x, y, w, h, items, size=14, color=INK, bullet_color=TEAL,
            gap=8, line_spacing=1.05):
    box = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = 0
    frame.margin_right = 0
    frame.margin_top = 0
    frame.margin_bottom = 0
    for index, item in enumerate(items):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.space_after = Pt(gap)
        paragraph.line_spacing = line_spacing
        dot = paragraph.add_run()
        dot.text = "▪  "
        dot.font.name = BODY
        dot.font.size = Pt(size)
        dot.font.color.rgb = bullet_color
        run = paragraph.add_run()
        run.text = item
        run.font.name = BODY
        run.font.size = Pt(size)
        run.font.color.rgb = color
    return box


def eyebrow(slide, text):
    tb(slide, M, 0.38, 10.0, 0.28, text.upper(), size=10.5, bold=True, color=TEAL)


def slide_title(slide, title, subtitle=None):
    tb(slide, M, 0.62, SW - 2 * M, 0.7, title, size=30, bold=True, color=NAVY)
    rect(slide, M, 1.38, 0.9, 0.055, TEAL, shape=MSO_SHAPE.RECTANGLE)
    if subtitle:
        tb(slide, M, 1.5, SW - 2 * M, 0.36, subtitle, size=13.5, color=GRAY)


def footer(slide, number, dark=False):
    color = GRAY_LIGHT if dark else GRAY
    accent = TEAL_BRIGHT if dark else TEAL
    tb(slide, M, 7.08, 7.0, 0.25, "Console Clinic Appointment System", size=9, color=color)
    tb(slide, SW - 1.2, 7.08, 0.6, 0.25, f"{number:02d}", size=9, bold=True, color=accent)


def picture(slide, name, x, y, width):
    path = os.path.join(IMAGES, name)
    img_w, img_h = image_size(path)
    height = width * img_h / img_w
    slide.shapes.add_picture(path, Inches(x), Inches(y), width=Inches(width), height=Inches(height))
    return height


def notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text


def stat_card(slide, x, y, w, h, value, label, accent=TEAL):
    rect(slide, x, y, w, h, WHITE, line=GRAY_LIGHT)
    rect(slide, x, y, 0.07, h, accent, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, x + 0.25, y + 0.16, w - 0.35, 0.55, value, size=30, bold=True, color=NAVY)
    tb(slide, x + 0.25, y + 0.75, w - 0.35, 0.5, label, size=10.5, color=GRAY)


def build():
    prs = Presentation()
    prs.slide_width = Inches(SW)
    prs.slide_height = Inches(SH)

    # ------------------------------------------------------------------ 1 title
    slide = new_slide(prs, NAVY)
    # slot-grid motif on the right
    gx, gy = 8.55, 1.35
    colors = [NAVY_SOFT] * 55
    for idx in (3, 8, 12, 19, 24, 30, 33, 38, 41, 47, 50, 54):
        colors[idx] = TEAL
    for idx in (16, 17, 27, 36):
        colors[idx] = AMBER
    for row in range(6):
        for col in range(9):
            idx = row * 9 + col
            x = gx + col * 0.5
            y = gy + row * 0.5
            if colors[idx] == NAVY_SOFT:
                shape = rect(slide, x, y, 0.38, 0.38, NAVY, line=NAVY_SOFT, radius=0.18)
            else:
                rect(slide, x, y, 0.38, 0.38, colors[idx], radius=0.18)
    tb(slide, 0.85, 1.05, 7.4, 0.3, "JAVA COURSEWORK  ·  CONSOLE APPLICATION", size=11.5, bold=True, color=TEAL_BRIGHT)
    rect(slide, 0.87, 1.55, 1.5, 0.06, TEAL, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, 0.85, 1.85, 7.6, 1.9, "Console Clinic\nAppointment System", size=42, bold=True, color=WHITE, spacing=0.95)
    tb(slide, 0.85, 4.15, 7.2, 1.1,
       "Scheduling rules, pricing strategies, waiting lists and JSON persistence in one Java 17 console application.",
       size=16, color=GRAY_LIGHT, spacing=1.15)
    rect(slide, 0.85, 5.55, 4.4, 0.045, TEAL_BRIGHT, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, 0.85, 5.62, 7.4, 0.67,
       "Onah Debeiwu Chrysanctus — Student1639236\n"
       "Okeke Chiemerie Miracle — Student1647235\n"
       "[NAME PENDING] — Student1627019",
       size=10, color=GRAY_LIGHT, spacing=1.05)
    tb(slide, 0.85, 6.38, 7.4, 0.35, "31 commands  ·  40 self-checks  ·  4 management reports  ·  Gson only", size=10.5, color=TEAL_BRIGHT)
    notes(slide, "Introduce yourself and the project in one sentence: a console clinic appointment system that "
                 "enforces every booking rule, prices appointments with strategies, offers cancelled slots to a "
                 "waiting list and keeps all data in JSON. Mention the stack: Java 17, Maven, Gson only.")

    # ------------------------------------------------------------------ 2 agenda
    slide = new_slide(prs)
    eyebrow(slide, "Overview")
    slide_title(slide, "What this presentation covers",
                "From the paper-diary problem to a verified, rule-enforcing system")
    agenda = [
        ("01", "The problem", "Why phone-and-paper booking breaks at scale"),
        ("02", "Architecture & domain", "Layers, packages, users, appointment types"),
        ("03", "Scheduling & booking", "15-minute grid, 12 checks, lifecycle"),
        ("04", "Pricing & waiting list", "Strategies, late fees, FIFO offers"),
        ("05", "Data, concurrency & CLI", "Atomic JSON, one lock, role-filtered commands"),
        ("06", "Verification & demo", "40 checks, scripted sessions, restart"),
    ]
    for index, (number, title, detail) in enumerate(agenda):
        col = index % 2
        row = index // 2
        x = M + col * 6.1
        y = 1.95 + row * 1.62
        rect(slide, x, y, 5.85, 1.35, WHITE, line=GRAY_LIGHT)
        tb(slide, x + 0.25, y + 0.2, 0.8, 0.5, number, size=22, bold=True, color=TEAL)
        tb(slide, x + 1.05, y + 0.17, 4.5, 0.4, title, size=16.5, bold=True, color=NAVY)
        tb(slide, x + 1.05, y + 0.62, 4.5, 0.55, detail, size=11.5, color=GRAY)
    footer(slide, 2)
    notes(slide, "Walk through the six parts. Emphasise that the presentation follows the project's story: problem, "
                 "design, the two hardest subsystems (scheduling and booking), the supporting subsystems, and the "
                 "evidence that it works.")

    # ------------------------------------------------------------------ 3 problem
    slide = new_slide(prs)
    eyebrow(slide, "Motivation")
    slide_title(slide, "The paper diary does not scale")
    rect(slide, M, 1.85, 4.1, 3.55, NAVY)
    rect(slide, M + 0.35, 2.2, 0.8, 0.055, TEAL_BRIGHT, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, M + 0.35, 2.45, 3.5, 1.5, "Six recurring\nproblems,\none system.", size=27, bold=True, color=WHITE, spacing=1.05)
    tb(slide, M + 0.35, 4.15, 3.45, 1.0, "The system enforces the rules,\nnot the receptionist.", size=13, color=TEAL_BRIGHT, spacing=1.2)
    problems = [
        ("Double bookings", "Two patients, one doctor, overlapping slots"),
        ("Wasted slots", "Cancellations stay empty, nobody calls the next patient"),
        ("No-shows", "Repeated absences are not tracked or penalised"),
        ("Inconsistent charges", "Insurance, seniors and walk-ins billed differently"),
        ("Poor visibility", "No clean daily schedule or utilisation view"),
        ("Scattered history", "Visit notes live in separate files"),
    ]
    for index, (title, detail) in enumerate(problems):
        col = index % 2
        row = index // 2
        x = 5.25 + col * 3.85
        y = 1.9 + row * 1.28
        rect(slide, x, y, 3.6, 1.08, WHITE, line=GRAY_LIGHT)
        tb(slide, x + 0.18, y + 0.15, 0.35, 0.35, f"{index + 1}", size=14, bold=True, color=TEAL)
        tb(slide, x + 0.55, y + 0.13, 2.9, 0.32, title, size=13.5, bold=True, color=NAVY)
        tb(slide, x + 0.55, y + 0.5, 2.9, 0.5, detail, size=10, color=GRAY)
    footer(slide, 3)
    notes(slide, "Tell the story from the brief: a growing clinic with a shared diary. Each card is a problem the "
                 "system solves. The punchline is that rules must be enforced by software, not by the person at the "
                 "desk, because that is where the inconsistency comes from.")

    # ------------------------------------------------------------------ 4 solution
    slide = new_slide(prs)
    eyebrow(slide, "Solution")
    slide_title(slide, "One console, three roles, every rule automated",
                "Typed commands instead of numbered menus - the CLI only parses, renders and enforces roles")
    roles = [
        ("RECEPTIONIST", TEAL, [
            "Register patients and doctors",
            "Book, move and cancel appointments",
            "Check in, mark no-shows, unblock",
            "Daily schedule, utilisation, revenue",
        ]),
        ("DOCTOR", NAVY, [
            "See today's schedule",
            "Complete checked-in visits",
            "Add notes, diagnosis, prescriptions",
            "Read history of treated patients",
        ]),
        ("PATIENT", AMBER, [
            "Book, reschedule, cancel own slots",
            "Join the waiting list",
            "Accept an offered slot",
            "View own history and inbox",
        ]),
    ]
    for index, (role, accent, items) in enumerate(roles):
        x = M + index * 4.12
        rect(slide, x, 1.95, 3.85, 3.55, WHITE, line=GRAY_LIGHT)
        rect(slide, x, 1.95, 3.85, 0.1, accent, shape=MSO_SHAPE.RECTANGLE)
        tb(slide, x + 0.26, 2.2, 3.3, 0.35, role, size=13, bold=True, color=accent)
        bullets(slide, x + 0.3, 2.7, 3.3, 2.6, items, size=11.5, gap=10, bullet_color=accent)
    chips = ["31 commands", "4 reports", "JSON files", "salted passwords", "40 self-checks", "no database"]
    chip_x = M
    for chip in chips:
        width = 0.35 + 0.105 * len(chip)
        rect(slide, chip_x, 5.85, width, 0.42, TEAL_LIGHT, radius=0.4)
        tb(slide, chip_x, 5.93, width, 0.28, chip, size=10.5, bold=True, color=TEAL, align=PP_ALIGN.CENTER)
        chip_x += width + 0.18
    footer(slide, 4)
    notes(slide, "The same data and the same rules are exposed differently to each role. Patients can only touch "
                 "their own appointments, doctors only their own visits, receptionists manage the diary. Role "
                 "filtering happens in the command registry and is re-checked inside services.")

    # ------------------------------------------------------------------ 5 architecture
    slide = new_slide(prs)
    eyebrow(slide, "Architecture")
    slide_title(slide, "Layered by design, injected at one place",
                "CLI -> services -> repository interfaces -> JSON; the model is shared by every layer")
    height = picture(slide, "slide_architecture.png", M, 1.72, 7.5)
    tb(slide, 0.65, 6.28, 7.5, 0.3, "ClinicApp.open wires repositories, services, listeners and seed data in one place.", size=9.5, color=GRAY)
    points = [
        ("1", "The CLI never touches files", "Commands call services only; formatting stays in the CLI."),
        ("2", "Services depend on interfaces", "JSON classes are hidden behind repository interfaces."),
        ("3", "Clock is injected", "No bare LocalDateTime.now(); --now makes the demo repeatable."),
        ("4", "One composition root", "ClinicApp wires every object; nothing else constructs JSON classes."),
    ]
    for index, (number, title, detail) in enumerate(points):
        y = 1.75 + index * 1.28
        tb(slide, 8.35, y, 0.4, 0.35, number, size=15, bold=True, color=TEAL)
        tb(slide, 8.75, y - 0.02, 4.05, 0.3, title, size=13, bold=True, color=NAVY)
        tb(slide, 8.75, y + 0.3, 4.05, 0.6, detail, size=10.5, color=GRAY)
    footer(slide, 5)
    notes(slide, "Explain dependency direction: the arrows only point downwards. The most important consequence is "
                 "testability and replaceability: swapping JSON for SQLite would only touch the repository package. "
                 "The Clock injection is what makes the reproducible demo possible.")

    # ------------------------------------------------------------------ 6 domain model
    slide = new_slide(prs)
    eyebrow(slide, "Domain model")
    slide_title(slide, "Two abstractions carry the clinic's rules",
                "Abstract User (three roles) and abstract AppointmentType (three booking behaviours)")
    picture(slide, "slide_uml_users.png", M, 1.75, 7.2)
    tb(slide, 0.65, 6.0, 7.2, 0.3, "One shared model; role-specific data only in the subclasses.", size=9.5, color=GRAY)
    bullets(slide, 8.2, 1.85, 4.6, 4.7, [
        "User is abstract: id, salted password hash, failed-login counter, lock flag.",
        "Receptionist, Doctor and Patient add only role-specific data.",
        "AppointmentType owns duration, base fee, break/capacity flags and eligibility.",
        "FollowUp overrides eligibility: completed visit with the same doctor within 14 days.",
        "The lifecycle is validated inside Appointment, not in the services.",
    ], size=11.5, gap=10)
    footer(slide, 6)
    notes(slide, "Two hierarchies, two reasons to change. The user hierarchy keeps identity and security in one "
                 "place. The appointment-type hierarchy means the booking service never asks 'what type is this?'; "
                 "it asks the type for its rules. That is the open/closed principle in practice.")

    # ------------------------------------------------------------------ 7 scheduling
    slide = new_slide(prs)
    eyebrow(slide, "Scheduling engine")
    slide_title(slide, "Time-slot logic, solved once",
                "Half-open intervals, a 15-minute grid, and pure functions that never touch a repository")
    picture(slide, "slide_slot_grid.png", M, 1.72, 12.0)
    cards = [
        ("Half-open overlap", "start < other.end &&\nother.start < end", TEAL),
        ("Hours, break, leave", "WorkingDay + LeavePeriod;\n15-minute boundary check", NAVY),
        ("Next free slots", "Scan forward up to 60 days;\nreturn the first three", AMBER),
    ]
    for index, (title, detail, accent) in enumerate(cards):
        x = M + index * 4.12
        rect(slide, x, 3.75, 3.85, 1.9, WHITE, line=GRAY_LIGHT)
        rect(slide, x, 3.75, 0.07, 1.9, accent, shape=MSO_SHAPE.RECTANGLE)
        tb(slide, x + 0.22, 3.95, 3.4, 0.3, title, size=12.5, bold=True, color=NAVY)
        tb(slide, x + 0.22, 4.38, 3.4, 1.0, detail, size=10, color=GRAY, font=MONO)
    footer(slide, 7)
    notes(slide, "This is the heart of the project. The overlap rule is a single method in TimeRange, used by "
                 "breaks, leave, double bookings and slot search. Show the grid: candidate starts every 15 minutes, "
                 "a 30-minute consultation occupies two cells, touching edges are legal, the break blocks normal "
                 "types but not emergencies.")

    # ------------------------------------------------------------------ 8 booking pipeline
    slide = new_slide(prs)
    eyebrow(slide, "Booking rules")
    slide_title(slide, "Twelve checks, one specific error each",
                "Nothing is mutated before every check passes; a rejected booking leaves no trace")
    picture(slide, "slide_booking_pipeline.png", 0.9, 1.72, 11.4)
    tb(slide, 0.9, 6.18, 11.4, 0.3,
       "Every failed check raises its own checked exception; nothing is written before the chain passes.",
       size=10.5, color=GRAY)
    footer(slide, 8)
    notes(slide, "Walk the numbered chain quickly, then focus on two: 'hours/break/leave' and 'doctor overlap'. "
                 "Each rule maps to a checked exception, so the console always tells the user which rule blocked "
                 "them. The waiting-list hold check (10) is subtle: an active offer reserves the slot even though no "
                 "provisional appointment is written.")

    # ------------------------------------------------------------------ 9 lifecycle
    slide = new_slide(prs)
    eyebrow(slide, "Lifecycle")
    slide_title(slide, "A small state machine, enforced in one place",
                "BOOKED -> CHECKED_IN -> COMPLETED; cancellation and no-show leave from BOOKED only")
    picture(slide, "slide_lifecycle.png", M, 1.8, 7.9)
    tb(slide, M, 5.45, 7.8, 0.3, "Transitions are validated inside Appointment.transitionTo(...).", size=9.5, color=GRAY)
    items = [
        ("Late fee", "Cancel inside 24h: 20% of the base fee (1,000 on a 5,000 consultation).", AMBER),
        ("Atomic reschedule", "New slot validated first; failure leaves the original untouched.", TEAL),
        ("No-show block", "3 no-shows in 90 days -> blocked until a receptionist unblocks.", RED),
    ]
    for index, (title, detail, accent) in enumerate(items):
        y = 1.85 + index * 1.62
        rect(slide, 8.55, y, 4.2, 1.4, WHITE, line=GRAY_LIGHT)
        rect(slide, 8.55, y, 0.07, 1.4, accent, shape=MSO_SHAPE.RECTANGLE)
        tb(slide, 8.78, y + 0.15, 3.8, 0.3, title, size=13, bold=True, color=NAVY)
        tb(slide, 8.78, y + 0.52, 3.8, 0.8, detail, size=10.5, color=GRAY)
    footer(slide, 9)
    notes(slide, "The lifecycle lives in Appointment.transitionTo, so no service can create an inconsistent state. "
                 "Point out the three consequences on the right: the late fee, atomic rescheduling and the no-show "
                 "block, which is lifted by the receptionist but keeps the history.")

    # ------------------------------------------------------------------ 10 pricing
    slide = new_slide(prs)
    eyebrow(slide, "Pricing")
    slide_title(slide, "Strategy pattern, no if/else on the category",
                "One policy per category, looked up in an EnumMap; BigDecimal only")
    picture(slide, "slide_uml_pricing.png", M, 1.85, 7.7)
    tb(slide, M, 5.35, 7.6, 0.3, "Adding a category means adding a policy and one factory entry - no billing change.", size=9.5, color=GRAY)
    rect(slide, 8.55, 1.85, 4.15, 4.3, WHITE, line=GRAY_LIGHT)
    tb(slide, 8.8, 2.0, 3.7, 0.3, "Worked example", size=13, bold=True, color=NAVY)
    tb(slide, 8.8, 2.4, 3.7, 0.3, "INSURED  ·  Consultation 5,000", size=10.5, bold=True, color=TEAL)
    tb(slide, 8.8, 2.72, 3.7, 0.4, "patient  1,500", size=17, bold=True, color=NAVY)
    tb(slide, 8.8, 3.12, 3.7, 0.4, "insurer  3,500", size=17, bold=True, color=NAVY)
    tb(slide, 8.8, 3.6, 3.7, 0.3, "insurer covers 70%, patient 30%", size=9.5, color=GRAY)
    tb(slide, 8.8, 3.95, 3.7, 0.3, "SENIOR  ·  Consultation 5,000", size=10.5, bold=True, color=AMBER)
    tb(slide, 8.8, 4.27, 3.7, 0.4, "patient  4,000", size=17, bold=True, color=NAVY)
    tb(slide, 8.8, 4.7, 3.7, 0.3, "20% discount; insurer pays 0", size=9.5, color=GRAY)
    tb(slide, 8.8, 5.2, 3.7, 0.8,
       "Late cancellation = 20% of the base fee. Money is stored as strings, split with HALF_UP, and always sums "
       "back to the base fee.", size=9.5, color=GRAY)
    footer(slide, 10)
    notes(slide, "The key design point: BillingService asks the factory for a policy and calls patientShare / "
                 "insurerShare. There is no if/else on the category. The senior category is derived from the date of "
                 "birth at pricing time and takes precedence over insurance - call this out as a documented "
                 "interpretation.")

    # ------------------------------------------------------------------ 11 waiting list
    slide = new_slide(prs)
    eyebrow(slide, "Waiting list")
    slide_title(slide, "Cancelled slots do not stay empty",
                "FIFO offers with a 2-hour hold, capped at one hour before the slot; expiry needs no threads")
    picture(slide, "slide_waitlist_flow.png", M, 1.85, 7.9)
    tb(slide, M, 5.12, 7.8, 0.3, "Accept, decline and expiry all funnel through the same offer lifecycle.", size=9.5, color=GRAY)
    bullets(slide, 8.6, 1.9, 4.2, 4.4, [
        "Join with a doctor, a date range and a MORNING/AFTERNOON window.",
        "The first matching entry gets the slot; while offered, nobody else can book it.",
        "Hold = min(offer time + 2h, slot start - 1h).",
        "Accept books through the normal validation; decline or expiry offers it to the next entry.",
        "Expiry runs at startup and before every command, so no background thread is needed.",
    ], size=11, gap=9)
    footer(slide, 11)
    notes(slide, "Explain the offer window formula with a concrete case: slot at 15:30, offer at 08:00, hold until "
                 "10:00. If the slot is only 30 minutes away, the cut-off is already in the past, so the entry is "
                 "skipped. The lazy expiry keeps the design simple and deterministic.")

    # ------------------------------------------------------------------ 12 persistence & concurrency
    slide = new_slide(prs)
    eyebrow(slide, "Data & concurrency")
    slide_title(slide, "Crash-safe JSON and exactly one winner",
                "Temp file -> atomic move; one ReentrantLock around every booking mutation")
    rect(slide, M, 1.85, 5.95, 4.55, WHITE, line=GRAY_LIGHT)
    tb(slide, M + 0.25, 2.0, 5.4, 0.3, "Persistence", size=14, bold=True, color=NAVY)
    bullets(slide, M + 0.25, 2.45, 5.45, 3.2, [
        "users.json, doctors.json, appointments.json, waitlist.json",
        "visit-records.json, notifications.json, sequences.json, activity.log",
        "ISO-8601 dates, money as strings, type discriminators",
        "Id sequences continue after a restart",
        "Corrupt file -> clear message and exit code 1 (no partial load)",
    ], size=10.5, gap=7)
    rect(slide, M + 0.25, 5.35, 5.45, 0.75, GRAY_LIGHT, radius=0.15)
    tb(slide, M + 0.4, 5.47, 5.1, 0.5,
       "Files.writeString(tmp)  ->  Files.move(tmp, target, ATOMIC_MOVE)",
       size=9.5, color=INK, font=MONO)
    rect(slide, 6.95, 1.85, 5.75, 4.55, NAVY)
    tb(slide, 7.2, 2.0, 5.2, 0.3, "Concurrency", size=14, bold=True, color=WHITE)
    tb(slide, 7.2, 2.45, 5.2, 0.3, "Two threads, one slot, one success", size=10.5, color=TEAL_BRIGHT)
    tb(slide, 7.2, 2.85, 5.25, 1.9,
       "PASS  concurrency: exactly one of two\n"
       "      simultaneous bookings succeeds\n"
       "PASS  restart: ids continue after the\n"
       "      last saved value\n"
       "SelfTest finished: 40 passed, 0 failed.",
       size=11.5, color=GRAY_LIGHT, font=MONO, spacing=1.35)
    bullets(slide, 7.2, 5.0, 5.25, 1.2, [
        "One ReentrantLock serialises booking writes.",
        "Repositories synchronise their own saves.",
    ], size=10.5, color=GRAY_LIGHT, bullet_color=TEAL_BRIGHT, gap=4)
    footer(slide, 12)
    notes(slide, "Two guarantees worth emphasising: a crash cannot leave half a JSON file because writes are "
                 "temp-file-then-atomic-move; and two receptionists cannot both book the same slot because all "
                 "write operations take one lock. The self-test proves the second with two real threads.")

    # ------------------------------------------------------------------ 13 verification
    slide = new_slide(prs)
    eyebrow(slide, "Verification")
    slide_title(slide, "Evidence, not claims",
                "A self-test harness, three scripted demo sessions and a clean Maven build")
    stats = [
        ("40 / 40", "self-test checks pass"),
        ("110", "Java source files"),
        ("9,216", "lines of Java"),
        ("31", "console commands"),
    ]
    for index, (value, label) in enumerate(stats):
        stat_card(slide, M + index * 3.08, 1.98, 2.85, 1.3, value, label)
    rect(slide, M, 3.55, 6.0, 2.95, WHITE, line=GRAY_LIGHT)
    tb(slide, M + 0.25, 3.7, 5.5, 0.3, "Covered by SelfTest", size=13, bold=True, color=NAVY)
    bullets(slide, M + 0.25, 4.1, 5.55, 2.3, [
        "Overlap, break, leave, notice, horizon, capacity, limits, blocked patient",
        "Pricing splits for all three categories",
        "Late fee, atomic reschedule, follow-up eligibility",
        "Waiting-list offer and acceptance",
        "No-show block and receptionist unblock",
        "Password hashing, concurrency, restart persistence and ids",
    ], size=10, gap=5)
    rect(slide, 6.9, 3.55, 5.8, 2.95, WHITE, line=GRAY_LIGHT)
    tb(slide, 7.15, 3.7, 5.3, 0.3, "Demo and failure checks", size=13, bold=True, color=NAVY)
    bullets(slide, 7.15, 4.1, 5.35, 2.3, [
        "demo/run-demos.ps1: 3 JVMs, one data folder",
        "Overlap -> suggestions, late fee, offer accepted",
        "No-show block -> unblock -> book again",
        "Check-in, note, complete, follow-up",
        "Restart loads 17 -> 19 -> 20 -> 21 appointments",
        "Corrupt JSON -> clear message, exit code 1",
    ], size=10, gap=5, bullet_color=AMBER)
    footer(slide, 13)
    notes(slide, "Be precise about what was verified and how: 40 programmatic checks, run against the real "
                 "repositories with a fixed clock; three scripted sessions as separate processes (which proves "
                 "persistence); and negative tests for corrupt data. Mention that JUnit was deliberately not added "
                 "because the brief allows Gson only.")

    # ------------------------------------------------------------------ 14 demo
    slide = new_slide(prs)
    eyebrow(slide, "Demo")
    slide_title(slide, "Three rules in action",
                "Reproducible because the clock is pinned to Monday 2 November 2026, 08:00")
    picture(slide, "slide_terminal_booking.png", M, 1.85, 6.55)
    picture(slide, "slide_terminal_block.png", M, 4.35, 6.55)
    steps = [
        ("1", "Overlap rejected", "Suggestions come from the same SlotFinder that powers the slots command."),
        ("2", "Waiting-list offer", "A cancellation offers the slot; accepting books it for the waiting patient."),
        ("3", "No-show block", "The third no-show blocks; the receptionist unblocks; history stays."),
    ]
    for index, (number, title, detail) in enumerate(steps):
        y = 1.9 + index * 1.55
        tb(slide, 7.6, y, 0.45, 0.4, number, size=16, bold=True, color=TEAL)
        tb(slide, 8.05, y - 0.02, 4.7, 0.3, title, size=13.5, bold=True, color=NAVY)
        tb(slide, 8.05, y + 0.34, 4.65, 0.8, detail, size=10.5, color=GRAY)
    footer(slide, 14)
    notes(slide, "Run the demo if time allows, otherwise narrate these two transcripts. Point out the fee split on "
                 "the first booking, the three suggestions on the conflict, and the block/unblock pair. Mention the "
                 "clock flag so the audience knows the result is repeatable.")

    # ------------------------------------------------------------------ 15 lessons
    slide = new_slide(prs)
    eyebrow(slide, "Reflection")
    slide_title(slide, "What went well, what stays rough, what comes next")
    columns = [
        ("What went well", TEAL, [
            "TimeRange first: one overlap rule for everything",
            "Clock injection -> deterministic tests and demo",
            "Lifecycle inside Appointment.remove inconsistent states",
            "Strategy + Factory kept booking/billing closed to change",
            "Self-test harness compensated for no JUnit",
        ]),
        ("Trade-offs", AMBER, [
            "One coarse booking lock, simple but serialising",
            "Whole-file JSON rewrites; fine for one clinic",
            "No password change / password policy",
            "Senior pricing overrides INSURED (documented)",
            "Console layout assumes a monospaced terminal",
        ]),
        ("Next steps", NAVY, [
            "Recurring appointments and week view",
            "Automatic doctor assignment by specialty",
            "History export to JSON/PDF; login reminders",
            "SQLite behind the same repository interfaces",
            "JUnit + CI running the self-test suite",
        ]),
    ]
    for index, (title, accent, items) in enumerate(columns):
        x = M + index * 4.12
        rect(slide, x, 1.85, 3.85, 4.6, WHITE, line=GRAY_LIGHT)
        rect(slide, x, 1.85, 3.85, 0.1, accent, shape=MSO_SHAPE.RECTANGLE)
        tb(slide, x + 0.25, 2.1, 3.4, 0.3, title, size=14, bold=True, color=accent)
        bullets(slide, x + 0.25, 2.55, 3.4, 3.7, items, size=10.5, gap=9, bullet_color=accent)
    footer(slide, 15)
    notes(slide, "Keep this honest. The strongest points are TimeRange, Clock injection and polymorphic rules. "
                 "The limitations are acceptable for the brief's single-clinic scale, and the future work shows "
                 "awareness of how the design would evolve (repository swap, CI, recurring appointments).")

    # ------------------------------------------------------------------ 16 thank you
    slide = new_slide(prs, NAVY)
    rect(slide, 0.85, 1.7, 1.6, 0.06, TEAL_BRIGHT, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, 0.85, 2.0, 8.6, 1.0, "Thank you", size=48, bold=True, color=WHITE)
    tb(slide, 0.85, 3.1, 8.6, 0.6, "Questions?", size=26, color=TEAL_BRIGHT)
    tb(slide, 0.85, 4.15, 8.2, 1.0,
       "The project is in clinical_java_project: source, README, UML diagrams, demo scripts and this deck.",
       size=13.5, color=GRAY_LIGHT, spacing=1.2)
    rect(slide, 0.85, 5.55, 4.6, 0.04, TEAL_BRIGHT, shape=MSO_SHAPE.RECTANGLE)
    tb(slide, 0.85, 5.72, 8.0, 0.85,
       "Onah Debeiwu Chrysanctus — Student1639236\n"
       "Okeke Chiemerie Miracle — Student1647235\n"
       "[NAME PENDING] — Student1627019",
       size=10.5, color=GRAY_LIGHT, spacing=1.05)
    footer(slide, 16, dark=True)
    notes(slide, "End with the takeaway: a small console application that still does the hard parts properly - "
                 "time arithmetic, rule enforcement, pricing strategies, waiting lists, data integrity and "
                 "concurrency. Invite questions.")

    properties = prs.core_properties
    properties.title = "Console Clinic Appointment System"
    properties.subject = "Java 17 console clinic appointment system - project presentation"
    properties.author = "Console Clinic Appointment System Team"
    properties.keywords = "Java, clinic, appointments, scheduling, pricing, JSON, Gson"
    properties.comments = "Editable presentation; speaker notes are included for every slide."

    prs.save(OUT)
    print("saved", OUT, os.path.getsize(OUT), "bytes", "slides:", len(prs.slides))


if __name__ == "__main__":
    build()
