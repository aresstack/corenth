package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sentence segmenter using Java's built-in {@link BreakIterator}.
 * This is the zero-model fallback that requires no external resources.
 */
public final class BreakIteratorSentenceSegmenter implements SentenceSegmenter {

    private final Locale locale;

    /** Creates a segmenter with German locale (default). */
    public BreakIteratorSentenceSegmenter() {
        this(Locale.GERMAN);
    }

    /** Creates a segmenter with the specified locale. */
    public BreakIteratorSentenceSegmenter(Locale locale) {
        if (locale == null) throw new IllegalArgumentException("locale must not be null");
        this.locale = locale;
    }

    @Override
    public List<TextRange> segment(String text) {
        List<TextRange> ranges = new ArrayList<TextRange>();
        if (text == null || text.isEmpty()) {
            return ranges;
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(locale);
        iterator.setText(text);
        int start = iterator.first();
        int end = iterator.next();
        while (end != BreakIterator.DONE) {
            String segment = text.substring(start, end);
            if (!segment.trim().isEmpty()) {
                ranges.add(new TextRange(start, end));
            }
            start = end;
            end = iterator.next();
        }
        return ranges;
    }
}
