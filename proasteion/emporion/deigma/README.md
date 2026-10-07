# Deigma

> **δεῖγμα** — the harbor inspection hall and sample house where imported cargo is examined before entering the city.

Deigma is the shallow content detection and extraction boundary in `proasteion:emporion`. It transforms raw transported resources into usable extracted content and metadata. It inspects incoming cargo — detecting content types, extracting text and structure — without performing deep analysis.

## Responsibility

```
raw transported resource → detected content type → extracted text/blocks/metadata
```

Deigma:
- Detects MIME type / content category from filenames, hints, and magic bytes.
- Extracts structured content (text, headings, code blocks) from supported formats.
- Marks source-code files for handoff to `propylaea` (not deeply analyzed here).
- Outputs extracted content for downstream consumers (`chalcotheca`, `anagraphai`, `pinakes`).

Deigma does **not**:
- Perform Lucene indexing, semantic embedding, or reranking.
- Own resource policy, archive/cache lifecycle, or credential decisions.
- Deep-parse Natural/JCL/COBOL source code.
- Provide UI preview/rendering or settings.

## Public API

| Contract | Purpose |
|----------|---------|
| `ContentDetector` | Port for detecting content type from hints/bytes |
| `DetectedContentType` | Detected MIME type + category |
| `ContentCategory` | Broad classification enum (PLAIN_TEXT, MARKDOWN, HTML, PDF, SOURCE_CODE, …) |
| `ResourceExtractor` | Port for extracting structured content |
| `ExtractionRequest` | Request carrying `VirtualResourceRef` + raw content + hints |
| `ExtractionResult` | Outcome with extracted document or failure |
| `ExtractedDocument` | Ordered blocks of content |
| `ExtractedBlock` | Single content block (TEXT, HEADING, CODE, TABLE, …) |
| `BlockKind` | Block type enum |
| `ExtractionRegistry` | Selects appropriate extractor by content type |

## Implementations

| Class | Package | Notes |
|-------|---------|-------|
| `SimpleContentDetector` | `impl` | Extension + MIME hint detection, no external deps |
| `PlainTextExtractor` | `impl` | UTF-8 text extraction |
| `MarkdownTextExtractor` | `impl` | Lightweight heading/code-block recognition |

`SimpleContentDetector` precedence: PDF magic bytes → MIME hint → filename extension → leading HTML markup (`<!doctype html`, `<html`, `<head`, `<body`, after an optional BOM, XML declaration and comments) → `application/octet-stream`/`UNKNOWN`. Sniffing never overrides a hint that already named a type.

### Isolated extractor modules

Extractors that need a third-party library live in their own Gradle module next to this one, so the Deigma core keeps no external dependency. Each implements `ResourceExtractor` and is registered explicitly in the `ExtractionRegistry` by the composition point; no library type appears in a public signature.

| Module | Class | Library | Status |
|--------|-------|---------|--------|
| `proasteion:emporion:deigma-html` | `HtmlDocumentExtractor` | JSoup 1.17.2 (no transitive dependencies) | #42 Slice 1 |
| `proasteion:emporion:deigma-pdf` | `PdfDocumentExtractor` | PDFBox 2.0.37 (+ fontbox, commons-logging) | #42 Slice 2 |
| `proasteion:emporion:deigma-office` | `DocxDocumentExtractor`, `XlsxDocumentExtractor` | Apache POI 5.2.5 `poi-ooxml` (+ xmlbeans, commons-compress/-io/-codec/-collections4/-math3, log4j-api, SparseBitSet, curvesapi) | #42 Slice 3 |

