"""Formatting helpers for the project documentation DOCX (python-docx only)."""
from __future__ import annotations

import os

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

NAVY = RGBColor(0x10, 0x2A, 0x43)
NAVY_HEX = "102A43"
TEAL = RGBColor(0x0D, 0x6E, 0x76)
TEAL_HEX = "0D6E76"
TEAL_LIGHT_HEX = "E0F2F1"
GRAY = RGBColor(0x47, 0x55, 0x69)
GRAY_LIGHT_HEX = "F1F5F9"
AMBER_HEX = "FEF3C7"
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

FONT_BODY = "Calibri"
FONT_CODE = "Consolas"


def _set_cell_fill(cell, hex_color: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tc_pr.append(shd)


def _set_table_borders(table, color: str = "CBD5E1", size: str = "4") -> None:
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = OxmlElement(f"w:{edge}")
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), size)
        element.set(qn("w:space"), "0")
        element.set(qn("w:color"), color)
        borders.append(element)
    tbl_pr.append(borders)


def _no_borders(table) -> None:
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        element = OxmlElement(f"w:{edge}")
        element.set(qn("w:val"), "nil")
        borders.append(element)
    tbl_pr.append(borders)

def _set_cell_margins(cell, top: int = 70, bottom: int = 70, left: int = 100, right: int = 100) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for name, value in (("top", top), ("bottom", bottom), ("start", left), ("end", right)):
        node = OxmlElement(f"w:{name}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        mar.append(node)
    tc_pr.append(mar)


def _shade_paragraph(paragraph, hex_color: str) -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    p_pr.append(shd)


def _bottom_border(paragraph, color: str = "0D6E76", size: str = "8") -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    p_bdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), size)
    bottom.set(qn("w:space"), "3")
    bottom.set(qn("w:color"), color)
    p_bdr.append(bottom)
    p_pr.append(p_bdr)


def _add_field(paragraph, instruction: str, placeholder: str = "1") -> None:
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = f" {instruction} "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = placeholder
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instr)
    run._r.append(separate)
    run._r.append(text)
    run._r.append(end)


def setup_document() -> Document:
    document = Document()
    section = document.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.1)
    section.bottom_margin = Cm(1.9)
    section.left_margin = Cm(2.2)
    section.right_margin = Cm(2.2)
    section.header_distance = Cm(0.9)
    section.footer_distance = Cm(0.8)

    styles = document.styles
    normal = styles["Normal"]
    normal.font.name = FONT_BODY
    normal.font.size = Pt(10.5)
    normal.font.color.rgb = RGBColor(0x1F, 0x29, 0x37)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.15

    for name, size, color, before, after in (
            ("Heading 1", 16, NAVY, 18, 8),
            ("Heading 2", 12.5, TEAL, 12, 4),
            ("Heading 3", 11, NAVY, 8, 3)):
        style = styles[name]
        style.font.name = FONT_BODY
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = color
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    caption = styles.add_style("Figure Caption", 1)  # 1 = paragraph style
    caption.font.name = FONT_BODY
    caption.font.size = Pt(8.5)
    caption.font.italic = True
    caption.font.color.rgb = GRAY
    caption.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.paragraph_format.space_before = Pt(2)
    caption.paragraph_format.space_after = Pt(10)
    return document


def add_header_footer(document: Document, title: str) -> None:
    section = document.sections[0]

    header = section.header.paragraphs[0]
    header.text = ""
    left = header.add_run(title)
    left.font.size = Pt(8.5)
    left.font.color.rgb = GRAY
    right = header.add_run("\t\tProject Documentation")
    right.font.size = Pt(8.5)
    right.font.color.rgb = GRAY
    _bottom_border(header, color="CBD5E1", size="4")

    footer = section.footer.paragraphs[0]
    footer.text = ""
    left = footer.add_run("Console Clinic Appointment System  ·  v1.0")
    left.font.size = Pt(8)
    left.font.color.rgb = GRAY
    footer.add_run("\t\t")
    page = footer.add_run("Page ")
    page.font.size = Pt(8)
    page.font.color.rgb = GRAY
    _add_field(footer, "PAGE")
    of = footer.add_run(" of ")
    of.font.size = Pt(8)
    of.font.color.rgb = GRAY
    _add_field(footer, "NUMPAGES")

