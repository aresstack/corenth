package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalDocument;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalQuery;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Return a fixed lexical ranking and record the queries it receives. */
final class ScriptedLexicalIndex implements LexicalIndex {

    private final List<LexicalSearchResult> ranking = new ArrayList<LexicalSearchResult>();
    private final List<LexicalQuery> queries = new ArrayList<LexicalQuery>();
    private IOException failure;

    ScriptedLexicalIndex add(ChunkKey key, float score, String text) {
        ranking.add(new LexicalSearchResult(key.resourceRef(), score, key.chunkIndex(), text,
                "Title " + key.resourceRef().uri(), "text/plain"));
        return this;
    }

    void failWith(IOException e) {
        this.failure = e;
    }

    List<LexicalQuery> queries() {
        return queries;
    }

    @Override
    public List<LexicalSearchResult> search(LexicalQuery query) throws IOException {
        queries.add(query);
        if (failure != null) {
            throw failure;
        }
        return ranking.size() > query.maxResults()
                ? new ArrayList<LexicalSearchResult>(ranking.subList(0, query.maxResults()))
                : new ArrayList<LexicalSearchResult>(ranking);
    }

    @Override
    public void index(LexicalDocument document) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(VirtualResourceRef resourceRef) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commit() {
    }

    @Override
    public void close() {
    }
}
