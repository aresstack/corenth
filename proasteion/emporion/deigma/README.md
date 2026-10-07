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

PDF, DOCX, XLSX, structured-record and Tika-based extractors are intentionally deferred (#42 Slices 2–4) and follow the same module pattern.

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

## Usage with astu

Deigma uses the `astu` resource language directly:
- `VirtualResourceRef` in extraction requests/results
- `BookmarkUri` for resource addressing
- `VirtualResourceKind` for structural classification

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