def add_cover(document: Document, meta: list[tuple[str, str]]) -> None:
    spacer = document.add_paragraph()
    spacer.paragraph_format.space_after = Pt(70)

    label = document.add_paragraph()
    run = label.add_run("JAVA COURSEWORK  ·  CONSOLE APPLICATION")
    run.font.size = Pt(10)
    run.font.bold = True
    run.font.color.rgb = TEAL

    title = document.add_paragraph()
    title.paragraph_format.space_before = Pt(6)
    title.paragraph_format.space_after = Pt(4)
    run = title.add_run("Console Clinic\nAppointment System")
    run.font.size = Pt(34)
    run.font.bold = True
    run.font.color.rgb = NAVY

    subtitle = document.add_paragraph()
    run = subtitle.add_run("Project documentation: design, implementation and verification")
    run.font.size = Pt(14)
    run.font.color.rgb = GRAY
    _bottom_border(subtitle, color=TEAL_HEX, size="18")

    stack = document.add_paragraph()
    stack.paragraph_format.space_before = Pt(10)
    run = stack.add_run("Java 17  ·  Maven  ·  Gson  ·  JSON persistence  ·  No database, no GUI")
    run.font.size = Pt(11)
    run.font.color.rgb = TEAL
    run.font.bold = True

    document.add_paragraph()
    for key, value in meta:
        paragraph = document.add_paragraph()
        paragraph.paragraph_format.space_after = Pt(6)
        paragraph.paragraph_format.tab_stops.add_tab_stop(Cm(4.6), 0)
        run = paragraph.add_run(key)
        run.font.size = Pt(9.5)
        run.font.bold = True
        run.font.color.rgb = GRAY
        run = paragraph.add_run("\t" + value)
        run.font.size = Pt(10)
        run.font.color.rgb = NAVY

    document.add_paragraph()
    note = document.add_paragraph()
    note.paragraph_format.space_before = Pt(26)
    run = note.add_run("Submission note: replace the bracketed placeholders on this page with your own "
                       "name, student number, course and submission date before exporting to PDF.")
    run.font.size = Pt(8.5)
    run.font.italic = True
    run.font.color.rgb = GRAY
    document.add_page_break()


def add_toc(document: Document, entries: list[tuple[str, str]]) -> None:
    heading = document.add_paragraph()
    run = heading.add_run("Contents")
    run.font.size = Pt(16)
    run.font.bold = True
    run.font.color.rgb = NAVY
    heading.paragraph_format.space_after = Pt(10)
    _bottom_border(heading, color=TEAL_HEX, size="8")

    for number, title in entries:
        paragraph = document.add_paragraph()
        paragraph.paragraph_format.space_after = Pt(2)
        paragraph.paragraph_format.tab_stops.add_tab_stop(Cm(1.3), 0)
        run = paragraph.add_run(number)
        run.font.size = Pt(9.5)
        run.font.bold = True
        run.font.color.rgb = TEAL
        run = paragraph.add_run("\t" + title)
        run.font.size = Pt(9.5)
        run.font.color.rgb = RGBColor(0x1F, 0x29, 0x37)
    document.add_page_break()


def heading(document: Document, text: str, level: int = 1) -> None:
    paragraph = document.add_heading(text, level=level)
    if level == 1:
        _bottom_border(paragraph, color="CBD5E1", size="6")


def para(document: Document, text: str, bold_lead: str | None = None, space_after: int = 6):
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(space_after)
    if bold_lead:
        run = paragraph.add_run(bold_lead)
        run.bold = True
        run.font.color.rgb = NAVY
    paragraph.add_run(text)
    return paragraph


