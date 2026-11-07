"""Generate the diagrams and terminal captures used by the DOCX report and the PPTX deck.

Run with the bundled Python (Pillow only):
    <bundled-python> docs/build/make_assets.py
"""
from __future__ import annotations

import os
import textwrap
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "images"))
os.makedirs(OUT, exist_ok=True)

NAVY = (16, 42, 67)
NAVY_SOFT = (36, 59, 83)
TEAL = (13, 110, 118)
TEAL_DARK = (9, 79, 86)
TEAL_LIGHT = (224, 242, 241)
AMBER = (201, 138, 4)
AMBER_LIGHT = (254, 243, 199)
BLUE_LIGHT = (219, 234, 254)
GRAY = (71, 85, 105)
GRAY_LINE = (203, 213, 225)
GRAY_LIGHT = (241, 245, 249)
WHITE = (255, 255, 255)
RED = (185, 28, 28)
GREEN = (21, 128, 61)

FONT_DIR = r"C:\Windows\Fonts"
F_CALIBRI = os.path.join(FONT_DIR, "calibri.ttf")
F_CALIBRI_B = os.path.join(FONT_DIR, "calibrib.ttf")
F_CALIBRI_L = os.path.join(FONT_DIR, "calibril.ttf")
F_CONSOLA = os.path.join(FONT_DIR, "consola.ttf")
F_CONSOLA_B = os.path.join(FONT_DIR, "consolab.ttf")


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt, max_width: int) -> list[str]:
    words = text.split()
    lines, current = [], ""
    for word in words:
        candidate = word if not current else current + " " + word
        if draw.textlength(candidate, font=fnt) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def rich_text(draw, box, lines, fnt, fill, align="center", spacing=1.25):
    x0, y0, x1, y1 = box
    rendered = []
    for item in lines:
        if isinstance(item, tuple):
            rendered.append(item)
        else:
            rendered.append((item, fnt, fill))
    heights = [draw.textbbox((0, 0), t, font=f)[3] - draw.textbbox((0, 0), t, font=f)[1] for t, f, _ in rendered]
    total = sum(heights) + spacing * (len(rendered) - 1)
    y = y0 + (y1 - y0 - total) / 2
    for (text, f, color), h in zip(rendered, heights):
        w = draw.textlength(text, font=f)
        x = x0 + (x1 - x0 - w) / 2 if align == "center" else x0 + 10
        draw.text((x, y), text, font=f, fill=color)
        y += h + spacing


def box(draw, rect, fill, outline=None, radius=18, width=3):
    draw.rounded_rectangle(rect, radius=radius, fill=fill, outline=outline, width=width)


def title_block(draw, w, title, subtitle=None):
    draw.text((70, 46), title, font=font(F_CALIBRI_B, 58), fill=NAVY)
    if subtitle:
        draw.text((72, 122), subtitle, font=font(F_CALIBRI, 30), fill=GRAY)
    draw.rounded_rectangle((70, 176, 250, 188), radius=6, fill=TEAL)


def arrow(draw, start, end, color=NAVY, width=4, head=16, dashed=False):
    import math
    x0, y0 = start
    x1, y1 = end
    if dashed:
        total = math.dist(start, end)
        step = 22
        travelled = 0
        while travelled < total - head * 2:
            t0 = travelled / total
            t1 = min((travelled + step) / total, 1)
            draw.line([x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0,
                       x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1], fill=color, width=width)
            travelled += step
    else:
        draw.line([start, end], fill=color, width=width)
    angle = math.atan2(y1 - y0, x1 - x0)
    left = (x1 - head * math.cos(angle - 0.45), y1 - head * math.sin(angle - 0.45))
    right = (x1 - head * math.cos(angle + 0.45), y1 - head * math.sin(angle + 0.45))
    draw.polygon([end, left, right], fill=color)


def label(draw, xy, text, size=24, color=GRAY, anchor="mm", bold=False):
    draw.text(xy, text, font=font(F_CALIBRI_B if bold else F_CALIBRI, size), fill=color, anchor=anchor)


