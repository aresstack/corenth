package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkKeyTest {

    @Test
    void rejectsMissingRefAndNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkKey(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkKey(TestRefs.file("a"), -1));
    }

    @Test
    void reusesTheLexicalChunkIdentity() {
        VirtualResourceRef ref = TestRefs.file("doc.txt");
        LexicalSearchResult lexical = new LexicalSearchResult(ref, 1.5f, 3, "text", "Doc", "text/plain");

        assertEquals(new ChunkKey(ref, 3), ChunkKey.of(lexical));
        assertEquals(new ChunkKey(ref, 2), ChunkKey.of(ref, new LexicalChunk(2, "x")));
    }

    @Test
    void distinguishesKindsOfTheSameUri() {
        BookmarkUri uri = BookmarkUri.parse("file:///same");
        ChunkKey file = new ChunkKey(new VirtualResourceRef(uri, VirtualResourceKind.FILE), 0);
        ChunkKey directory = new ChunkKey(new VirtualResourceRef(uri, VirtualResourceKind.DIRECTORY), 0);

        assertNotEquals(file, directory);
    }

    @Test
    void ordersByUriThenKindThenIndex() {
        ChunkKey a0 = TestRefs.chunk("a", 0);
        ChunkKey a2 = TestRefs.chunk("a", 2);
        ChunkKey a10 = TestRefs.chunk("a", 10);
        ChunkKey b0 = TestRefs.chunk("b", 0);
        List<ChunkKey> keys = new ArrayList<ChunkKey>(Arrays.asList(b0, a10, a0, a2));

        Collections.sort(keys);

        assertEquals(Arrays.asList(a0, a2, a10, b0), keys);
        assertEquals(0, a0.compareTo(TestRefs.chunk("a", 0)));
    }
}
