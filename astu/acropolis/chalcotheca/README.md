# Chalcotheca

Resource archive, cache and lifecycle manager.

## The bronze-archive metaphor

In ancient Corinth the *chalcotheca* (χαλκοθήκη) was the bronze storehouse — a controlled repository where valuable artifacts were catalogued, preserved and tracked over time. In Corenth's architecture the `chalcotheca` module plays the same role for virtual resources: it is the authoritative record of what has been acquired, what state each resource is in, and whether it has changed since the last processing pass.

## Resource lifecycle

A resource progresses through these states:

```
PENDING → ACQUIRED → CACHED → INDEXED
                                  ↓
                               STALE → (re-acquire) → CACHED → INDEXED
                                  ↓
                             TOMBSTONED
```

| State | Meaning |
|-------|---------|
| `PENDING` | Detected by a connector but content not yet fetched. |
| `ACQUIRED` | Content downloaded/fetched and available for processing. |
| `CACHED` | Content hashed and stored; ready for indexing. |
| `INDEXED` | Fully processed, searchable by downstream modules. |
| `STALE` | Source content changed; awaiting re-processing. |
| `TOMBSTONED` | Removed at source; retained as a marker for downstream cleanup. |

## Role in Corenth

Chalcotheca sits between resource acquisition (`holkas`/`deigma`) and indexing (`anagraphai`/`pinakes`). It provides:

- **Change detection** — content hashing via `ContentHasher` determines whether a resource needs reprocessing.
- **Lifecycle tracking** — `ArchivedResource` and `ResourceLifecycleState` record each resource's journey from discovery to indexing or deletion.
- **Snapshot history** — `ResourceSnapshot` / `ResourceVersion` capture point-in-time digest records.
- **Persistence abstraction** — `ResourceArchiveRepository` is storage-agnostic; implementations may use in-memory maps, filesystem, databases or any other backend.

## Key types

| Type | Purpose |
|------|---------|
| `ContentHasher` | Reusable SHA-256 hashing (shared with tamias, anagraphai, pinakes). |
| `ResourceDigest` | Fingerprint + size for a specific content blob. |
| `ResourceVersion` | A digest observation at a point in time. |
| `ResourceSnapshot` | Lightweight change-detection record (ref + digest + timestamp). |
| `ArchivedResource` | Full lifecycle aggregate for a tracked resource. |
| `ResourceLifecycleState` | Enum of lifecycle states. |
| `ResourceArchive` | Port for snapshot-level change detection. |
| `ResourceArchiveRepository` | Port for full lifecycle persistence. |

## Design constraints

- Compiles on Java 8; depends only inward on stable `astu` concepts.
- No assumption of H2, local filesystem, or web-only resources.
- Resources may originate from local files, mail, SharePoint, Confluence, mainframe, source artifacts or any other connector.
- Hashing and change-detection utilities are deliberately public so that `tamias`, `anagraphai` and `pinakes` can reuse them.

## Module location

This module is part of the Corenth Gradle multi-project architecture at `astu:acropolis:chalcotheca` and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