# --------------------------------------------------------------------------- architecture
def architecture():
    W, H = 2400, 1620
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Layered architecture", "CLI -> services -> repository interfaces -> JSON files; model shared by every layer")

    x0, x1 = 90, 1780
    cx = (x0 + x1) / 2
    rows = [
        (235, 375, NAVY, WHITE, "CLI  |  com.clinic.cli",
         "ConsoleUI · CommandParser · CommandRegistry · 30 typed command classes (Command pattern)", 27),
        (435, 590, TEAL, WHITE, "Services  |  com.clinic.service",
         "AuthService · SchedulingService · BookingService · WaitlistService\nBillingService · VisitService · ReportService", 23),
        (800, 1010, None, None, None, None, None),
        (1060, 1180, AMBER_LIGHT, NAVY, "Repository interfaces  |  com.clinic.repository",
         "UserRepository · AppointmentRepository · WaitlistRepository\nVisitRecordRepository · NotificationRepository · ActivityLogRepository", 23),
        (1230, 1340, GRAY_LIGHT, NAVY, "JSON implementations  |  com.clinic.repository",
         "JsonSupport (atomic temp file -> move) · SequenceStore · one Json*Repository per interface", 22),
        (1390, 1520, (226, 232, 240), NAVY, "Persistent store  |  data/",
         "users.json · doctors.json · appointments.json · waitlist.json\nvisit-records.json · notifications.json · sequences.json · activity.log", 22),
    ]
    for y0, y1, fill, fg, head, body, body_size in rows:
        if fill is None:
            cards = [
                ("scheduling", "TimeRange · SlotFinder\nScheduleRules · SlotCell", TEAL_LIGHT, TEAL),
                ("pricing", "PricingPolicy +\nStandard / Insured / Senior", TEAL_LIGHT, TEAL),
                ("event", "EventBus · ClinicEvent\nNotificationInbox · ActivityLog", TEAL_LIGHT, TEAL),
                ("factory", "UserFactory\nAppointmentTypeFactory\nPricingPolicyFactory", TEAL_LIGHT, TEAL),
            ]
            cw = (x1 - x0 - 3 * 26) / 4
            for i, (name, text, card_fill, card_line) in enumerate(cards):
                cx0 = x0 + i * (cw + 26)
                box(d, (cx0, y0, cx0 + cw, y1), card_fill, card_line, radius=16, width=3)
                d.text((cx0 + 20, y0 + 14), name, font=font(F_CALIBRI_B, 28), fill=TEAL_DARK)
                d.multiline_text((cx0 + 20, y0 + 54), text, font=font(F_CALIBRI, 21), fill=GRAY, spacing=5)
            continue
        box(d, (x0, y0, x1, y1), fill, None, radius=18)
        d.text((x0 + 30, y0 + 18), head, font=font(F_CALIBRI_B, 32), fill=fg)
        d.multiline_text((x0 + 30, y0 + 72), body, font=font(F_CALIBRI, body_size), fill=fg, spacing=6)

    for ya, yb, text in [(375, 435, "commands"), (590, 800, "uses"), (1010, 1060, "depends on interfaces"),
                         (1180, 1230, "implemented by"), (1340, 1390, "atomic writes")]:
        arrow(d, (cx, ya + 6), (cx, yb - 6), color=TEAL_DARK, width=5, head=19)
        d.text((cx + 22, (ya + yb) / 2), text, font=font(F_CALIBRI, 23), fill=GRAY, anchor="lm")

    # model panel on the right, clear of every row
    mx0, mx1 = 1840, 2310
    my0, my1 = 435, 1010
    box(d, (mx0, my0, mx1, my1), WHITE, TEAL, radius=18, width=4)
    d.text((mx0 + 24, my0 + 16), "model", font=font(F_CALIBRI_B, 34), fill=NAVY)
    d.text((mx0 + 24, my0 + 60), "entities + enums", font=font(F_CALIBRI, 22), fill=GRAY)
    d.multiline_text((mx0 + 24, my0 + 110),
                     "User · Receptionist\nDoctor · Patient\nAppointment\nAppointmentType +\nConsultation · FollowUp\nEmergency\nWorkingDay · LeavePeriod\nWaitlistEntry\nVisitRecord\nRole · status enums",
                     font=font(F_CALIBRI, 21), fill=GRAY, spacing=5)
    d.line([(x1 + 10, 500), (mx0 - 10, 500)], fill=GRAY_LINE, width=3)
    d.line([(x1 + 10, 900), (mx0 - 10, 900)], fill=GRAY_LINE, width=3)


    d.text((70, H - 44), "ClinicApp / Main is the composition root: the only place that constructs concrete JSON classes (manual dependency injection).",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "architecture.png"))

