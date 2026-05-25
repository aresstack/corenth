# Acropolis

Administrative fortress for data and control authority.

Acropolis coordinates the protected core knowledge area. It is the administrative center above archive, cache and indexing concerns, but it should not directly perform adapter work or UI work.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

## Walking Skeleton

The first end-to-end path through Corenth is implemented here as a walking skeleton:

```text
file: URI
→ holkas (FileSystemResourceConnector) — fetch raw bytes
→ deigma (SimpleContentDetector + PlainTextExtractor/MarkdownTextExtractor) — detect and extract
→ tamias (PatternResourcePolicy) — accept/deny by rules
→ chalcotheca (InMemoryResourceArchive) — snapshot/digest for change detection
→ anagraphai (LuceneLexicalIndex) — full-text indexing
→ acropolis (ResourceLifecycleCoordinator / SearchCoordinator) — orchestration and search
```

### Public API

| Class | Purpose |
|-------|---------|
| `ResourceLifecycleCoordinator` | Orchestrates the full processing pipeline for a single resource |
| `ProcessingResult` | Outcome of processing (INDEXED, DENIED, UNCHANGED, FAILED) |
| `SearchCoordinator` | Thin search facade over the lexical index |

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
