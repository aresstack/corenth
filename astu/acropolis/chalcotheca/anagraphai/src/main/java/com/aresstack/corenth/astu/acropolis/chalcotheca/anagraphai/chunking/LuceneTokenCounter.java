package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalAnalyzerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

import java.io.IOException;

/**
 * Token counter using the shared {@link LexicalAnalyzerFactory} analyzer,
 * matching the tokenization assumptions of {@code LuceneLexicalIndex}.
 *
 * <p>Both this counter and the index obtain their analyzer from
 * {@link LexicalAnalyzerFactory#create()}, ensuring that any future analyzer
 * change applies to both by construction.
 */
public final class LuceneTokenCounter implements TokenCounter {

    private final Analyzer analyzer;

    /** Creates a counter using the shared {@link LexicalAnalyzerFactory} analyzer. */
    public LuceneTokenCounter() {
        this(LexicalAnalyzerFactory.create());
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