# --------------------------------------------------------------------------- UML: users
def uml_users():
    W, H = 2400, 1560
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "User hierarchy", "One abstract base class, three roles, role-specific data and behaviour")

    def uml_box(x0, y0, x1, y1, name, abstract, attrs, accent=NAVY):
        box(d, (x0, y0, x1, y1), WHITE, accent, radius=10, width=4)
        d.rectangle((x0 + 4, y0 + 4, x1 - 4, y0 + 62), fill=accent)
        label = name + ("  (abstract)" if abstract else "")
        d.text(((x0 + x1) / 2, y0 + 20), label, font=font(F_CALIBRI_B, 32), fill=WHITE, anchor="mm")
        d.text((x0 + 20, y0 + 78), attrs, font=font(F_CONSOLA, 21), fill=GRAY, spacing=7)

    uml_box(760, 250, 1640, 560, "User", True,
            "- int id\n- String name\n- String passwordHash\n- String passwordSalt\n- int failedLoginAttempts\n- boolean locked\n+ Role getRole()\n+ recordFailedLogin(int)\n+ unlock()")

    uml_box(130, 760, 730, 1010, "Receptionist", False, "+ getRole() = RECEPTIONIST\n\nManages patients, doctors,\nbookings and reports")
    uml_box(890, 760, 1490, 1080, "Doctor", False,
            "- String specialty\n- Map<DayOfWeek, WorkingDay>\n- List<LeavePeriod> leave\n- int dailyCapacity\n+ worksOn(DayOfWeek)\n+ isOnLeave(LocalDate)")
    uml_box(1650, 760, 2270, 1080, "Patient", False,
            "- LocalDate dateOfBirth\n- PatientCategory category\n- String insurer\n- boolean blocked\n- List<LocalDateTime> noShows\n+ pricingCategoryOn(date)\n+ recordNoShow(when)\n+ block() / unblock()")

    arrow(d, (1100, 560), (430, 758), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1200, 560), (1190, 758), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1300, 560), (1960, 758), color=NAVY_SOFT, width=4, head=18)

    # doctor composition
    uml_box(300, 1180, 860, 1380, "WorkingDay", False, "- DayOfWeek day\n- LocalTime start / end\n- breakStart / breakEnd\n+ hasBreak()")
    uml_box(930, 1180, 1490, 1380, "LeavePeriod", False, "- LocalDate from\n- LocalDate to\n+ covers(LocalDate)")
    arrow(d, (1040, 1082), (580, 1178), color=GRAY, width=3, head=15)
    arrow(d, (1340, 1082), (1210, 1178), color=GRAY, width=3, head=15)
    d.text((760, 1125), "1 .. *", font=font(F_CALIBRI, 22), fill=GRAY)

    d.text((70, H - 62), "Passwords are stored as SHA-256 hash + per-user salt; the lockout counter and no-show history survive restarts.",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "uml_users.png"))


# --------------------------------------------------------------------------- UML: appointment types
def uml_appointments():
    W, H = 2400, 1420
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Appointment types", "Duration, fee and eligibility belong to the type classes, not to the booking service")

    box(d, (690, 235, 1710, 470), WHITE, NAVY, radius=10, width=4)
    d.rectangle((694, 239, 1706, 300), fill=NAVY)
    d.text((1200, 256), "AppointmentType  (abstract)", font=font(F_CALIBRI_B, 32), fill=WHITE, anchor="mm")
    d.text((720, 320), "+ AppointmentTypeCode getCode()      + Duration getDuration()\n"
                       "+ BigDecimal getBaseFee()             + boolean mayUseBreak()\n"
                       "+ boolean ignoresDailyCapacity()      + boolean sameDayOnly()\n"
                       "+ checkEligibility(BookingContext)", font=font(F_CONSOLA, 20), fill=GRAY, spacing=8)

    def type_box(x0, name, rule, fee, dur, extra_lines, color):
        box(d, (x0, 650, x0 + 620, 960), WHITE, color, radius=10, width=4)
        d.rectangle((x0 + 4, 654, x0 + 616, 714), fill=color)
        d.text((x0 + 310, 670), name, font=font(F_CALIBRI_B, 32), fill=WHITE, anchor="mm")
        d.text((x0 + 26, 738), fee, font=font(F_CALIBRI_B, 34), fill=NAVY)
        d.text((x0 + 26, 790), dur, font=font(F_CALIBRI, 26), fill=GRAY)
        d.multiline_text((x0 + 26, 840), extra_lines, font=font(F_CALIBRI, 23), fill=GRAY, spacing=6)

    type_box(120, "Consultation", "", "5,000", "30 minutes", "Standard booking rules.\nNo extra eligibility checks.", NAVY)
    type_box(890, "FollowUp", "", "3,000", "15 minutes", "Only within 14 days of a\nCOMPLETED visit with the\nsame doctor.", TEAL)
    type_box(1660, "Emergency", "", "10,000", "20 minutes", "Same-day only, may use the\nbreak, ignores daily capacity,\nno minimum notice.", AMBER)

    arrow(d, (850, 470), (430, 648), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1200, 470), (1200, 648), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1550, 470), (1970, 648), color=NAVY_SOFT, width=4, head=18)

    # appointment + factory
    box(d, (120, 1050, 1080, 1300), WHITE, NAVY, radius=10, width=4)
    d.rectangle((124, 1054, 1076, 1112), fill=NAVY)
    d.text((600, 1068), "Appointment", font=font(F_CALIBRI_B, 30), fill=WHITE, anchor="mm")
    d.text((146, 1130), "id · patientId · doctorId · type code · start · end · status\nbaseFee · patientShare · insurerShare · lateCancellationFee\ntransitionTo(status, at) enforces the lifecycle",
           font=font(F_CONSOLA, 20), fill=GRAY, spacing=8)

    box(d, (1180, 1050, 2280, 1240), TEAL_LIGHT, TEAL, radius=12, width=3)
    d.text((1210, 1068), "AppointmentTypeFactory", font=font(F_CALIBRI_B, 30), fill=TEAL_DARK)
    d.multiline_text((1210, 1115), "forCode(code) -> stateless singleton\nparse(String) -> InvalidInputException\nall() -> 3 types",
                     font=font(F_CONSOLA, 20), fill=GRAY, spacing=7)
    arrow(d, (1180, 1160), (1082, 1170), color=TEAL, width=3, head=14, dashed=True)
    d.text((1130, 1205), "creates", font=font(F_CALIBRI, 20), fill=GRAY)

    d.text((70, H - 58), "BookingService asks the type object for its rules instead of branching on the type code.",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "uml_appointments.png"))


