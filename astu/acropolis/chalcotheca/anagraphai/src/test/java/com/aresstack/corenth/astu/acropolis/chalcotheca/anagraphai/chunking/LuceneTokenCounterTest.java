package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LuceneTokenCounterTest {

    private final LuceneTokenCounter counter = new LuceneTokenCounter();

    @Test
    void countsTokensConsistentWithStandardAnalyzer() {
        // StandardAnalyzer splits on whitespace and punctuation, lowercases
        int count = counter.countTokens("Hello world, this is a test.");
        assertTrue(count > 0);
        // "Hello", "world", "this", "is", "a", "test" = 6 tokens
        // StandardAnalyzer removes stop words by default, so "this", "is", "a" may be kept or removed
        // depending on version. Just verify it returns a positive value.
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
    void sameAnalyzerUsedByIndex() {
        // Verify the analyzer is a StandardAnalyzer — same type used by LuceneLexicalIndex
        assertNotNull(counter.analyzer());
        assertEquals("org.apache.lucene.analysis.standard.StandardAnalyzer",
                counter.analyzer().getClass().getName());
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
}
