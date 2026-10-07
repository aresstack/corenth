# Chalcotheca

The mediated bronze archive and archive counter.

## The bronze-archive metaphor

In ancient Corinth the *chalcotheca* (χαλκοθήκη) was the bronze storehouse — a controlled repository where valuable artifacts were catalogued, preserved and tracked over time. In Corenth's architecture the `chalcotheca` module plays the same role for virtual resources: it is the authoritative record of which resources are known, which bronze versions were observed, which version is indexed and whether a resource was removed at its source.

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

**Known limitation (#5/#10):** `MediatedResourceService` keeps three in-memory payload stores (`listingCache`, `contentCache`, `metadataCache`) without invalidation or TTL. Content read once is served from the cache until `deleteEntry` removes it, so a changed source is not re-acquired within the lifetime of a service instance. `metadataCache` is never populated: `ResourceOperation.READ_METADATA` is declared in Tamias, but neither the `MediatedResourceAccess` contract / `MediatedResourceService` nor `AcquisitionPort` offer a metadata operation yet. The lifecycle also cannot evict content it caused to be cached (e.g. an oversized resource denied after acquisition), because `deleteEntry` is not on the contract. Since #33 the archive holds the authoritative resource records (see below); they are facts about payloads, not payloads, and do not record cache presence. Deciding when a cached payload is invalid belongs to Tamias (#5); consolidating the stores against the records is #10 Slice 5.

## Bronze resource shapes

| Shape | Purpose |
|-------|---------|
| `BronzeMetadata` | Existence/name/type/size/modifiedTime/source metadata |
| `BronzeListing` | Directory/container listing with child entries |
| `BronzeContent` | Content snapshot with digest/fingerprint |

All are addressable via `BookmarkUri`.

## Resource records (#33)

Chalcotheca keeps one authoritative record per `VirtualResourceRef`. The record holds **orthogonal facts**, not a linear lifecycle state machine:

| Fact | Type | Meaning |
|------|------|---------|
| Version history | `List<ResourceVersion>` | Every observed bronze version in order: monotone `sequence` (1..n, assigned by the record), `ResourceDigest`, observation time. Never empty; the last entry is the latest observed version. No payload bytes. |
| Indexed version | `IndexedVersion` (optional) | Which version derived indexes were built from, and when. |
| Removal at source | `SourceRemoval` (optional) | The resource was observed as removed at its source (tombstone). |

The facts are independent. A resource can be removed at the source while version 7 is both its latest observed and its indexed version; that is a legitimate state. A digest equal to the latest observed version creates no new version; a changed digest creates the next one.

The record deliberately contains **no decisions and no cache state**: no cached/stale flags, no access outcomes (`DENIED`, `BLACKLISTED`, `REQUIRE_AUTH`, `ALLOW_CACHED_ONLY` …), no `FAILED`, no "should reindex/reacquire". Access denials are request- and actor-specific, indexing-rule denials depend on the current policy configuration, and decisions are Tamias' job:

```
Chalcotheca (#33)  facts and history
Tamias (#5)        decisions derived from those facts (reacquire, invalidate, reindex, withdraw)
Acropolis (#10)    runs and executes those decisions
```

`ResourceArchiveRepository` is the single persistence port for records (`save`, `findByRef`, `findByUri`; no hard delete). `InMemoryResourceArchiveRepository` is the deterministic reference implementation; a persistent adapter (e.g. H2) belongs to the outer ring and must satisfy `ResourceArchiveRepositoryContractTest`.

### `ResourceArchive` compatibility facade

`ResourceArchive` stays as a temporary facade until the lifecycle moves onto the record port (#10 Slice 5). `RecordBackedResourceArchive` maps it onto the records, and `InMemoryResourceArchive` composes it with the in-memory repository. `ResourceSnapshot` is only a view of the indexed-version fact; it is not stored separately.

| Operation | Semantics |
|-----------|-----------|
| `store(snapshot)` | Observe the digest (new version only if it changed) and mark that version indexed at `indexedAtMillis`. |
| `hasChanged(ref, digest)` | Fact query: `true` without an indexed version, otherwise digest vs. the indexed version. |
| `remove(ref)` | Withdraw the indexed-version fact only; record and history stay. |
| `removeByUri(uri)` | Tombstone every record with the URI, regardless of kind; history and indexed-version fact stay. |
| `find(ref)` / `findByUri(uri)` | Indexed view only; never falls back to the latest observed version. |

## Role in Corenth

Chalcotheca is the archive counter in front of acquisition (`holkas`, used internally through the `AcquisitionPort`) and the bronze source for extraction and indexing (`deigma`, `anagraphai`/`pinakes`). It provides:

- **Change detection** — content hashing via `ContentHasher` determines whether a resource needs reprocessing.
- **Resource records** — `ArchivedResource` keeps the full bronze version history, the indexed-version fact and the removal-at-source observation for each resource.
- **Persistence abstraction** — `ResourceArchiveRepository` is storage-agnostic; implementations may use in-memory maps, databases or any other backend.

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
| `ArchivedResource` | Immutable resource record: version history, indexed-version fact, removal at source. |
| `ResourceVersion` | One observed bronze version (sequence, digest, observation time). |
| `IndexedVersion` | Fact that a version is indexed, and since when. |
| `SourceRemoval` | Observation that the resource was removed at its source. |
| `ResourceArchiveRepository` | The persistence port for resource records. |
| `ResourceArchive` | Temporary snapshot-level compatibility facade (`RecordBackedResourceArchive`, `InMemoryResourceArchive`). |
| `ResourceSnapshot` | View of a record's indexed version returned by the facade. |

## Design constraints

- Compiles on Java 8; depends only inward on stable `astu` concepts.
- No assumption of H2, local filesystem, or web-only resources.
- Resources may originate from local files, mail, SharePoint, Confluence, mainframe, source artifacts or any other connector.
- Hashing and change-detection utilities are deliberately public so that `tamias`, `anagraphai` and `pinakes` can reuse them.

## Module location

This module is part of the Corenth Gradle multi-project architecture at `astu:acropolis:chalcotheca` and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

