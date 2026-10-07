package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;

/**
 * Identify one chunk of an indexed resource across the lexical and the semantic register.
 *
 * <p>The key is exactly the identity Anagraphai already uses: the {@link VirtualResourceRef}
 * of the resource and the zero-based index of a {@link LexicalChunk} within it. Pinakes does
 * not chunk text and owns no chunk model of its own; this value only lets lexical and
 * semantic hits of the same chunk be recognised as one candidate.
 *
 * <p>Keys order deterministically by URI, kind and chunk index. Use that order to break
 * score ties so that equal scores never depend on hash or insertion order.
 */
public final class ChunkKey implements Comparable<ChunkKey> {

    private final VirtualResourceRef resourceRef;
    private final int chunkIndex;

    public ChunkKey(VirtualResourceRef resourceRef, int chunkIndex) {
        if (resourceRef == null) {
            throw new IllegalArgumentException("resourceRef must not be null");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        this.resourceRef = resourceRef;
        this.chunkIndex = chunkIndex;
    }

    /** Derive the key of the chunk a lexical hit points to. */
    public static ChunkKey of(LexicalSearchResult lexicalResult) {
        if (lexicalResult == null) {
            throw new IllegalArgumentException("lexicalResult must not be null");
        }
        return new ChunkKey(lexicalResult.resourceRef(), lexicalResult.chunkIndex());
    }

    /** Derive the key of a lexical chunk of the given resource. */
    public static ChunkKey of(VirtualResourceRef resourceRef, LexicalChunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        return new ChunkKey(resourceRef, chunk.index());
    }

    /** Return the resource the chunk belongs to. */
    public VirtualResourceRef resourceRef() {
        return resourceRef;
    }

    /** Return the zero-based chunk index within the resource. */
    public int chunkIndex() {
        return chunkIndex;
    }

    @Override
    public int compareTo(ChunkKey other) {
        int byUri = resourceRef.uri().toString().compareTo(other.resourceRef.uri().toString());
        if (byUri != 0) {
            return byUri;
        }
        int byKind = resourceRef.kind().name().compareTo(other.resourceRef.kind().name());
        if (byKind != 0) {
            return byKind;
        }
        return chunkIndex < other.chunkIndex ? -1 : (chunkIndex == other.chunkIndex ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChunkKey)) {
            return false;
        }
        ChunkKey that = (ChunkKey) o;
        return chunkIndex == that.chunkIndex && resourceRef.equals(that.resourceRef);
    }

    @Override
    public int hashCode() {
        return 31 * resourceRef.hashCode() + chunkIndex;
    }

    @Override
    public String toString() {
        return "ChunkKey{" + resourceRef + ", chunk=" + chunkIndex + "}";
    }
}
