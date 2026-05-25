# MainframeMate → Anagraphai Migration Inventory

This document records the inspection of MainframeMate research sources and the migration decisions for `anagraphai` lexical indexing contracts.

## Source files inspected

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `app/.../rag/infrastructure/LuceneLexicalIndex.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex` | Core Lucene index logic (open, write, search, close) is adapted. Singleton lifecycle removed. Path-based document ID replaced by composite `VirtualResourceRef` key (URI + kind). BM25/full-text retrieval behavior preserved. |
| `app/.../rag/port/LexicalIndex.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LexicalIndex` | Port interface concept preserved. Method signatures adapted to use Corenth models (`LexicalDocument`, `LexicalQuery`, `LexicalSearchResult`). `Closeable` added for explicit lifecycle. Explicit commit model documented. |
| `app/.../rag/model/Chunk.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LexicalChunk` | Text chunk concept kept. Embedding vector field removed (belongs in `pinakes`). Chunk index added for positional tracking within a document. |
| `app/.../rag/model/ScoredChunk.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult` | Score + text excerpt concept kept. Resource identity changed from path string to `VirtualResourceRef`. Title, content type, and chunk index added for richer result context. |
| `app/.../search/SearchService.java` | do-not-copy | — | UI search tab orchestration tightly coupled to Swing and application tabs. Search behavior is expressed through `LexicalIndex.search()` instead. |
| `app/.../search/SearchResult.java` | adapter-candidate | — | Basic result model. Concepts absorbed into `LexicalSearchResult` which carries richer metadata. May inform future `deigma` search facade. |
| `app/.../search/SearchQuery.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LexicalQuery` | Query text + limit concept kept. UI-specific fields (tab filter, case sensitivity toggle) not copied. |
| `app/.../service/LuceneDependencyIndex.java` | adapt | `c.a.c.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex` | Lucene directory management and writer lifecycle patterns informed the implementation. Dependency-specific fields not copied. |
| `app/.../indexing/service/IndexingPipeline.java` | do-not-copy | — | Orchestrates full ingestion (scan → chunk → index → embed). This is pipeline/orchestration logic belonging in a higher module (`deigma` or `chalcotheca` coordination). Only the "index a document" concept is used. |
| `app/.../indexing/service/RagContentProcessor.java` | do-not-copy | — | Handles PDF/DOCX parsing, text splitting, and embedding generation. Content extraction belongs in `deigma`; embedding in `pinakes`. Only the "accept text chunks" concept influenced `LexicalDocument.Builder`. |

## New Corenth API (not derived from MainframeMate)

| Class | Purpose |
|---|---|
| `LexicalIndexConfig` | Injectable configuration object for index storage location. Replaces MainframeMate's hard-coded application folder paths and global settings. |
| `LexicalDocument` | Builder-pattern document model tying chunks to a `VirtualResourceRef`. No equivalent single class in MainframeMate (was spread across pipeline steps). |

## Key design decisions

### VirtualResourceRef as document identity

MainframeMate used file paths (`String documentId`) as the primary key in its Lucene index. Corenth uses a composite `VirtualResourceRef` key (`uri + "\n" + kind`) as the Lucene key for update/remove identity, while storing URI and kind as metadata fields for reconstruction. This supports all resource schemes (file, ndv, mail, sharepoint, etc.) without path assumptions and preserves full `VirtualResourceRef` identity.

### No singleton lifecycle

MainframeMate's `RagService` managed the Lucene `IndexWriter` as a singleton tied to application startup/shutdown. Anagraphai uses explicit `Closeable` lifecycle — callers (typically a lifecycle manager in `chalcotheca`) control when the index opens and closes.

### No embedding or semantic fields

MainframeMate stored embedding vectors alongside lexical content. Anagraphai stores only lexical fields (text, title, metadata). Vector/semantic indexing belongs in `pinakes`.

### Configurable storage location

`LexicalIndexConfig` replaces MainframeMate's `Settings.getApplicationFolder()` path derivation. The index directory is supplied at construction time, allowing tests and deployments to use arbitrary locations.

### BM25 similarity retained

Lucene 8.x defaults to BM25 similarity, which MainframeMate also used. No custom similarity configuration is needed at this stage.
