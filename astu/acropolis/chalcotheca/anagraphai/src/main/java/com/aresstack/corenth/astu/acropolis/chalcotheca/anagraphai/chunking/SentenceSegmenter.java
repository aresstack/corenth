package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import java.util.List;

/**
 * Port interface for sentence boundary detection.
 */
public interface SentenceSegmenter {
    /**
     * Segments text into sentence ranges.
     *
     * @param text the input text
     * @return ordered list of sentence ranges (non-overlapping, covering the text)
     */
    List<TextRange> segment(String text);
}
