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
 *
 * <p>Analyzer ownership:
 * <ul>
 *   <li>Default constructor: this counter owns the analyzer and closes it in {@link #close()}.</li>
 *   <li>Injected analyzer constructor: caller retains ownership; {@link #close()} is a no-op.</li>
 * </ul>
 */
public final class LuceneTokenCounter implements TokenCounter, AutoCloseable {

    private final Analyzer analyzer;
    private final boolean ownsAnalyzer;

    /** Creates a counter using the shared {@link LexicalAnalyzerFactory} analyzer. */
    public LuceneTokenCounter() {
        this(LexicalAnalyzerFactory.create(), true);
    }

    /** Creates a counter using the provided analyzer for shared usage. Caller retains ownership. */
    public LuceneTokenCounter(Analyzer analyzer) {
        this(analyzer, false);
    }

    private LuceneTokenCounter(Analyzer analyzer, boolean ownsAnalyzer) {
        if (analyzer == null) throw new IllegalArgumentException("analyzer must not be null");
        this.analyzer = analyzer;
        this.ownsAnalyzer = ownsAnalyzer;
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

    @Override
    public void close() {
        if (ownsAnalyzer) {
            analyzer.close();
        }
    }
}