# --------------------------------------------------------------------------- UML: pricing
def uml_pricing():
    W, H = 2300, 1200
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Pricing strategies", "Strategy pattern: one class per category, looked up in an EnumMap")

    box(d, (620, 235, 1680, 470), WHITE, NAVY, radius=10, width=4)
    d.rectangle((624, 239, 1676, 298), fill=NAVY)
    d.text((1150, 254), "PricingPolicy  (interface)", font=font(F_CALIBRI_B, 32), fill=WHITE, anchor="mm")
    d.text((650, 320), "PatientCategory category()\nBigDecimal patientShare(BigDecimal baseFee)\nBigDecimal insurerShare(BigDecimal baseFee)",
           font=font(F_CONSOLA, 22), fill=GRAY, spacing=8)

    def policy(x0, name, rule, example, color):
        box(d, (x0, 620, x0 + 640, 900), WHITE, color, radius=10, width=4)
        d.rectangle((x0 + 4, 624, x0 + 636, 684), fill=color)
        d.text((x0 + 320, 638), name, font=font(F_CALIBRI_B, 30), fill=WHITE, anchor="mm")
        d.text((x0 + 28, 710), rule, font=font(F_CALIBRI_B, 27), fill=NAVY)
        d.multiline_text((x0 + 28, 760), example, font=font(F_CONSOLA, 22), fill=GRAY, spacing=8)

    policy(120, "StandardPricing", "Patient pays 100%", "Consultation 5,000\npatient 5,000 · insurer 0", NAVY)
    policy(830, "InsuredPricing", "Insurer 70% / patient 30%", "Consultation 5,000\npatient 1,500 · insurer 3,500", TEAL)
    policy(1540, "SeniorPricing", "20% discount", "Consultation 5,000\npatient 4,000 · insurer 0", AMBER)

    arrow(d, (820, 470), (440, 618), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1150, 470), (1150, 618), color=NAVY_SOFT, width=4, head=18)
    arrow(d, (1480, 470), (1860, 618), color=NAVY_SOFT, width=4, head=18)

    box(d, (120, 960, 980, 1080), TEAL_LIGHT, TEAL, radius=12, width=3)
    d.text((146, 980), "PricingPolicyFactory", font=font(F_CALIBRI_B, 28), fill=TEAL_DARK)
    d.text((146, 1022), "EnumMap<PatientCategory, PricingPolicy>", font=font(F_CONSOLA, 19), fill=GRAY)
    d.text((146, 1052), "registers one policy per category", font=font(F_CALIBRI, 20), fill=GRAY)
    box(d, (1180, 960, 2180, 1080), GRAY_LIGHT, None, radius=12)
    d.text((1204, 980), "BillingService", font=font(F_CALIBRI_B, 28), fill=NAVY)
    d.text((1204, 1022), "quote(patient, type, date) -> PricingQuote\nlateCancellationFee(type) = 20% of base",
           font=font(F_CONSOLA, 19), fill=GRAY, spacing=6)
    arrow(d, (982, 1020), (1178, 1020), color=TEAL, width=3, head=14)
    d.text((1080, 986), "looks up", font=font(F_CALIBRI, 20), fill=GRAY)

    d.text((70, H - 48), "All amounts use BigDecimal (scale 2, HALF_UP); no double/float anywhere and no if/else on the category.",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "uml_pricing.png"))


