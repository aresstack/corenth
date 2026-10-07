# Pinakes

**The semantic register for Corenth.**

Pinakes owns semantic indexing, embedding and reranking contracts and the hybrid retrieval flow that combines them with the lexical register (`anagraphai`). It does not generate text, assemble prompts or run conversations; using retrieval results for answers belongs to a later LLM/context module.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

Retrieval is staged:

1. **Lexical candidates** (mandatory) — `anagraphai.LexicalIndex`, BM25.
2. **Semantic widening** (optional) — `EmbeddingClient` + `SemanticIndex`, fused with the lexical ranking by reciprocal rank fusion.
3. **Reranking** (optional) — `Reranker`, scores raw candidate text.

Semantic widening and reranking are independent. Reranking works on purely lexical candidates; semantic widening works without a reranker; with both off, retrieval is plain lexical search and needs no embedding runtime at all.

## Status: ports and reference implementation only

| Part | Status |
|---|---|
| Ports and values | implemented, Java 8, no third-party types |
| `InMemorySemanticIndex` | implemented; deterministic exact cosine reference, not persistent, not meant to scale |
| `HybridRetrieval`, `ReciprocalRankFusion` | implemented, I/O-free except for the injected ports |
| Real `EmbeddingClient` adapter | **missing** |
| Real `Reranker` adapter | **missing** |
| Persistent / scalable `SemanticIndex` adapter | **missing** |
| Lifecycle wiring (index on accept, withdraw on removal) | **missing**; belongs to the Acropolis composition (#10) |

Tests run against `TokenHashEmbeddingClient` and `TokenOverlapReranker` from the test fixtures. They prove contract coverage, never real embedding or reranking quality.

### Runtime adapters follow #44

- MainframeMate's `onnx` and `winml-java` trees are **chat backends, not embedding runtimes** (#44, `DO_NOT_MIGRATE`). They provide no adapter for Pinakes.
- The candidate for a first local embedding/rerank adapter is the AresStack DirectML family as used by askai-java8 (`local-model-runtime-sidecar-java21`: `embed`, `rerank`, E5 `query:`/`passage:` input types). That runtime needs Java 21, so it would be integrated as an optional sidecar adapter behind `EmbeddingClient`/`Reranker`. No sidecar transport type may appear in this module.
- ONNX, WinML, DirectML, HTTP clients and model downloads are never mandatory for this module.

## Public API

| Type | Purpose |
|---|---|
| `ChunkKey` | Chunk identity shared with Anagraphai: `VirtualResourceRef` + `LexicalChunk` index; deterministic order for tie-breaking |
| `EmbeddingVector` | Immutable, finite, non-empty vector; zero vectors are detectable, cosine on them is undefined |
| `EmbeddingModel` | Vector space identity (opaque id + dimension); vectors of different models are never compared |
| `EmbeddingPurpose` | `QUERY` or `PASSAGE`, for asymmetric models such as E5 |
| `EmbeddingClient` | Port: `model()`, batch `embed(purpose, texts)` |
| `SemanticEntry`, `SemanticSearchResult` | Index input and output |
| `SemanticIndex` | Port: `upsert`, atomic `replaceResource`, `removeResource`, `search`, `size` |
| `InMemorySemanticIndex` | Reference implementation |
| `Reranker`, `RerankRequest`, `RerankCandidate`, `RerankedResult` | Reranking port and values; keyed by `ChunkKey`, not by position |
| `ReciprocalRankFusion` | Pure rank fusion (`k = 60` by default) |
| `HybridRetrievalPlan` | Final limit, pool sizes, independent stage switches |
| `HybridRetrieval` | Runs the stages; immutable, holds no index state |
| `RetrievalResult`, `RetrievalHit`, `ScoreSource`, `StageOutcome` | Result with per-stage, machine-readable outcome |

## Contract rules

- **One chunk truth.** Pinakes never chunks text. Callers index the same `LexicalChunk`s in both registers under the same `ChunkKey`.
- **One model per index.** Writes and queries are checked against the index model's dimension; `HybridRetrieval` refuses a client of another model (`MODEL_MISMATCH`) before calling it.
- **Zero and non-finite vectors.** `NaN`/infinite components are rejected when the vector is built. Zero vectors are rejected on index writes; a zero query returns no semantic hits.
- **Determinism.** Equal scores are ordered by `ChunkKey` (URI, kind, chunk index); fused ties first by best single rank. Results never depend on insertion or hash order.
- **Replacement.** `replaceResource` drops chunks of the resource that are not in the new list, so a re-chunked resource leaves no stale vectors. It validates everything before changing anything.
- **Degradation.** Optional stages never fail the search: a failing or contract-breaking adapter keeps the previous ranking and reports `FAILED` with a description. Failures of the lexical register propagate.
- **Reranker answers.** Results may come in any order. Candidates left out are dropped; scores for unknown or duplicate candidates invalidate the whole answer.
- **No decisions about indexing.** Whether a resource is indexed, reindexed or withdrawn is decided by Tamias and executed by Acropolis; Pinakes only stores and searches.

## Usage

```java
LexicalIndex lexical = ...;                       // anagraphai
SemanticIndex semantic = new InMemorySemanticIndex(client.model());

HybridRetrieval retrieval = HybridRetrieval.lexical(lexical)
        .withSemantic(semantic, client)           // optional
        .withReranker(reranker);                  // optional, independent

RetrievalResult result = retrieval.retrieve("job entry subsystem",
        HybridRetrievalPlan.builder(5).semantic(true).rerank(true).build());

result.semanticOutcome();                         // APPLIED, DISABLED, FAILED, ...
for (RetrievalHit hit : result.hits()) {
    hit.key(); hit.text(); hit.score(); hit.scoreSource();
}
```

## Dependencies

- `astu` — `VirtualResourceRef`
- `astu:acropolis:chalcotheca:anagraphai` — `LexicalIndex`, `LexicalChunk`, `LexicalSearchResult`. The direction is one-way: Anagraphai never depends on Pinakes (`ANAGRAPHAI_MUST_STAY_LEXICAL_ONLY`). Lucene stays an `implementation` detail of Anagraphai.

## Migration inventory

| MainframeMate (`de.bund.zrb.rag`) | Corenth |
|---|---|
| `port/EmbeddingClient` (`float[] embed(String)`) | `EmbeddingClient` (batch, purpose, model identity, checked failure) |
| `port/SemanticIndex` (String chunk ids) | `SemanticIndex` keyed by `ChunkKey`, bound to an `EmbeddingModel` |
| `port/RerankerClient` (positional `float[]`) | `Reranker` with `ChunkKey`-keyed `RerankedResult` |
| `infrastructure/InMemorySemanticIndex` | `InMemorySemanticIndex` (deterministic ties, atomic resource replacement) |
| `usecase/HybridRetriever` weighted score merge | `ReciprocalRankFusion` (rank-only, no score normalisation) |
| `HybridRetriever` independent toggles, reranker pool, best-effort rerank | `HybridRetrievalPlan` + `StageOutcome` |
| `RagService` singleton | not migrated; composition decides wiring |
| `MultiProviderEmbeddingClient`, `HttpRerankerClient` | not migrated; future adapters outside Pinakes |
| `RagConfig.FallbackMode.SEMANTIC_ONLY` | not migrated; the lexical register stays mandatory |
| document-id filter in `retrieve` | not migrated; candidate filtering by scope is a later, explicit decision |
