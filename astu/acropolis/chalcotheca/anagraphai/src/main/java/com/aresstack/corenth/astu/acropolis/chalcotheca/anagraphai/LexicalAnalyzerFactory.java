package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * Shared analyzer factory for lexical indexing and token counting.
 *
 * <p>Both {@link LuceneLexicalIndex} and
 * {@link com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LuceneTokenCounter}
 * obtain their analyzer from this factory. Any future change to the analyzer
 * configuration (e.g. custom stop words, language-specific tokenization) only
 * needs to happen here — both indexing and token counting follow by construction.
 */
public final class LexicalAnalyzerFactory {

    private LexicalAnalyzerFactory() {
        // utility class
    }

    /**
     * Creates the canonical analyzer used for lexical indexing and token counting.
     *
     * <p>Currently returns a {@link StandardAnalyzer}. Callers should not
     * assume a specific concrete type — use the returned {@link Analyzer} interface.
     *
     * @return a new Analyzer instance for indexing or token counting
     */
    public static Analyzer create() {
        return new StandardAnalyzer();
    }
}