def bullets(document: Document, items: list[str]) -> None:
    for item in items:
        paragraph = document.add_paragraph(item, style="List Bullet")
        paragraph.paragraph_format.space_after = Pt(2)
        paragraph.paragraph_format.line_spacing = 1.1


def numbered(document: Document, items: list[str]) -> None:
    for item in items:
        paragraph = document.add_paragraph(item, style="List Number")
        paragraph.paragraph_format.space_after = Pt(2)
        paragraph.paragraph_format.line_spacing = 1.1


def add_table(document: Document, headers: list[str], rows: list[list[str]],
              widths: list[float] | None = None, font_size: float = 8.5,
              header_bg: str = NAVY_HEX, band: bool = True) -> None:
    table = document.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    _set_table_borders(table)
    if widths:
        for i, width in enumerate(widths):
            table.columns[i].width = Cm(width)
    header_cells = table.rows[0].cells
    for i, text in enumerate(headers):
        cell = header_cells[i]
        if widths:
            cell.width = Cm(widths[i])
        _set_cell_fill(cell, header_bg)
        _set_cell_margins(cell)
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        run = paragraph.add_run(text)
        run.font.size = Pt(font_size)
        run.font.bold = True
        run.font.color.rgb = WHITE
    for row_index, values in enumerate(rows):
        cells = table.add_row().cells
        for i, value in enumerate(values):
            cell = cells[i]
            if widths:
                cell.width = Cm(widths[i])
            if band and row_index % 2 == 1:
                _set_cell_fill(cell, GRAY_LIGHT_HEX)
            _set_cell_margins(cell)
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            run = paragraph.add_run(str(value))
            run.font.size = Pt(font_size)
            run.font.color.rgb = RGBColor(0x1F, 0x29, 0x37)
    document.add_paragraph().paragraph_format.space_after = Pt(2)


def add_caption(document: Document, text: str) -> None:
    paragraph = document.add_paragraph(text, style="Figure Caption")
    paragraph.paragraph_format.space_after = Pt(10)


def add_figure(document: Document, path: str, caption: str, width_cm: float = 16.2) -> None:
    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(4)
    paragraph.paragraph_format.space_after = Pt(2)
    run = paragraph.add_run()
    run.add_picture(path, width=Cm(width_cm))
    add_caption(document, caption)


def add_code(document: Document, code: str, caption: str | None = None) -> None:
    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(2)
    paragraph.paragraph_format.space_after = Pt(2)
    paragraph.paragraph_format.line_spacing = 1.0
    paragraph.paragraph_format.keep_with_next = True
    paragraph.paragraph_format.keep_together = True
    _shade_paragraph(paragraph, "F8FAFC")
    run = paragraph.add_run(code)
    run.font.name = FONT_CODE
    run.font.size = Pt(8.2)
    run.font.color.rgb = RGBColor(0x1E, 0x29, 0x3B)
    if caption:
        add_caption(document, caption)


def add_note(document: Document, title: str, text: str, fill: str = TEAL_LIGHT_HEX) -> None:
    table = document.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(table, color=TEAL_HEX, size="4")
    cell = table.rows[0].cells[0]
    cell.width = Cm(16.6)
    _set_cell_fill(cell, fill)
    _set_cell_margins(cell, 110, 110, 160, 160)
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_after = Pt(3)
    run = paragraph.add_run(title)
    run.font.size = Pt(9.5)
    run.font.bold = True
    run.font.color.rgb = TEAL
    paragraph = cell.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(0)
    run = paragraph.add_run(text)
    run.font.size = Pt(9)
    run.font.color.rgb = RGBColor(0x1F, 0x29, 0x37)
    document.add_paragraph().paragraph_format.space_after = Pt(2)


def page_break(document: Document) -> None:
    document.add_page_break()


def set_properties(document: Document, title: str, subject: str, author: str) -> None:
    core = document.core_properties
    core.title = title
    core.subject = subject
    core.author = author
    core.keywords = "Java, clinic, appointments, JSON, Gson, Maven, scheduling"
    core.comments = "Project documentation generated from the implementation in this repository."
