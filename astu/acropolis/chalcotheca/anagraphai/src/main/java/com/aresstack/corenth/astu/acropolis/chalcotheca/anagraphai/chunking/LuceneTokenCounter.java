package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.IOException;

/**
 * Token counter using Lucene's {@link StandardAnalyzer}, matching the tokenization
 * assumptions of {@code LuceneLexicalIndex}.
 *
 * <p>This ensures the chunker and the index agree on what constitutes a "token",
 * preventing silent drift between chunk size estimation and actual indexed terms.
 */
public final class LuceneTokenCounter implements TokenCounter {

    private final Analyzer analyzer;

    /** Creates a counter using a new StandardAnalyzer (same as LuceneLexicalIndex). */
    public LuceneTokenCounter() {
        this(new StandardAnalyzer());
    }

    /** Creates a counter using the provided analyzer for shared usage. */
    public LuceneTokenCounter(Analyzer analyzer) {
        if (analyzer == null) throw new IllegalArgumentException("analyzer must not be null");
        this.analyzer = analyzer;
    }

    /** Returns the analyzer used by this counter (for shared use with index). */
    public Analyzer analyzer() {
        return analyzer;
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        TokenStream stream = null;
        try {
            stream = analyzer.tokenStream("content", text);
            stream.reset();
            int count = 0;
            while (stream.incrementToken()) {
                count++;
            }
            stream.end();
            return count;
        } catch (IOException e) {
            throw new RuntimeException("Token counting failed unexpectedly", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }
}
