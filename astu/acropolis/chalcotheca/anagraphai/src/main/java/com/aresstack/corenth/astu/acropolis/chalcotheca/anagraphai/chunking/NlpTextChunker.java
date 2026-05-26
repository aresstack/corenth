package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLP-aware text chunker that splits text into token-budgeted lexical chunks
 * using sentence segmentation.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Preserves Markdown heading context when possible</li>
 *   <li>Splits at sentence boundaries when possible</li>
 *   <li>Enforces max token count per chunk using the shared Lucene analyzer</li>
 *   <li>Supports sentence overlap between chunks</li>
 *   <li>Keeps stable chunk order and chunk indexes</li>
 *   <li>Tolerates empty/blank input (returns empty list)</li>
 *   <li>Tolerates very long single sentences by emitting an oversized single-sentence chunk</li>
 * </ul>
 *
 * <p>Adapted from MainframeMate PR #51 NLP-enhanced RAG chunking concepts.
 */
public final class NlpTextChunker implements LexicalChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    private final SentenceSegmenter segmenter;
    private final TokenCounter tokenCounter;
    private final LexicalChunkingConfig config;

    public NlpTextChunker(SentenceSegmenter segmenter, TokenCounter tokenCounter, LexicalChunkingConfig config) {
        if (segmenter == null) throw new IllegalArgumentException("segmenter must not be null");
        if (tokenCounter == null) throw new IllegalArgumentException("tokenCounter must not be null");
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.segmenter = segmenter;
        this.tokenCounter = tokenCounter;
        this.config = config;
    }

    /** Creates a chunker with default config. */
    public NlpTextChunker(SentenceSegmenter segmenter, TokenCounter tokenCounter) {
        this(segmenter, tokenCounter, new LexicalChunkingConfig());
    }

    @Override
    public List<LexicalChunk> chunk(String text) {
        List<LexicalChunk> result = new ArrayList<LexicalChunk>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        List<Section> sections = splitByHeadings(text);

        int chunkIndex = 0;
        for (Section section : sections) {
            List<LexicalChunk> sectionChunks = chunkSection(section, chunkIndex);
            result.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }

        return result;
    }

    private List<LexicalChunk> chunkSection(Section section, int startIndex) {
        List<LexicalChunk> chunks = new ArrayList<LexicalChunk>();
        String sectionText = section.body;
        if (sectionText.trim().isEmpty()) {
            return chunks;
        }

        List<TextRange> sentences = segmenter.segment(sectionText);
        if (sentences.isEmpty()) {
            String chunkText = buildChunkText(section.heading, sectionText.trim());
            if (!chunkText.isEmpty()) {
                chunks.add(new LexicalChunk(startIndex, chunkText));
            }
            return chunks;
        }

        int sentIdx = 0;
        while (sentIdx < sentences.size()) {
            StringBuilder chunkBuilder = new StringBuilder();
            if (section.heading != null) {
                chunkBuilder.append(section.heading).append("\n\n");
            }

            int firstSentInChunk = sentIdx;
            int lastSentInChunk = sentIdx;

            while (sentIdx < sentences.size()) {
                TextRange sentRange = sentences.get(sentIdx);
                String sentText = sectionText.substring(sentRange.start(), sentRange.end());

                String candidate;
                if (chunkBuilder.length() == 0
                        || (section.heading != null && chunkBuilder.toString().trim().equals(section.heading))) {
                    candidate = chunkBuilder.toString() + sentText;
                } else {
                    candidate = chunkBuilder.toString() + sentText;
                }

                int tokenCount = tokenCounter.countTokens(candidate);

                if (tokenCount > config.chunkSizeTokens() && sentIdx > firstSentInChunk) {
                    break;
                }

                chunkBuilder.append(sentText);
                lastSentInChunk = sentIdx;
                sentIdx++;

                if (tokenCount > config.chunkSizeTokens()) {
                    break;
                }
            }

            String chunkText = chunkBuilder.toString().trim();
            if (!chunkText.isEmpty()) {
                chunks.add(new LexicalChunk(startIndex + chunks.size(), chunkText));
            }

            if (sentIdx < sentences.size() && config.overlapSentences() > 0) {
                int overlapStart = lastSentInChunk - config.overlapSentences() + 1;
                if (overlapStart > firstSentInChunk) {
                    sentIdx = overlapStart + config.overlapSentences();
                    sentIdx = lastSentInChunk + 1 - config.overlapSentences();
                    if (sentIdx <= firstSentInChunk) {
                        sentIdx = lastSentInChunk + 1;
                    }
                }
            }
        }

        return chunks;
    }

    private String buildChunkText(String heading, String body) {
        if (heading != null && !heading.isEmpty()) {
            return heading + "\n\n" + body;
        }
        return body;
    }

    private List<Section> splitByHeadings(String text) {
        List<Section> sections = new ArrayList<Section>();
        Matcher matcher = HEADING_PATTERN.matcher(text);

        List<int[]> headingPositions = new ArrayList<int[]>();
        List<String> headings = new ArrayList<String>();

        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
            headings.add(matcher.group(0));
        }

        if (headingPositions.isEmpty()) {
            sections.add(new Section(null, text));
            return sections;
        }

        if (headingPositions.get(0)[0] > 0) {
            String prolog = text.substring(0, headingPositions.get(0)[0]);
            if (!prolog.trim().isEmpty()) {
                sections.add(new Section(null, prolog));
            }
        }

        for (int i = 0; i < headingPositions.size(); i++) {
            String heading = headings.get(i);
            int bodyStart = headingPositions.get(i)[1];
            int bodyEnd = (i + 1 < headingPositions.size())
                    ? headingPositions.get(i + 1)[0]
                    : text.length();
            String body = text.substring(bodyStart, bodyEnd);
            sections.add(new Section(heading, body));
        }

        return sections;
    }

    private static final class Section {
        final String heading;
        final String body;

        Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }
}