# --------------------------------------------------------------------------- lifecycle
def lifecycle():
    W, H = 2400, 1250
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Appointment lifecycle", "Every status change is validated in Appointment.transitionTo(...)")

    states = {
        "BOOKED": (170, 430, 620, 610, NAVY),
        "CHECKED_IN": (930, 430, 1420, 610, TEAL),
        "COMPLETED": (1730, 430, 2240, 610, GREEN),
        "CANCELLED": (420, 850, 980, 1030, RED),
        "NO_SHOW": (1250, 850, 1810, 1030, (120, 53, 15)),
    }
    for name, (x0, y0, x1, y1, color) in states.items():
        final = name in ("COMPLETED", "CANCELLED", "NO_SHOW")
        box(d, (x0, y0, x1, y1), color if not final else WHITE, color, radius=16, width=5 if final else 3)
        d.text(((x0 + x1) / 2, (y0 + y1) / 2), name, font=font(F_CALIBRI_B, 38), fill=WHITE if not final else color, anchor="mm")

    def edge(start, end, text, color, dashed=False, offset=(0, 0), curve=None):
        arrow(d, start, end, color=color, width=4, head=18, dashed=dashed)
        d.text(((start[0] + end[0]) / 2 + offset[0], (start[1] + end[1]) / 2 + offset[1]), text,
               font=font(F_CALIBRI, 24), fill=GRAY, anchor="mm")

    edge((622, 520), (926, 520), "checkin", TEAL, offset=(0, -24))
    edge((1424, 500), (1726, 500), "complete", GREEN, offset=(0, -26))
    edge((330, 616), (700, 844), "cancel", RED, offset=(-90, 12))
    edge((560, 616), (1280, 844), "noshow (> 15 min after start)", (120, 53, 15), offset=(-40, 34))

    # final state note
    box(d, (1760, 700, 2300, 1010), GRAY_LIGHT, None, radius=14)
    d.text((1788, 724), "Final states", font=font(F_CALIBRI_B, 30), fill=NAVY)
    d.multiline_text((1788, 776), "COMPLETED, CANCELLED and NO_SHOW\nare terminal. Every other transition\nthrows\nInvalidStatusTransitionException.",
                     font=font(F_CALIBRI, 24), fill=GRAY, spacing=8)

    d.text((70, 1160), "Cancelling inside 24 hours charges 20% of the base fee; a reschedule validates the new slot before replacing the old booking.",
           font=font(F_CALIBRI, 23), fill=GRAY)
    img.save(os.path.join(OUT, "lifecycle.png"))


# --------------------------------------------------------------------------- booking pipeline
def booking_pipeline():
    W, H = 2600, 1180
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Booking validation pipeline", "One lock, one ordered rule chain: every check fails with a specific custom exception")

    steps = [
        ("1", "Patient exists", "PatientNotFound"),
        ("2", "Not blocked", "PatientBlocked"),
        ("3", "Doctor exists", "DoctorNotFound"),
        ("4", "15-min boundary", "InvalidInput"),
        ("5", "Type eligibility", "FollowUpNotEligible"),
        ("6", "Booking window", "SlotUnavailable"),
        ("7", "Hours / break / leave", "OutsideWorkingHours / DoctorOnLeave"),
        ("8", "Doctor overlap", "SlotUnavailable + suggestions"),
        ("9", "Patient overlap", "SlotUnavailable"),
        ("10", "Wait-list hold", "SlotUnavailable"),
        ("11", "Daily capacity", "SlotUnavailable"),
        ("12", "3 upcoming limit", "BookingLimitExceeded"),
    ]
    cols, cw, ch, gap = 4, 590, 190, 26
    x_start, y_start = 80, 250
    for i, (num, name, err) in enumerate(steps):
        row, col = divmod(i, cols)
        x0 = x_start + col * (cw + gap)
        y0 = y_start + row * (ch + 44)
        box(d, (x0, y0, x0 + cw, y0 + ch), TEAL_LIGHT if row % 2 == 0 else GRAY_LIGHT, TEAL, radius=14, width=3)
        d.ellipse((x0 + 18, y0 + 18, x0 + 66, y0 + 66), fill=TEAL)
        d.text((x0 + 42, y0 + 41), num, font=font(F_CALIBRI_B, 26), fill=WHITE, anchor="mm")
        d.text((x0 + 82, y0 + 22), name, font=font(F_CALIBRI_B, 27), fill=NAVY)
        d.text((x0 + 82, y0 + 66), err, font=font(F_CONSOLA, 18), fill=GRAY)
        if i < len(steps) - 1 and col < cols - 1:
            arrow(d, (x0 + cw + 3, y0 + ch / 2), (x0 + cw + gap - 3, y0 + ch / 2), color=GRAY, width=3, head=13)
        elif col == cols - 1 and row < 2:
            last_x = x_start + (cols - 1) * (cw + gap) + cw / 2
            first_x = x_start + cw / 2
            mid_y = y0 + ch + 24
            d.line([(last_x, y0 + ch + 2), (last_x, mid_y)], fill=GRAY, width=4)
            d.line([(last_x, mid_y), (first_x, mid_y)], fill=GRAY, width=4)
            arrow(d, (first_x, mid_y + 2), (first_x, y0 + ch + 46), color=GRAY, width=4, head=15)

    # then
    box(d, (80, 980, 2520, 1080), NAVY, None, radius=14)
    d.text((2520 / 2, 1030), "then:   PricingQuote (strategy)  ->  save appointment atomically  ->  publish APPOINTMENT_BOOKED  ->  inbox + activity log",
           font=font(F_CALIBRI_B, 29), fill=WHITE, anchor="mm")

    d.text((80, 1120), "All write operations run inside a single ReentrantLock, so concurrent requests for one slot cannot both succeed.",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "booking_pipeline.png"))


