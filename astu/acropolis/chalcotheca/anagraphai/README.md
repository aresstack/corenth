# Anagraphai

**The classical full-text register for Corenth.**

Anagraphai owns lexical indexing, keyword search, and text retrieval over accepted virtual resources. It is the natural home for Lucene-backed indexing, tokenization, metadata fields and classic full-text retrieval within the `astu:acropolis:chalcotheca` boundary.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

Anagraphai provides:

- **Lexical indexing** — index documents tied to `VirtualResourceRef` identities from `astu`.
- **Full-text search** — BM25-based keyword retrieval returning scored results with excerpts.
- **Resource lifecycle** — update and remove indexed content by resource reference.
- **Technology isolation** — consumers depend only on the `LexicalIndex` port interface; Lucene internals stay encapsulated.

## Public API

| Class | Purpose |
|-------|---------|
| `LexicalIndex` | Port interface for indexing and retrieval (extends `Closeable`) |
| `LexicalDocument` | Document model with resource ref, title, content type, and text chunks |
| `LexicalChunk` | A numbered text segment within a document |
| `LexicalQuery` | Search query with text and result limit |
| `LexicalSearchResult` | Scored result with resource ref, chunk index, excerpt, title, and content type |
| `LexicalIndexConfig` | Injectable configuration (index directory path) |
| `LuceneLexicalIndex` | Lucene 8.11.x implementation of `LexicalIndex` |

## Usage

```java
// Configure
LexicalIndexConfig config = new LexicalIndexConfig(Paths.get("/var/data/index"));

// Open index
try (LexicalIndex index = new LuceneLexicalIndex(config)) {

    // Index a document
    VirtualResourceRef ref = new VirtualResourceRef(
        BookmarkUri.parse("file:///docs/guide.txt"),
        VirtualResourceKind.FILE);

    LexicalDocument doc = LexicalDocument.builder(ref)
        .title("User Guide")
        .contentType("text/plain")
        .fullText("Full document text content here...")
        .build();

    index.index(doc);
    index.commit();

    // Search
    List<LexicalSearchResult> results = index.search(new LexicalQuery("content"));
}
```

## Design principles

- **Uses astu resource identity** — no parallel resource ID model. All documents are keyed by the full `VirtualResourceRef` (URI + kind). Two refs with the same URI but different kinds are treated as distinct index entries.
- **Explicit commit model** — callers must call `commit()` after write operations. Changes are only visible to `search()` after commit. This keeps `search()` as a pure read operation.
- **No semantic indexing** — this module handles only classical lexical/keyword search. Embeddings, reranking, and vector search belong in `pinakes`.
- **Injectable configuration** — index location is supplied via `LexicalIndexConfig`, not global settings.
- **No singleton lifecycle** — callers manage the index lifecycle explicitly via `Closeable`.

## Package

All classes live in:

```
com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai
```

This follows the nested module boundary convention for Corenth.

## Lucene version

Uses Apache Lucene 8.11.4, which is the last release line compatible with Java 8. BM25 is the default similarity in Lucene 8+.

## Dependencies

- `astu` — for `VirtualResourceRef`, `BookmarkUri`, `VirtualResourceKind`
- `org.apache.lucene:lucene-core:8.11.4`
- `org.apache.lucene:lucene-analyzers-common:8.11.4`
- `org.apache.lucene:lucene-queryparser:8.11.4`
