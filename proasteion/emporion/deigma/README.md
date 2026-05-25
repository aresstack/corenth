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

PDF, DOCX, XLSX, and Tika-based extractors are intentionally deferred. When added, they belong in implementation packages with isolated dependencies.

## Usage with astu

Deigma uses the `astu` resource language directly:
- `VirtualResourceRef` in extraction requests/results
- `BookmarkUri` for resource addressing
- `VirtualResourceKind` for structural classification

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
