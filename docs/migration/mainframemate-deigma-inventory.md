# MainframeMate → deigma Migration Inventory

This document records the inspection of MainframeMate ingestion/extraction source
files and the migration decisions for the `deigma` module.

## Scope

`deigma` is the shallow content detection and extraction boundary:

```
raw transported resource → detected content type → extracted text/blocks/metadata
```

It does not own indexing, rendering, UI, or deep source-code analysis.

## Source files inspected

All files listed in the issue were reviewed from the `research/` directory.

## Migration inventory

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `ingestion/model/DocumentSource.java` | adapt | `deigma:ExtractionRequest` | Raw-input model concept preserved. Replaced with `ExtractionRequest` carrying `VirtualResourceRef` from astu instead of parallel identity. |
| `ingestion/model/document/ExtractedDocument.java` | adapt | `deigma:ExtractedDocument` | Document model preserved but simplified. UI-rendering hooks removed; block model made UI-independent. |
| `ingestion/model/document/ExtractedBlock.java` | adapt | `deigma:ExtractedBlock` | Block concept preserved with kind enum. Swing-specific formatting removed. |
| `ingestion/model/document/ContentType.java` | adapt | `deigma:DetectedContentType`, `deigma:ContentCategory` | Split into value object + category enum. Added SOURCE_CODE category for propylaea routing. |
| `ingestion/port/ContentDetector.java` | adapt | `deigma:ContentDetector` | Port interface preserved. Method signature simplified to filename + MIME hint + byte prefix. |
| `ingestion/port/DocumentExtractor.java` | adapt | `deigma:ResourceExtractor` | Port interface preserved. Renamed to align with astu resource language. |
| `ingestion/port/ExtractorRegistry.java` | adapt | `deigma:ExtractionRegistry` | Registry concept preserved. Registration-order priority retained. |
| `ingestion/infrastructure/extractor/PlainTextExtractor.java` | adapt | `deigma:impl:PlainTextExtractor` | Implementation preserved. Simplified to single-block output. |
| `ingestion/infrastructure/extractor/MarkdownExtractor.java` | adapt | `deigma:impl:MarkdownTextExtractor` | Lightweight heading/code-block recognition kept. Full AST parsing not copied. |
| `ingestion/infrastructure/extractor/HtmlExtractor.java` | adapter-candidate | (deferred) | Would require JSoup or similar. Deferred to later PR. |
| `ingestion/infrastructure/extractor/PdfExtractor.java` | adapter-candidate | (deferred) | Requires PDFBox. Deferred — will go in isolated impl package. |
| `ingestion/infrastructure/extractor/DocxExtractor.java` | adapter-candidate | (deferred) | Requires POI. Deferred — will go in isolated impl package. |
| `ingestion/infrastructure/extractor/XlsxExtractor.java` | adapter-candidate | (deferred) | Requires POI. Deferred — will go in isolated impl package. |
| `ingestion/infrastructure/extractor/TikaFallbackExtractor.java` | adapter-candidate | (deferred) | Optional Tika fallback. Deferred — replaceable by design via registry. |
| `ingestion/infrastructure/SimpleContentDetector.java` | adapt | `deigma:impl:SimpleContentDetector` | Extension/MIME detection logic adapted. Tika-based detection deferred. |
| `ingestion/infrastructure/render/DocumentRenderer.java` | do-not-copy | — | UI rendering. Not part of extraction boundary. |
| `ingestion/infrastructure/render/SwingPreviewPanel.java` | do-not-copy | — | Swing UI. Explicitly excluded per issue. |
| `ingestion/infrastructure/render/HtmlPreviewRenderer.java` | do-not-copy | — | UI rendering for HTML preview. |
| `ingestion/usecase/IngestDocumentUseCase.java` | adapt | (deferred) | Orchestration use case. Will be adapted when walking skeleton integrates deigma. |
| `ingestion/usecase/ExtractContentUseCase.java` | adapt | (deferred) | Content extraction orchestration. Pattern preserved in registry + extractor design. |
| `files/codec/RecordStructureCodec.java` | adapter-candidate | (deferred) | Fixed-format file parsing concept. Useful for mainframe record structures. Deferred. |
| `files/ftpconfig/FtpTransferConfig.java` | do-not-copy | — | Transport configuration. Belongs in proasteion connectors, not deigma. |
| `files/ftpconfig/FtpRecordFormat.java` | do-not-copy | — | Transport-level record format. Not extraction concern. |
| `mail/model/MailMessage.java` | do-not-copy | — | Mail model. Belongs in mail connector module. |
| `mail/model/MailAttachment.java` | adapter-candidate | (deferred) | Attachment concept could feed into deigma extraction, but connector owns it. |
| `plugins/excelimport/ExcelImportCommand.java` | do-not-copy | — | Plugin command wiring / UI. Explicitly excluded. |
| `plugins/excelimport/ExcelImportDialog.java` | do-not-copy | — | Swing UI dialog. Explicitly excluded. |
| `plugins/excelimport/ExcelSheetParser.java` | adapter-candidate | (deferred) | Parsing logic useful but requires POI. Deferred to isolated impl package. |

## New Corenth APIs (not present in MainframeMate)

| Contract | Reason |
|---|---|
| `ExtractionRequest` | Combines astu `VirtualResourceRef` with content and hints. Replaces coupled `DocumentSource` + transport state. |
| `ExtractionResult` | Explicit success/failure result with warnings. MainframeMate used exceptions for failures. |
| `ContentCategory` | Enum for routing decisions (especially SOURCE_CODE → propylaea). Not present in original. |
| `BlockKind` | Typed enum replacing string-based block type in original. |

## Decisions summary

| Category | Count |
|---|---|
| adapt | 9 |
| adapter-candidate | 7 |
| do-not-copy | 8 |
| new-corenth-api | 4 |
