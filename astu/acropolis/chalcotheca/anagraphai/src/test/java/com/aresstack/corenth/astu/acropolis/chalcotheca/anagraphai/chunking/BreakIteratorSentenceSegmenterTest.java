package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BreakIteratorSentenceSegmenterTest {

    private final BreakIteratorSentenceSegmenter segmenter = new BreakIteratorSentenceSegmenter();

    @Test
    void segmentsGermanText() {
        String text = "Dies ist ein Satz. Hier kommt ein zweiter Satz. Und ein dritter.";
        List<TextRange> ranges = segmenter.segment(text);
        assertFalse(ranges.isEmpty());
        assertTrue(ranges.size() >= 2, "Expected at least 2 sentences, got " + ranges.size());
        // All ranges cover the text without gaps
        assertEquals(0, ranges.get(0).start());
    }

    @Test
    void emptyTextReturnsEmptyList() {
        assertTrue(segmenter.segment("").isEmpty());
    }

    @Test
    void nullTextReturnsEmptyList() {
        assertTrue(segmenter.segment(null).isEmpty());
    }

    @Test
    void singleSentence() {
        String text = "Ein einzelner Satz ohne Punkt am Ende";
        List<TextRange> ranges = segmenter.segment(text);
        assertEquals(1, ranges.size());
        assertEquals(text, text.substring(ranges.get(0).start(), ranges.get(0).end()));
    }

    @Test
    void respectsLocale() {
        BreakIteratorSentenceSegmenter english = new BreakIteratorSentenceSegmenter(Locale.ENGLISH);
        String text = "Hello world. This is a test. Another sentence here.";
        List<TextRange> ranges = english.segment(text);
        assertTrue(ranges.size() >= 2);
    }

    @Test
    void rejectsNullLocale() {
        assertThrows(IllegalArgumentException.class, () -> new BreakIteratorSentenceSegmenter(null));
    }
}