# --------------------------------------------------------------------------- waitlist flow
def waitlist_flow():
    W, H = 2400, 1300
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "Waiting-list offer lifecycle", "FIFO offers, 2-hour holds, lazy expiry (startup + before every command)")

    def step_box(x0, y0, x1, y1, head, body, color=NAVY, fill=WHITE):
        box(d, (x0, y0, x1, y1), fill, color, radius=14, width=4)
        d.text((x0 + 24, y0 + 16), head, font=font(F_CALIBRI_B, 29), fill=color)
        d.multiline_text((x0 + 24, y0 + 62), body, font=font(F_CALIBRI, 22), fill=GRAY, spacing=6)

    step_box(80, 250, 700, 430, "1  Slot freed", "cancel() or reschedule() completes,\nold slot is free again", RED)
    step_box(850, 250, 1560, 430, "2  FIFO scan", "first WAITING entry matching doctor,\ndate range and MORNING/AFTERNOON window", NAVY)
    step_box(1710, 250, 2320, 430, "3  Offer created", "hold = min(now + 2h, slot - 1h)\nstatus OFFERED, inbox message sent", TEAL)

    step_box(80, 560, 700, 760, "Accept", "bookingGateway.bookReservedSlot(...)\ncreates the appointment, entry ACCEPTED", GREEN)
    step_box(850, 560, 1560, 760, "Decline", "entry DECLINED, slot re-offered to the\nnext matching entry", (120, 53, 15))
    step_box(1710, 560, 2320, 760, "Expire", "at startup and before every command:\nentry EXPIRED, slot re-offered", AMBER)

    arrow(d, (702, 340), (846, 340), color=GRAY, width=4, head=16)
    arrow(d, (1562, 340), (1706, 340), color=GRAY, width=4, head=16)
    for x in (390, 1205, 2015):
        arrow(d, (x, 434), (x, 556), color=GRAY, width=4, head=16)

    box(d, (80, 880, 2320, 1080), TEAL_LIGHT, TEAL, radius=14, width=3)
    d.text((110, 902), "Acceptance is booked through the normal booking validation", font=font(F_CALIBRI_B, 30), fill=TEAL_DARK)
    d.multiline_text((110, 952), "The offer reserves the slot logically: while an offer is active, other patients cannot book that slot.\n"
                                  "Only the offered patient's acceptance writes the new appointment. Expiry needs no background thread.",
                     font=font(F_CALIBRI, 24), fill=GRAY, spacing=8)

    d.text((80, 1150), "waitlist join <doctorId> <fromDate> <toDate> [--time=MORNING|AFTERNOON]   ·   waitlist accept <entryId>   ·   waitlist leave <entryId>",
           font=font(F_CALIBRI, 22), fill=GRAY)
    img.save(os.path.join(OUT, "waitlist_flow.png"))


