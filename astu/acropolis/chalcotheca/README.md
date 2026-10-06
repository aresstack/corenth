# Chalcotheca

The mediated bronze archive and archive counter.

## The bronze-archive metaphor

In ancient Corinth the *chalcotheca* (χαλκοθήκη) was the bronze storehouse — a controlled repository where valuable artifacts were catalogued, preserved and tracked over time. In Corenth's architecture the `chalcotheca` module plays the same role for virtual resources: it is the authoritative record of what has been acquired, what state each resource is in, and whether it has changed since the last processing pass.

**Chalcotheca is both the bronze archive and the archive counter.** External callers do not interact with connectors (Holkas) directly. They request resources from the bronze archive by `BookmarkUri`. The archive decides, via Tamias, whether the caller may see, list, fetch, refresh, index, or delete that resource. If acquisition is needed, Chalcotheca uses Holkas internally through the `AcquisitionPort`.

## Mediated access

```
Exedra / Bot / Agent / Plugin / Application Service / Acropolis lifecycle
  -> MediatedResourceAccess (narrow contract: readContent, listChildren)
  -> MediatedResourceService (the archive counter)
  -> Tamias access decision (ResourceAccessPolicy)
  -> Adyton, if credentials or external delegated access are needed (planned, #10 Slice 3)
  -> AcquisitionPort (Holkas connector internally)
  -> Chalcotheca stores/updates bronze (BronzeContent, BronzeListing, BronzeMetadata)
  -> Anagraphai / Pinakes derive indexes
```

**Forbidden direction:** no UI, bot, plugin, application service or lifecycle should call Holkas directly.

Clients depend on the `MediatedResourceAccess` contract, not on `MediatedResourceService`; the composition point decides how the counter is assembled. `deleteEntry` is an administrative operation of the concrete service and is deliberately not part of the contract.

**Known limitation (#33/#5):** `MediatedResourceService` keeps three in-memory stores (`listingCache`, `contentCache`, `metadataCache`) without invalidation or TTL, next to the `ResourceArchive` snapshots. Content read once is served from the cache until `deleteEntry` removes it, so a changed source is not re-acquired within the lifetime of a service instance, and there are currently two bronze truths. `metadataCache` is never populated: `ResourceOperation.READ_METADATA` is declared in Tamias, but neither the `MediatedResourceAccess` contract / `MediatedResourceService` nor `AcquisitionPort` offer a metadata operation yet (#5/#33). The lifecycle also cannot evict content it caused to be cached (e.g. an oversized resource denied after acquisition), because `deleteEntry` is not on the contract. #33 makes the archive the authoritative record and #5 adds change detection and invalidation; the stores are consolidated against those contracts rather than replaced ad hoc.

## Bronze resource shapes

| Shape | Purpose |
|-------|---------|
| `BronzeMetadata` | Existence/name/type/size/modifiedTime/source metadata |
| `BronzeListing` | Directory/container listing with child entries |
| `BronzeContent` | Content snapshot with digest/fingerprint |

All are addressable via `BookmarkUri`.

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

Chalcotheca is the archive counter in front of acquisition (`holkas`, used internally through the `AcquisitionPort`) and the bronze source for extraction and indexing (`deigma`, `anagraphai`/`pinakes`). It provides:

- **Change detection** — content hashing via `ContentHasher` determines whether a resource needs reprocessing.
- **Lifecycle tracking** — `ArchivedResource` and `ResourceLifecycleState` record each resource's journey from discovery to indexing or deletion.
- **Current version tracking** — `ResourceSnapshot` / `ResourceVersion` record the most recent digest for a resource. Full version history (retaining all past versions) is left for a later persistent archive implementation.
- **Persistence abstraction** — `ResourceArchiveRepository` is storage-agnostic; implementations may use in-memory maps, filesystem, databases or any other backend.

## Key types

| Type | Purpose |
|------|---------|
| `MediatedResourceAccess` | Narrow client contract (`readContent`, `listChildren`) implemented by the counter; what lifecycle use cases depend on. |
| `MediatedResourceService` | The archive counter: all external access flows through here. |
| `MediatedResult` | Result of a mediated operation (success/denied/error). |
| `AcquisitionPort` | Internal acquisition port (Holkas implements this). |
| `BronzeMetadata` | Existence/name/type/size metadata for a resource. |
| `BronzeListing` | Directory listing with child entries. |
| `BronzeContent` | Content snapshot with digest. |
| `ContentHasher` | Reusable SHA-256 hashing (shared with tamias, anagraphai, pinakes). |
| `ResourceDigest` | Fingerprint + size for a specific content blob. |
| `ResourceVersion` | Current digest observation at a point in time. |
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