Structured-record and Tika-based extractors are intentionally deferred (#42 Slice 4) and follow the same module pattern. All listed libraries ship Java 8 class files (major version ≤ 52).

### HTML mapping (`HtmlDocumentExtractor`)

| HTML | Deigma |
|------|--------|
| `<title>` | `ExtractedDocument.title()` (whitespace-normalized, `null` if absent) |
| charset, `<html lang>`, `<meta name=description/keywords/author>` | `METADATA` blocks with `text == null` and attributes `name`, `value` — kept out of `combinedText()` |
| `h1`–`h6` | `HEADING`, attribute `level` |
| `p` and inline text between block elements | `TEXT` (whitespace collapsed, `<br>` kept as `\n`) |
| `li` | one `LIST` block per item, attributes `ordered`, `depth`; text around a nested list becomes separate items |
| `table` | one `TABLE` block, cells tab-separated, rows newline-separated; attributes `rows`, `columns`, optional `caption`; nested tables flatten into their cell |
| `pre` | `CODE`, whitespace preserved; attribute `language` from a `language-*`/`lang-*` class |
| `script`, `style`, `noscript`, `template`, `nav`, `iframe`, `object`, `embed`, `svg`, `canvas`, `[hidden]`, comments | dropped |

Blocks follow document order; metadata blocks come first. Encoding: byte order mark → `charset` parameter of the content-type hint → `<meta>` declaration → UTF-8. Malformed markup is repaired the way browsers do (HTML5 tree building), never rejected. Outcomes: empty or invisible-only documents succeed with no visible blocks and a `NO_VISIBLE_TEXT` warning; an unusable charset hint adds `UNSUPPORTED_CHARSET_HINT`; failures start with `UNSUPPORTED_CONTENT_TYPE`, `CONTENT_TOO_LARGE` (above 64 MiB) or `HTML_PARSE_FAILED`. Links are not resolved and nothing is rendered.

### PDF mapping (`PdfDocumentExtractor`)

| PDF | Deigma |
|-----|--------|
| information-dictionary title | `ExtractedDocument.title()` |
| author, subject, keywords, creator, producer, creation/modification time (ISO-8601 UTC), page count | text-less `METADATA` blocks (`name`, `value`) |
| each page with a text layer | one `TEXT` block, attribute `page` (1-based); blank pages yield no block |

PDFBox 2.0.x is chosen over 3.x for its stable `PDDocument.load` API and class files that run on Java 8 (major version 50). Text is read with position sorting; no OCR is performed (`NO_TEXT_LAYER` warning when no page has text). Encryption: a required user password yields `PDF_ENCRYPTED` (no password is ever requested; document passwords are deliberately not an Adyton concern yet), a forbidden extraction permission yields `PDF_EXTRACTION_NOT_PERMITTED`, and an owner-password-only document is extracted with a `PDF_OWNER_PASSWORD_PROTECTED` warning. Other failures start with `UNSUPPORTED_CONTENT_TYPE`, `CONTENT_TOO_LARGE` (above 64 MiB) or `PDF_PARSE_FAILED`. The parsed document is closed on every path.

### Office mapping (`DocxDocumentExtractor`, `XlsxDocumentExtractor`)

Both extractors support only their exact OOXML MIME type; legacy binary `.doc`/`.xls`, `.pptx` and OpenDocument files find no extractor in the registry.

| DOCX | Deigma |
|------|--------|
| core properties title | `ExtractedDocument.title()` |
| author, subject, keywords, description, created, modified (ISO-8601 UTC) | text-less `METADATA` blocks |
| embedded objects (`package`/`oleObject` relations) | one `embedded` `METADATA` entry per part: part name and content type; the part itself is not extracted |
| paragraph with built-in style `heading 1`–`heading 9` | `HEADING`, `level` |
| numbered/bulleted paragraph | `LIST`, `ordered` (numbering format other than `bullet`), `depth` (`ilvl` + 1) |
| other paragraph | `TEXT` (whitespace collapsed) |
| table | one `TABLE` block, tab-separated cells, newline-separated rows; `rows`, `columns` |

Headers, footers, footnotes, comments and images are not extracted.

| XLSX | Deigma |
|------|--------|
| core properties | title + `METADATA` as for DOCX, plus `sheets` |
| each sheet with values, in workbook order | one `TABLE` block; `sheet`, `sheetIndex`, `rows`, `columns`, `hidden` for hidden sheets |
| cell | display-formatted value (`DataFormatter`, root locale); formula cells show their cached result, never the formula and never a recalculation |

Columns are absolute from column A, so interior empty cells stay empty fields; trailing empty cells and fully empty rows are omitted; empty sheets yield no block. Outcomes for both: no body text or no cell values succeed with a `NO_VISIBLE_TEXT` warning; failures start with `UNSUPPORTED_CONTENT_TYPE`, `CONTENT_TOO_LARGE` (above 64 MiB), `OFFICE_ENCRYPTED` (password-protected OLE2 container; no password is requested), `UNSUPPORTED_FORMAT` (legacy binary container) or `OFFICE_PARSE_FAILED`. The parsed document is closed on every path. POI logs through `log4j-api`; without a Log4j implementation on the classpath its status logger prints one notice and drops the messages.

## Usage with astu

Deigma uses the `astu` resource language directly:
- `VirtualResourceRef` in extraction requests/results
- `BookmarkUri` for resource addressing
- `VirtualResourceKind` for structural classification

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