# --------------------------------------------------------------------------- terminal screenshots
def terminal(lines, name, title, width=2100, line_h=42, pad=34, font_size=27):
    fnt = font(F_CONSOLA, font_size)
    fnt_b = font(F_CONSOLA_B, font_size)
    height = pad + 92 + len(lines) * line_h + pad
    img = Image.new("RGB", (width, height), (15, 23, 42))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, width - 1, height - 1), radius=24, fill=(15, 23, 42))
    # title bar
    d.rounded_rectangle((0, 0, width - 1, 70), radius=24, fill=(30, 41, 59))
    d.rectangle((0, 40, width - 1, 70), fill=(30, 41, 59))
    d.ellipse((26, 24, 48, 46), fill=(248, 113, 113))
    d.ellipse((60, 24, 82, 46), fill=(251, 191, 36))
    d.ellipse((94, 24, 116, 46), fill=(52, 211, 153))
    d.text((140, 24), title, font=font(F_CALIBRI, 25), fill=(148, 163, 184))
    y = 92 + pad - 10
    for line in lines:
        color = (226, 232, 240)
        stripped = line.strip()
        if stripped.startswith("Error:"):
            color = (252, 165, 165)
        elif stripped.startswith("PASS") or "ALL SELF-TEST" in line or stripped.startswith("Booked") or stripped.startswith("Saved") or stripped.startswith("Marked"):
            color = (134, 239, 172)
        elif stripped.startswith("Slot unavailable") or "Next free slots" in line:
            color = (253, 224, 71)
        elif stripped.startswith("clinic") or stripped.startswith("Dr.") or "Free consultation" in line or "Daily capacity" in line:
            color = (125, 211, 252)
        elif stripped.startswith("=") or stripped.startswith("Loaded") or stripped.startswith("SelfTest"):
            color = (148, 163, 184)
        d.text((pad, y), line, font=fnt, fill=color)
        y += line_h
    img.save(os.path.join(OUT, name))


def read_capture(path):
    with open(path, "rb") as handle:
        data = handle.read()
    if data.startswith(b"\xff\xfe") or data.startswith(b"\xfe\xff"):
        text = data.decode("utf-16")
    else:
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            text = data.decode("utf-16")
    return [line.rstrip("\r\n") for line in text.splitlines()]


def terminals():
    raw = read_capture(os.path.join(HERE, "out_slots.txt"))
    start = next(i for i, line in enumerate(raw) if "Dr. Bello (General Practice) - Monday" in line)
    slots_lines = raw[start:start + 5]
    terminal(slots_lines, "terminal_slots.png", "clinic[RECEPTIONIST]  slots 2 2026-11-02", width=2100)

    raw = read_capture(os.path.join(HERE, "out_slots.txt"))
    start = next(i for i, line in enumerate(raw) if line.startswith("Booked appointment"))
    booking_lines = raw[start:start + 5]
    terminal(booking_lines, "terminal_booking.png", "book / overlap rejection", width=2100)

    raw = read_capture(os.path.join(HERE, "out_noshow.txt"))
    picked = []
    for line in raw:
        if any(key in line for key in ("Marked appointment", "blocked from booking", "unblocked", "No-show report", "Overall no-show rate", "Blocked patients")):
            picked.append(line)
    terminal(picked[:7], "terminal_noshow.png", "no-show block -> receptionist unblock", width=2100)

    raw = read_capture(os.path.join(HERE, "out_selftest.txt"))
    terminal(raw[-4:], "terminal_selftest.png", "com.clinic.SelfTest", width=2100)



# --------------------------------------------------------------------------- slide-only assets
def slot_grid():
    W, H = 2600, 640
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    title_block(d, W, "15-minute grid", "Candidate starts, durations and one break - the same rule for every doctor")

    x0, x1 = 140, 2460
    y = 300
    cell_h = 118
    slots = 32  # 08 hours, 09:00-17:00
    step = (x1 - x0) / slots
    booked = {0, 1, 4, 5, 24, 25, 26, 27}  # 09:00-09:30, 10:00-10:30, 15:00-16:00
    for i in range(slots):
        x = x0 + i * step
        if 16 <= i < 20:
            fill, outline, text_color = (254, 243, 199), AMBER, AMBER
        elif i in booked:
            fill, outline, text_color = NAVY, NAVY, WHITE
        else:
            fill, outline, text_color = GRAY_LIGHT, GRAY_LINE, GRAY
        d.rounded_rectangle((x + 3, y, x + step - 3, y + cell_h), radius=8, fill=fill, outline=outline, width=2)
        if i in booked:
            d.text((x + step / 2, y + cell_h / 2), "B", font=font(F_CALIBRI_B, 26), fill=text_color, anchor="mm")
    # labels every hour plus break
    for hour in range(9, 18):
        i = (hour - 9) * 4
        d.text((x0 + i * step, y - 34), f"{hour:02d}:00", font=font(F_CALIBRI, 23), fill=GRAY)
    d.text((x0 + 16 * step + 0.6 * step, y + cell_h + 16), "BREAK 13:00-14:00", font=font(F_CALIBRI_B, 24), fill=AMBER)
    d.text((x0 + 0.6 * step, y + cell_h + 16), "booked", font=font(F_CALIBRI_B, 24), fill=NAVY)
    d.text((x0 + 4.5 * step, y + cell_h + 16), "booked", font=font(F_CALIBRI_B, 24), fill=NAVY)

    # duration bracket under a consultation
    bx0 = x0 + 4 * step
    bx1 = x0 + 6 * step
    by = y + cell_h + 74
    d.line([(bx0, by), (bx1, by)], fill=TEAL, width=4)
    d.line([(bx0, by - 12), (bx0, by + 12)], fill=TEAL, width=4)
    d.line([(bx1, by - 12), (bx1, by + 12)], fill=TEAL, width=4)
    d.text(((bx0 + bx1) / 2, by + 34), "30-min consultation = 2 cells", font=font(F_CALIBRI_B, 24), fill=TEAL, anchor="mm")

    # top annotations
    d.text((x0, 236), "candidate starts: every 15 minutes (32 cells in an 8-hour day)", font=font(F_CALIBRI, 25), fill=GRAY)
    d.text((x0 + 1180, 236), "touching edges are allowed: 10:30 follows 10:00-10:30", font=font(F_CALIBRI, 25), fill=TEAL)
    img.save(os.path.join(OUT, "slot_grid.png"))


