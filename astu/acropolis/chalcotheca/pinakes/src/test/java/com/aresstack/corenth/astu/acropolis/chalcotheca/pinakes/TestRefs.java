package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

final class TestRefs {

    private TestRefs() {
    }

    static VirtualResourceRef file(String path) {
        return new VirtualResourceRef(BookmarkUri.parse("file:///" + path), VirtualResourceKind.FILE);
    }

    static ChunkKey chunk(String path, int index) {
        return new ChunkKey(file(path), index);
    }

    static EmbeddingVector vector(float... values) {
        return new EmbeddingVector(values);
    }
}
