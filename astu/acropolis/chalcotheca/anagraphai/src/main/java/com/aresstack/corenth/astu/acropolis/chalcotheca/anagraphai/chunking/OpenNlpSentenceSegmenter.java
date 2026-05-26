package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sentence segmenter using Apache OpenNLP.
 *
 * <p>If the OpenNLP model cannot be loaded (missing file, classpath issue,
 * or OpenNLP not available at runtime), this segmenter gracefully falls back
 * to the {@link BreakIteratorSentenceSegmenter}.
 *
 * <p>Model absence is a normal operational condition, not a startup failure.
 *
 * <p>To use with a German sentence model, place {@code de-sent.bin} on the
 * classpath (e.g. in {@code src/main/resources/opennlp/de-sent.bin}) or
 * provide an InputStream at construction time.
 */
public final class OpenNlpSentenceSegmenter implements SentenceSegmenter {

    private static final Logger LOG = Logger.getLogger(OpenNlpSentenceSegmenter.class.getName());

    private final Object sentenceDetector;
    private final SentenceSegmenter fallback;

    /**
     * Creates the segmenter attempting to load the model from the given input stream.
     * Falls back to BreakIterator if model cannot be loaded.
     *
     * @param modelStream input stream for the OpenNLP sentence model, may be null
     */
    public OpenNlpSentenceSegmenter(InputStream modelStream) {
        this(modelStream, new BreakIteratorSentenceSegmenter());
    }

    /**
     * Creates the segmenter attempting to load the model from the given input stream.
     * Falls back to the specified fallback segmenter if model cannot be loaded.
     *
     * @param modelStream input stream for the OpenNLP sentence model, may be null
     * @param fallback segmenter to use when OpenNLP is unavailable
     */
    public OpenNlpSentenceSegmenter(InputStream modelStream, SentenceSegmenter fallback) {
        if (fallback == null) throw new IllegalArgumentException("fallback must not be null");
        this.fallback = fallback;
        this.sentenceDetector = loadModel(modelStream);
    }

    /**
     * Returns true if the OpenNLP model was loaded successfully.
     */
    public boolean isModelLoaded() {
        return sentenceDetector != null;
    }

    @Override
    public List<TextRange> segment(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<TextRange>();
        }
        if (sentenceDetector == null) {
            return fallback.segment(text);
        }
        try {
            return segmentWithOpenNlp(text);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "OpenNLP segmentation failed, using fallback", e);
            return fallback.segment(text);
        }
    }

    private List<TextRange> segmentWithOpenNlp(String text) throws Exception {
        java.lang.reflect.Method spanMethod = sentenceDetector.getClass()
                .getMethod("sentPosDetect", String.class);
        Object[] spans = (Object[]) spanMethod.invoke(sentenceDetector, text);

        List<TextRange> ranges = new ArrayList<TextRange>();
        for (Object span : spans) {
            java.lang.reflect.Method getStart = span.getClass().getMethod("getStart");
            java.lang.reflect.Method getEnd = span.getClass().getMethod("getEnd");
            int start = ((Integer) getStart.invoke(span)).intValue();
            int end = ((Integer) getEnd.invoke(span)).intValue();
            String segment = text.substring(start, end);
            if (!segment.trim().isEmpty()) {
                ranges.add(new TextRange(start, end));
            }
        }
        return ranges;
    }

    private static Object loadModel(InputStream modelStream) {
        if (modelStream == null) {
            LOG.info("No OpenNLP sentence model stream provided; using fallback segmenter.");
            return null;
        }
        try {
            Class<?> modelClass = Class.forName("opennlp.tools.sentdetect.SentenceModel");
            Object model = modelClass.getConstructor(InputStream.class).newInstance(modelStream);
            Class<?> detectorClass = Class.forName("opennlp.tools.sentdetect.SentenceDetectorME");
            return detectorClass.getConstructor(modelClass).newInstance(model);
        } catch (ClassNotFoundException e) {
            LOG.info("OpenNLP not on classpath; using fallback segmenter.");
            return null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load OpenNLP sentence model; using fallback segmenter.", e);
            return null;
        } finally {
            try {
                modelStream.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
