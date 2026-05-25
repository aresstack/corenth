# MainframeMate → Astu Migration Inventory

This document records the inspection of MainframeMate research sources and the migration decisions for `astu` core contracts.

## Source files inspected

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `core/.../VirtualResourceRef.java` | adapt | `com.aresstack.corenth.astu.BookmarkUri`, `ResourceScheme` | Concept of scheme-prefixed resource addressing is kept. The per-scheme `isFoo()`/`getFoo()` API is replaced with a generic scheme + path model. `local://` is replaced by canonical `file:` URI. |
| `app/.../VirtualResource.java` | do-not-copy | — | Tightly coupled to UI state (`FtpResourceState`, `NdvResourceState`, tab system). The inner model keeps only `VirtualResourceRef` (URI + kind). Transport state belongs in `proasteion`. |
| `app/.../VirtualResourceKind.java` | adapt | `com.aresstack.corenth.astu.VirtualResourceKind` | Original has only FILE/DIRECTORY. Corenth extends this to a richer structural classification (file, directory, message, dataset, stream). |
| `app/.../VirtualBackendType.java` | adapt | `com.aresstack.corenth.astu.ResourceScheme` | Backend types (LOCAL, FTP, NDV, MAIL, …) become URI scheme identifiers. The closed enum is replaced by an extensible value object that preserves the raw scheme string. |
| `app/.../BookmarkHelper.java` | do-not-copy | — | JSON-file persistence and Swing-coupled bookmark management. Belongs outside the Corenth core. Future adapter work in `proasteion` or `exedra`. |
| `app/.../BookmarkEntry.java` | adapt | `com.aresstack.corenth.astu.BookmarkUri` | The protocol-prefix concept (`local://`, `ndv://`, `ftp://`) is migrated as `BookmarkUri`. The tree/folder structure and NDV-specific metadata are not copied to `astu`. |
| `app/.../ScannedItem.java` | adapt | `com.aresstack.corenth.astu.VirtualResourceMetadata` | Lightweight scan metadata (path, lastModified, size, mimeType, directory flag) is migrated into the metadata value object with a builder pattern. |
| `app/.../DocumentSource.java` | adapter-candidate | — | Carries raw bytes + resource name. This is a transport/ingestion concept belonging in `proasteion/emporion/deigma`, not in the inner model. `astu` defines only `ResourceContentRef` as an opaque pointer. |

## New Corenth API (not derived from MainframeMate)

| Class | Purpose |
|---|---|
| `ResourceScheme` | Extensible value object for URI scheme identity. Replaces the closed `VirtualBackendType` enum with open scheme support. |
| `ConfigSnapshot` | Minimal configuration data carrier. Placeholder interface for typed module configuration; full implementation deferred (see below). |
| `ResourceFingerprint` | Content-identity hash for change detection and deduplication. Not present in MainframeMate. |
| `ResourceContentRef` | Opaque pointer to stored content. Decouples resource identity from payload delivery. |

## Key design decisions

### `file:` is canonical for local filesystem resources

Corenth uses `file:` as the canonical local filesystem scheme. MainframeMate's `local://` prefix is kept only as a legacy input alias and is normalized immediately to `file:` during parsing. Public documentation and new configuration examples must use `file:`.

### Extensible scheme model

`ResourceScheme` is a value object, not a closed enum. Well-known schemes (`file`, `ftp`, `ndv`, `mail`, `sharepoint`, `confluence`, `http`, `https`, etc.) have constants for convenience, but any scheme string is accepted. This allows future adapters to register new schemes without modifying core code.

### URI parsing uses `java.net.URI`

`BookmarkUri` delegates to `java.net.URI` for standard schemes and documents where Corenth keeps an opaque scheme-specific locator for non-standard schemes (like `ndv:` or `mail:`).

### ConfigSnapshot deferred

Full typed configuration support is deferred to a follow-up issue. The current `ConfigSnapshot` interface establishes the contract boundary: configuration is an immutable snapshot provided to modules, not a mutable settings store. The walking skeleton can proceed safely because:
1. No module currently requires runtime configuration to compile.
2. The interface is minimal and stable — implementations can be added without breaking dependents.
3. A follow-up issue should define typed configuration per module (indexing settings, cache policy, etc.).
