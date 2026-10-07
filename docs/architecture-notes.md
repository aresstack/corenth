# Architecture Notes

Corenth is modeled as a small Greek city. The metaphor is not decorative; it defines boundaries.

The system separates an isolated vault, an inner city of core logic and an outer ring of adapters.

```text
com.aresstack.corenth
│
├── adyton                         Security vault
│
├── astu                           Inner city / core logic
│   ├── propylaea                  Deep source-code parsing gate
│   │
│   └── acropolis                  Administrative fortress
│       └── chalcotheca            Resource archive and cache
│           ├── tamias             Rights and cache-policy steward
│           ├── anagraphai         Full-text register
│           └── pinakes            Semantic register
│
└── proasteion                     Outer ring / ports and adapters
    ├── exedra                     Local UI adapter
    ├── katagogion                 Plugin adapter and sandbox
    └── emporion                   Data adapter harbor
        ├── holkas                 Connections for raw virtual resources
        └── deigma                 Harbor parsers for transport/file structure
```

## Boundary rules

1. `astu` must not depend on `proasteion`.
2. `adyton` must remain isolated from ordinary application logic.
3. `proasteion` translates the outside world into stable internal concepts.
4. `emporion.holkas` fetches raw resources; it does not parse them deeply.
5. `emporion.deigma` makes transported resources usable; it does not perform semantic source-code analysis.
6. `astu.propylaea` performs deeper source-code parsing and language abstraction.
7. `chalcotheca` owns resource lifecycle, cache and archive concerns.
8. `tamias` owns access and cache policy decisions.
9. `anagraphai` is for lexical/full-text indexing.
10. `pinakes` is for semantic indexing, embeddings and reranking preparation.

## Resource model

The core should work with virtual resources rather than raw files.

A virtual resource may represent:

- a local file,
- an email,
- a SharePoint document,
- a Confluence page,
- an exported spreadsheet,
- a mainframe resource,
- a source-code artifact,
- any other addressable enterprise resource.

The resource should be addressable through a bookmark-like URI scheme, for example:

```text
local://documents/specification.pdf
ndv://mainframe/system/program.cgp
https://example.internal/wiki/page
outlook://mailbox/folder/message
```

## Data flow

### Mediated bronze access (primary path)

All external access to resources flows through the mediated bronze archive counter:

```text
Exedra / Bot / Agent / Plugin / Application Service / Acropolis lifecycle
  -> MediatedResourceAccess contract (Chalcotheca)
  -> MediatedResourceService (the archive counter)
  -> Tamias ResourceAccessPolicy decision
  -> Adyton, if credentials or external delegated access are needed (planned, #10 Slice 3)
  -> AcquisitionPort (Holkas connector internally)
  -> Chalcotheca stores/updates bronze state
  -> Anagraphai / Pinakes derive indexes
```

**Forbidden direction:** `Exedra / Bot / Plugin / Application Service / Acropolis -> Holkas directly`

This is analogous to the `adyton` rule: Adyton does not hand out passwords; it mediates bounded operations. Likewise, Chalcotheca does not hand out connectors; it mediates bounded resource operations.

Since #10 Slice 1 the Acropolis lifecycle (`ResourceLifecycleCoordinator`) is itself a client of this counter: it reads through the `MediatedResourceAccess` contract and has no direct provider or connector path. ArchUnit enforces this (`ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS`): `acropolis` must not depend on `MediatedResourceService`, `AcquisitionPort` or `holkas`.

**Current state (2026-10-06):** the counter and the lifecycle are composed only in tests. There is no production composition point yet; its location is decided in [ADR-0001](adr/0001-composition-root.md) (`proasteion:application`, to be created in #10 Slice 2). The counter's in-memory bronze caches have no invalidation yet (#5 decides, #10 executes; #33 records the facts): before Slice 1 every lifecycle run re-read the source and re-indexed changed content, now a changed source is served from the cache within one service instance; likewise oversized content is acquired and retained before the size rule denies it, because the contract has no size probe. `REQUIRE_AUTH` decisions end as `DENIED` until the Adyton station exists (#10 Slice 3).

### Indexing pipeline (walking skeleton)

A simplified flow for the existing indexing pipeline:

1. `exedra` or another client asks for a resource or search action.
2. `acropolis` evaluates the `tamias` indexing rules (scheme, include/exclude patterns) and requests the content through `MediatedResourceAccess`.
3. `chalcotheca` (`MediatedResourceService`) asks `tamias` for the access decision, serves cached bronze content or acquires it through `AcquisitionPort`, where `emporion.holkas` opens the required connection (internal to Chalcotheca).
4. `tamias` indexing rules are evaluated again with the actual size.
5. `emporion.deigma` parses transport/file-specific structure and extracts text (behind the `ContentInspector` port).
6. `chalcotheca` compares the digest with the lifecycle snapshot and stores or updates the archive entry.
7. `anagraphai` updates the lexical index.
8. `pinakes` optionally updates semantic vectors and reranking material.
9. `propylaea` is used when source code requires deeper language-aware parsing.

### Key architectural rules

- **BookmarkUri** is the canonical address for every external or archived resource.
- **Indexes (Anagraphai/Pinakes) are derived views**, not the authority source for access control.
- **Holkas is internal acquisition machinery**, not a client-facing API.
- **Tamias guards every operation** at the archive counter.
- **Composition happens outside the inner city.** Concrete adapters are wired in a dedicated outer bootstrap module (ADR-0001), never in `acropolis`, `proasteion` root or exclusively in Exedra.

## Architecture test maintenance

The boundary rules are executable in `architecture-tests` (`CorenthArchitectureRulesTest`). Two lists must be maintained by hand:

- `architectureProjects` in `architecture-tests/build.gradle` names every Gradle project whose classes are scanned. A project missing from the list fails the build configuration, so a new module such as `proasteion:application` must be added deliberately.
- The secret-containment rules whitelist the vault and the trusted secret adapters by package (`adyton`, `platform.security.keepassrpc`, `platform.network` for `SecretRef`). Every new secret-source adapter from #43 must be added to these whitelists explicitly.

## Open questions

- Exact Java interfaces for `VirtualResource`, bookmark URIs and resource metadata.
- Policy model for user permissions, whitelists, blacklists and cache invalidation.
- How strongly `adyton` should expose delegated operations instead of raw credentials.
- Which local AI runtime paths are supported first.
- How semantic indexes are stored, updated and invalidated.
- How source-code structures are normalized across languages.
- Which module becomes the first implementation target.
