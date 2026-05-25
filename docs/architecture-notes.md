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

A simplified flow:

1. `exedra` or another client asks for a resource or search action.
2. `emporion.holkas` opens the required connection and obtains raw data.
3. `emporion.deigma` parses transport/file-specific structure and creates a usable virtual resource.
4. `tamias` checks access rules, cache state, whitelists, blacklists and lifecycle policy.
5. `chalcotheca` stores or updates the archive/cache entry.
6. `anagraphai` updates the lexical index.
7. `pinakes` optionally updates semantic vectors and reranking material.
8. `propylaea` is used when source code requires deeper language-aware parsing.

## Open questions

- Exact Java interfaces for `VirtualResource`, bookmark URIs and resource metadata.
- Policy model for user permissions, whitelists, blacklists and cache invalidation.
- How strongly `adyton` should expose delegated operations instead of raw credentials.
- Which local AI runtime paths are supported first.
- How semantic indexes are stored, updated and invalidated.
- How source-code structures are normalized across languages.
- Which module becomes the first implementation target.