def slide_terminal(lines, name, title, width=1600, font_size=30, line_h=56, pad=40):
    fnt = font(F_CONSOLA, font_size)
    title_fnt = font(F_CALIBRI, 22)
    height = 70 + pad + len(lines) * line_h + pad
    img = Image.new("RGB", (width, height), (15, 23, 42))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, width - 1, height - 1), radius=22, fill=(15, 23, 42))
    d.rounded_rectangle((0, 0, width - 1, 66), radius=22, fill=(30, 41, 59))
    d.rectangle((0, 36, width - 1, 66), fill=(30, 41, 59))
    d.ellipse((22, 22, 42, 42), fill=(248, 113, 113))
    d.ellipse((52, 22, 72, 42), fill=(251, 191, 36))
    d.ellipse((82, 22, 102, 42), fill=(52, 211, 153))
    d.text((118, 22), title, font=title_fnt, fill=(148, 163, 184))
    y = 66 + pad
    for line in lines:
        color = (226, 232, 240)
        stripped = line.strip()
        if stripped.startswith("Error:"):
            color = (252, 165, 165)
        elif stripped.startswith("$"):
            color = (125, 211, 252)
        elif "Booked" in stripped or "unblocked" in stripped or "Marked" in stripped:
            color = (134, 239, 172)
        elif "Next free slots" in stripped or stripped.startswith("10:30"):
            color = (253, 224, 71)
        d.text((pad, y), line, font=fnt, fill=color)
        y += line_h
    img.save(os.path.join(OUT, name))


def slide_terminals():
    slide_terminal([
        "book 2 10:00 CONSULTATION --patient=7",
        "Booked #18: Ibrahim Musa, 10:00-10:30.",
        "Patient pays 1,500, insurer 3,500.",
        "book 2 10:15 CONSULTATION --patient=8",
        "Slot unavailable: Dr. Bello is booked at 10:15.",
        "Next free slots: 10:30, 10:45, 11:00",
    ], "slide_terminal_booking.png", "clinic[RECEPTIONIST]")

    slide_terminal([
        "$ noshow 6",
        "Marked appointment #6 as NO_SHOW (Tunde Balogun).",
        "$ book 2 2026-11-04T09:00 CONSULTATION",
        "Error: Tunde Balogun is blocked from booking.",
        "$ patient unblock 9",
        "Patient #9 (Tunde Balogun) has been unblocked.",
    ], "slide_terminal_block.png", "no-show block -> unblock")



def slide_variants():
    import numpy as np
    targets = {
        "architecture.png": 215,
        "uml_users.png": 215,
        "uml_pricing.png": 215,
        "lifecycle.png": 215,
        "waitlist_flow.png": 215,
        "booking_pipeline.png": 215,
        "slot_grid.png": 195,
    }
    slide_bg = np.array([247, 249, 252], dtype=np.uint8)
    for name, top in targets.items():
        path = os.path.join(OUT, name)
        img = Image.open(path).convert("RGB")
        img = img.crop((0, top, img.width, img.height))
        data = np.array(img)
        mask = np.all(data >= 248, axis=2)
        data[mask] = slide_bg
        Image.fromarray(data).save(os.path.join(OUT, "slide_" + name))
        print("  slide_" + name)

def main():
    architecture()
    uml_users()
    uml_appointments()
    uml_pricing()
    lifecycle()
    booking_pipeline()
    waitlist_flow()
    terminals()
    slot_grid()
    slide_terminals()
    slide_variants()
    print("assets written to", OUT)
    for name in sorted(os.listdir(OUT)):
        print(" ", name, os.path.getsize(os.path.join(OUT, name)), "bytes")


if __name__ == "__main__":
    main()
