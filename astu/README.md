# Astu

Inner city and core logic boundary.

Astu is the inner city of Corenth. It contains the core model and inner orchestration rules. It must remain independent from user interfaces, external protocols, plugin systems and transport-specific adapters.

## Responsibility

Astu owns **stable inner concepts** — not external protocol handling, not UI state, and not transport implementations. The contracts defined here are intended as the common language between `proasteion` adapters and `acropolis/chalcotheca` indexing modules.

### Core contracts (package `com.aresstack.corenth.astu`)

| Class | Purpose |
|-------|---------|
| `ResourceScheme` | Known URI scheme prefixes for bookmark addressing |
| `BookmarkUri` | Immutable parsed bookmark-style URI |
| `VirtualResourceKind` | Logical classification of a resource (document, source, message, …) |
| `VirtualResourceRef` | Lightweight handle combining URI and kind |
| `VirtualResourceMetadata` | Immutable metadata discovered during scanning |
| `ResourceContentRef` | Pointer to content storage without embedding payload |
| `ResourceFingerprint` | Content-identity hash for change detection |

### Design rules

- No Swing or UI dependencies.
- No transport or protocol implementation.
- Java 8 compatible.
- Value objects are immutable and safe for use as map keys.
- Bookmark URI parsing is explicit and tested.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
