package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalAnalyzerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LuceneTokenCounterTest {

    private final LuceneTokenCounter counter = new LuceneTokenCounter();

    @AfterEach
    void closeDefaultCounter() {
        counter.close();
    }

    @Test
    void countsTokensConsistentWithStandardAnalyzer() {
        int count = counter.countTokens("Hello world, this is a test.");
        assertTrue(count > 0);
        assertTrue(count >= 3 && count <= 7,
                "Expected between 3 and 7 tokens, got " + count);
    }

    @Test
    void emptyTextReturnsZero() {
        assertEquals(0, counter.countTokens(""));
    }

    @Test
    void nullTextReturnsZero() {
        assertEquals(0, counter.countTokens(null));
    }

    @Test
    void usesSharedAnalyzerFactory() {
        // The default constructor must produce an analyzer of the same type
        // as LexicalAnalyzerFactory.create() — verifying the factory contract.
        Analyzer fromFactory = LexicalAnalyzerFactory.create();
        Analyzer fromCounter = counter.analyzer();
        assertNotNull(fromCounter);
        assertEquals(fromFactory.getClass(), fromCounter.getClass(),
                "LuceneTokenCounter must use the same analyzer type as LexicalAnalyzerFactory");
        fromFactory.close();
    }

    @Test
    void factoryProducesConsistentTokenCounts() {
        // Token counting via the default counter must agree with a counter
        // constructed from the factory — proving the contract holds end-to-end.
        Analyzer factoryAnalyzer = LexicalAnalyzerFactory.create();
        LuceneTokenCounter factoryCounter = new LuceneTokenCounter(factoryAnalyzer);
        String sample = "The Corenth architecture uses modular resource indexing.";
        assertEquals(factoryCounter.countTokens(sample), counter.countTokens(sample),
                "Default counter and factory-based counter must agree on token count");
        factoryCounter.close();
        factoryAnalyzer.close();
    }

    @Test
    void rejectsNullAnalyzer() {
        assertThrows(IllegalArgumentException.class, () -> new LuceneTokenCounter(null));
    }

    @Test
    void longerTextHasMoreTokens() {
        int short_ = counter.countTokens("Short text.");
        int long_ = counter.countTokens("This is a much longer text with many more words that should produce more tokens overall.");
        assertTrue(long_ > short_);
    }

    @Test
    void closeMakesDefaultCounterAnalyzerUnavailable() {
        LuceneTokenCounter ownedCounter = new LuceneTokenCounter();
        ownedCounter.close();
        assertThrows(RuntimeException.class, () -> ownedCounter.countTokens("text after close"));
    }

    @Test
    void closeDoesNotCloseInjectedAnalyzer() {
        Analyzer analyzer = LexicalAnalyzerFactory.create();
        LuceneTokenCounter injectedCounter = new LuceneTokenCounter(analyzer);
        injectedCounter.close();
        LuceneTokenCounter stillUsable = new LuceneTokenCounter(analyzer);
        assertTrue(stillUsable.countTokens("still open") > 0);
        stillUsable.close();
        analyzer.close();
    }
}
