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
| `ProcessingResult` | Outcome of processing (INDEXED, UNCHANGED, DENIED, NO_EXTRACTABLE_CONTENT, REMOVED, CANCELLED, FAILED) with the executed steps and a typed failure |
| `ResourceProcessingPlan` / `ResourceProcessingStep` / `ResourceProcessingRun` / `ResourceProcessingRunner` | Immutable plan, step log and run summary over several resources (#10 Slice 4); no engine, no persistence |
| `ContentInspector` / `InspectionResult` | Inward port for detection and extraction; implemented in production by `DeigmaContentInspector` in `proasteion:application` (#10 Slice 2) |
| `SearchCoordinator` | Thin search facade over the lexical index |

### Decision semantics

- A `tamias` indexing-rule `DENY` is a lifecycle decision for the resource: it yields `DENIED` and removes stale index entries and the snapshot.
- A `tamias` access decision that withholds the content (`DENY`, `ALLOW_CACHED_ONLY` without cached content on either evaluation, `REQUIRE_AUTH` without a station, `REQUIRE_SOURCE_CHECK`) yields `DENIED` with the decision in the message. Access denials are never lifecycle decisions and never delete derived state, including resource-level reason codes such as `BLACKLISTED`; withdrawing an already indexed resource is decided by Tamias (#5) and executed by the lifecycle (#10) on top of the #33 resource records (`knownGap_blacklisted…` test).
- Authentication outcomes stay distinct (#10 Slice 3): cancellation `CANCELLED`, missing credential and failed authentication `FAILED` with their own reason codes. An acquisition error yields `FAILED`.

### Composition

`acropolis` does not construct connectors, extractors, policies or the counter. ArchUnit forbids any dependency of this module (outside `chalcotheca`) on `MediatedResourceService`, `AcquisitionPort` and `holkas`, and whitelists `ContentInspector` as the only inward port declared in the root package. The production composition point is `CorenthComposition` in `proasteion:application` ([ADR-0001](../../docs/adr/0001-composition-root.md), #10 Slice 2). Besides it:

- **Contract:** `MediatedResourceAccess` (chalcotheca), implemented by `MediatedResourceService`.
- **Test composition with real adapters:** `WalkingSkeletonIntegrationTest` and `MediatedAccessWalkingSkeletonTest` wire `HolkasAcquisitionPort` over `FileSystemResourceConnector` behind an anonymous permit-all `ResourceAccessPolicy`; the first also builds the `ContentInspector` from real deigma extractors.
- **Fakes only:** `MediatedLifecycleCoordinatorTest` (`RecordingMediatedAccess`, `CountingAcquisitionPort`, recording index) proves the contract shape and the decision mapping, not any integration.

### Known gaps (left to later slices)

- No pre-acquisition size probe: before Slice 1 the skeleton denied oversized files before fetching them. The mediated contract offers no metadata operation yet, so the indexing policy is evaluated first with `ResourcePolicy.SIZE_UNKNOWN` (scheme and patterns only), the counter acquires the content, and size limits are enforced afterwards. Oversized content is therefore acquired and retained in `MediatedResourceService.contentCache` for the lifetime of the service instance; the lifecycle cannot evict it (`knownGap_oversizedFile…` test). Restoring the pre-acquisition check needs a `READ_METADATA` operation on `MediatedResourceAccess` and `AcquisitionPort` (#5/#10).
- The counter's bronze caches have no invalidation: before Slice 1 every run re-read the source and a changed file was re-indexed; now a changed source is served from the cache within one `MediatedResourceService` instance and reported as `UNCHANGED` (`knownGap_changedSourceContent…` test in `WalkingSkeletonIntegrationTest`). #33 records the facts (observed versions, indexed version); deciding invalidation is #5 and executing it is #10.
- Bronze content carries no name yet; the filename hint for extraction is derived from the last path segment of the `BookmarkUri`. The #33 resource records deliberately carry no name metadata yet; stable resource metadata is a follow-up.

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
