# Astu

Inner city and core logic boundary.

Astu is the inner city of Corenth. It contains the core model and inner orchestration rules. It must remain independent from user interfaces, external protocols, plugin systems and transport-specific adapters.

## Responsibility

Astu owns **stable inner concepts** — not external protocol handling, not UI state, and not transport implementations. The contracts defined here are the common language between `proasteion` adapters and `acropolis/chalcotheca` indexing modules.

### Core contracts (package `com.aresstack.corenth.astu`)

| Class | Purpose | Research origin |
|-------|---------|----------------|
| `ResourceScheme` | Extensible URI scheme identity (value object, not closed enum) | Adapted from `VirtualBackendType`, `VirtualResourceRef` prefixes |
| `BookmarkUri` | Parsed URI with standard (`java.net.URI`) and opaque scheme support | Adapted from `VirtualResourceRef`, `BookmarkEntry` prefix concept |
| `VirtualResourceKind` | Structural classification (file, directory, message, …) | Adapted from `VirtualResourceKind` (extended beyond FILE/DIRECTORY) |
| `VirtualResourceRef` | Lightweight handle combining URI and kind | Adapted from `VirtualResource` (without UI/transport state) |
| `VirtualResourceMetadata` | Immutable scan metadata (title, size, modified, contentType) | Adapted from `ScannedItem` |
| `ResourceContentRef` | Opaque pointer to content storage | New Corenth API |
| `ResourceFingerprint` | Content-identity hash for change detection | New Corenth API |
| `ConfigSnapshot` | Immutable configuration snapshot interface | New Corenth API (placeholder) |

### Key design decisions

- **`file:` is canonical** for local filesystem resources. `local://` is a legacy alias that normalizes to `file:///` on parse.
- **ResourceScheme is extensible**: well-known schemes have constants but any string is accepted via `ResourceScheme.of(...)`.
- **Standard URIs use `java.net.URI`**: `file:`, `http:`, `https:`, `ftp:` are fully parsed. Non-standard schemes keep an opaque locator.
- **No Swing, no transport impl**: `VirtualResource` (with `FtpResourceState`, `NdvResourceState`) is not copied. Transport state belongs in `proasteion`.
- **`BookmarkHelper` not copied**: JSON-file persistence belongs outside the core.
- **`DocumentSource` not copied**: raw-byte transport belongs in `proasteion/emporion/deigma`.
- **ConfigSnapshot deferred**: minimal interface only; typed per-module config is a follow-up.

### Design rules

- No Swing or UI dependencies.
- No transport or protocol implementation.
- Java 8 compatible.
- Value objects are immutable and safe for use as map keys.
- Bookmark URI parsing is explicit and tested.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
