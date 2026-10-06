# Acropolis

Administrative fortress for data and control authority.

Acropolis coordinates the protected core knowledge area. It is the administrative center above archive, cache and indexing concerns, but it should not directly perform adapter work or UI work.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

## Walking Skeleton

The first end-to-end path through Corenth is implemented here as a walking skeleton. Since #10 Slice 1 it runs through the mediated bronze archive counter; the lifecycle has no direct connector or provider path:

```text
file: URI
→ acropolis (ResourceLifecycleCoordinator) — tamias indexing rules before acquisition
→ chalcotheca MediatedResourceAccess (MediatedResourceService, the archive counter)
    → tamias (ResourceAccessPolicy) — access decision for the lifecycle actor
    → AcquisitionPort (HolkasAcquisitionPort → FileSystemResourceConnector) — internal acquisition on cache miss
→ tamias (PatternResourcePolicy) — accept/deny by rules with the actual size
→ deigma (SimpleContentDetector + PlainTextExtractor/MarkdownTextExtractor) — detect and extract, behind ContentInspector
→ chalcotheca (InMemoryResourceArchive) — snapshot/digest for change detection
→ anagraphai (LuceneLexicalIndex) — full-text indexing
→ acropolis (SearchCoordinator) — search
```

### Public API

| Class | Purpose |
|-------|---------|
| `ResourceLifecycleCoordinator` | Orchestrates the full processing pipeline for a single resource; acquires only through `MediatedResourceAccess` |
| `ProcessingResult` | Outcome of processing (INDEXED, DENIED, UNCHANGED, FAILED) |
| `ContentInspector` / `InspectionResult` | Inward port for detection and extraction (implemented by a deigma adapter at composition time) |
| `SearchCoordinator` | Thin search facade over the lexical index |

### Decision semantics

- A `tamias` indexing-rule `DENY` is a lifecycle decision for the resource: it yields `DENIED` and removes stale index entries and the snapshot.
- A `tamias` access decision that withholds the content (`DENY`, `ALLOW_CACHED_ONLY` without cached content, `REQUIRE_AUTH`, `REQUIRE_SOURCE_CHECK`) yields `DENIED` with the decision in the message and leaves global derived state untouched, because access decisions are actor-scoped. `REQUIRE_AUTH` becomes an Adyton-backed preparation step in #10 Slice 3.
- An acquisition error inside the counter yields `FAILED`.

### Composition

`acropolis` does not construct connectors, extractors, policies or the counter. ArchUnit forbids any dependency of this package on `MediatedResourceService`, `AcquisitionPort` and `holkas`. Today the counter and the lifecycle are composed only in tests (`WalkingSkeletonIntegrationTest`, `MediatedAccessWalkingSkeletonTest`); the production composition point is decided in [ADR-0001](../../docs/adr/0001-composition-root.md) and created in #10 Slice 2.

### Known gaps (left to later slices)

- No pre-acquisition size probe: the mediated contract offers no metadata operation yet, so size limits are enforced after the counter has acquired the content (`READ_METADATA`, #5/#33).
- The counter's bronze caches have no invalidation: a changed source is served from the cache within one `MediatedResourceService` instance (`knownGap_…` test in `WalkingSkeletonIntegrationTest`; #33/#5).
- Bronze content carries no name yet; the filename hint for extraction is derived from the last path segment of the `BookmarkUri` (#33 resource records).

### Running the walking skeleton

Build:

```bash
./gradlew build
```

The integration test `WalkingSkeletonIntegrationTest` proves the full path:

```bash
./gradlew :astu:acropolis:test
```

The test creates temporary `.txt` and `.md` files, processes them through the entire pipeline, and verifies that lexical search returns results linked to the original `VirtualResourceRef`.

### Configuration shape

Policy is configured via typed objects. A future YAML configuration may look like:

```yaml
tamias:
  indexing:
    defaultDecision: deny
    rules:
      - name: file-text-documents
        schemes:
          - file
        include:
          - "**/*.txt"
          - "**/*.md"
        exclude:
          - "**/.git/**"
          - "**/target/**"
          - "**/build/**"
        maxBytes: 1048576
```

For the walking skeleton, this configuration is expressed directly via `IndexingRule` and `PatternResourcePolicy` objects.
