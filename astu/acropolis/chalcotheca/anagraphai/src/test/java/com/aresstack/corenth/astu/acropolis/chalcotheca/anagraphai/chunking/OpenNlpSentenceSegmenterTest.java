package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenNlpSentenceSegmenterTest {

    @Test
    void fallsBackWhenModelStreamIsNull() {
        OpenNlpSentenceSegmenter segmenter = new OpenNlpSentenceSegmenter(null);
        assertFalse(segmenter.isModelLoaded());
        // Should still segment using BreakIterator fallback
        String text = "First sentence. Second sentence.";
        List<TextRange> ranges = segmenter.segment(text);
        assertFalse(ranges.isEmpty());
    }

    @Test
    void fallsBackWhenModelStreamIsInvalid() {
        InputStream invalid = new ByteArrayInputStream("not a valid model".getBytes());
        OpenNlpSentenceSegmenter segmenter = new OpenNlpSentenceSegmenter(invalid);
        assertFalse(segmenter.isModelLoaded());
        // Should still segment using BreakIterator fallback
        String text = "First sentence. Second sentence.";
        List<TextRange> ranges = segmenter.segment(text);
        assertFalse(ranges.isEmpty());
    }

    @Test
    void emptyTextReturnsEmptyList() {
        OpenNlpSentenceSegmenter segmenter = new OpenNlpSentenceSegmenter(null);
        assertTrue(segmenter.segment("").isEmpty());
    }

    @Test
    void nullTextReturnsEmptyList() {
        OpenNlpSentenceSegmenter segmenter = new OpenNlpSentenceSegmenter(null);
        assertTrue(segmenter.segment(null).isEmpty());
    }

    @Test
    void customFallbackIsUsed() {
        final boolean[] called = {false};
        SentenceSegmenter customFallback = new SentenceSegmenter() {
            @Override
            public List<TextRange> segment(String text) {
                called[0] = true;
                return new BreakIteratorSentenceSegmenter().segment(text);
            }
        };
        OpenNlpSentenceSegmenter segmenter = new OpenNlpSentenceSegmenter(null, customFallback);
        segmenter.segment("Test sentence.");
        assertTrue(called[0], "Custom fallback should have been called");
    }

    @Test
    void rejectsNullFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenNlpSentenceSegmenter(null, null));
    }
}
