---
name: document-generation
description: Create, read, and edit Office documents (Word .docx, Excel .xlsx, PowerPoint .pptx) and PDFs programmatically — with structure-first planning, XML-safe editing, and validation before claiming success. Use whenever the user wants a document file as input or output: resumes, cover letters, reports, spreadsheets, slide decks, PDFs. Inherited from anthropics/skills (docx, xlsx, pptx, pdf) and MiniMax-AI/skills (minimax-docx, minimax-xlsx, minimax-pdf, pptx-generator).
---

# Document Generation

Create, read, and edit documents people actually open: Word (.docx), Excel (.xlsx),
PowerPoint (.pptx), and PDF. The quality bar is "opens cleanly in the real app, looks
intentional, survives round-trip".

## 1. Plan the Document Before Generating

Never generate a document file first and fix it later. Plan first:

- **Audience + purpose** — a resume for a human recruiter, a data report for an
  analyst, a deck for an investor. This decides structure, not just styling.
- **Outline** — section order, headings, what content goes where. For decks: slide
  types (cover, agenda, content, section divider, summary) with variety.
- **Content** — write the real copy. A document with placeholder text ("Lorem ipsum")
  is a draft, not a deliverable. If the user hasn't given content, draft it and flag
  what needs their confirmation.

## 2. Choose the Right Format

| Want | Format | Tool approach |
|---|---|---|
| Resumes, cover letters, reports | .docx | OpenXML (Python `python-docx` or direct XML) |
| Tabular data, analysis | .xlsx | OpenXML (Python `openpyxl`) |
| Slides, pitch decks | .pptx | PptxGenJS (JS) or `python-pptx` |
| Fixed layout, printing, sharing | .pdf | HTML→PDF (weasyprint) or PDF generation libs |

**Never convert** from a rendered image or screenshot — always generate from structured
content so it stays editable and searchable.

## 3. Format Fundamentals

- **Styling via templates/styles, not one-off runs.** Define named styles (Headings,
  Body, Table) once; apply them. Per-run formatting everywhere is how documents break.
- **Layout dimensions:** A4 = 210×297mm, Letter = 8.5×11in, 16:9 slide = 10×5.625in.
  Set page size and margins explicitly — default templates vary.
- **Tables:** define column widths, header styling, and alignment. Check for overflow.
- **Colors/fonts:** a coherent palette (see distinctive-design for tokens); system-safe
  fonts unless embedding.
- **Accessibility:** real headings (not bold text), alt text for images, contrast.

## 4. XML-Safe Editing

.docX/.xlsx/.pptx are ZIP files of XML. When editing existing files:

- **Don't string-replace blindly** — text may be split across XML runs
  (`<w:r><w:t>Hel</w:t></w:r><w:r><w:t>lo</w:t></w:r>`). "Hello" is NOT a substring.
- **Work at the paragraph/run level** with a proper library, or operate on the XML
  document tree — never raw regex on the unzipped XML.
- **Preserve relationships** (images, styles, fonts) — repacking must keep
  `[Content_Types].xml` and rels intact.
- After any edit: **re-validate** the file opens (see section 6).

## 5. Multi-Slide/Page Workflows

For large documents, plan and orchestrate:

- Classify each slide/page type, enforce visual variety (no 20 content slides that look
  identical).
- Generate in parallel where possible (each slide's XML is independent), then assemble.
- Clean orphaned assets (unused images/styles) before repacking.

## 6. Validate Before Claiming Success

The gate — never claim "done" until:

1. **Open test:** the file opens in the target application without a repair prompt
   (or programmatically: unzip + parse all XML successfully).
2. **Content check:** headings/content match the outline; no placeholder text; no
   missing sections.
3. **Visual check:** for anything visual (decks, PDFs, designed docs), render to image
   and look — screenshots over assumptions.
4. **Round-trip check (edits):** open, edit, save, reopen — nothing lost.

If any check fails, fix the cause, not the symptom, and re-validate.
