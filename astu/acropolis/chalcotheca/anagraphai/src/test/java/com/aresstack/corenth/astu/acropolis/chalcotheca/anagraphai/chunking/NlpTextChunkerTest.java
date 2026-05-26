package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NlpTextChunkerTest {

    private final SentenceSegmenter segmenter = new BreakIteratorSentenceSegmenter();
    private final LuceneTokenCounter tokenCounter = new LuceneTokenCounter();

    @AfterEach
    void closeTokenCounter() {
        tokenCounter.close();
    }

    @Test
    void blankInputReturnsNoChunks() {
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter);
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void doesNotSplitOrdinarySentencesMidSentence() {
        // With a generous token budget, a few sentences should stay in one chunk
        LexicalChunkingConfig config = new LexicalChunkingConfig(1000, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        String text = "This is sentence one. This is sentence two. This is sentence three.";
        List<LexicalChunk> chunks = chunker.chunk(text);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("sentence one"));
        assertTrue(chunks.get(0).text().contains("sentence three"));
    }

    @Test
    void maxTokenBudgetCreatesMultipleChunks() {
        // Very small token budget should force splitting
        LexicalChunkingConfig config = new LexicalChunkingConfig(5, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        String text = "First sentence with several words. Second sentence with several words. "
                + "Third sentence with several words. Fourth sentence with several words.";
        List<LexicalChunk> chunks = chunker.chunk(text);
        assertTrue(chunks.size() > 1, "Expected multiple chunks, got " + chunks.size());
        // Verify stable indexes
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }

    @Test
    void sentenceOverlapWorks() {
        // Small budget + overlap should cause shared sentences between chunks
        LexicalChunkingConfig config = new LexicalChunkingConfig(10, 1);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        String text = "Alpha sentence here. Beta sentence here. Gamma sentence here. Delta sentence here.";
        List<LexicalChunk> chunks = chunker.chunk(text);
        assertTrue(chunks.size() >= 2, "Expected at least 2 chunks with overlap");
    }

    @Test
    void markdownHeadingsRetainedAsContext() {
        LexicalChunkingConfig config = new LexicalChunkingConfig(1000, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        String text = "# Introduction\n\nThis is the introduction text.\n\n## Details\n\nHere are the details.";
        List<LexicalChunk> chunks = chunker.chunk(text);
        assertFalse(chunks.isEmpty());
        // At least one chunk should contain a heading
        boolean hasHeading = false;
        for (LexicalChunk c : chunks) {
            if (c.text().contains("# Introduction") || c.text().contains("## Details")) {
                hasHeading = true;
                break;
            }
        }
        assertTrue(hasHeading, "At least one chunk should retain a markdown heading");
    }

    @Test
    void headingOnlyMarkdownSectionIsEmitted() {
        LexicalChunkingConfig config = new LexicalChunkingConfig(1000, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        List<LexicalChunk> chunks = chunker.chunk("# Title");
        assertEquals(1, chunks.size());
        assertEquals("# Title", chunks.get(0).text());
    }

    @Test
    void longSingleSentenceDoesNotLoopForever() {
        // A very long sentence with a tiny token budget
        LexicalChunkingConfig config = new LexicalChunkingConfig(3, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("word").append(i).append(" ");
        }
        String text = sb.toString().trim() + ".";

        // Should not loop forever — must terminate and emit at least one chunk
        List<LexicalChunk> chunks = chunker.chunk(text);
        assertFalse(chunks.isEmpty(), "Should produce at least one chunk for oversized sentence");
    }

    @Test
    void chunksHaveStableOrder() {
        LexicalChunkingConfig config = new LexicalChunkingConfig(20, 0);
        NlpTextChunker chunker = new NlpTextChunker(segmenter, tokenCounter, config);

        String text = "First part of the document. Second part of the document. Third part. "
                + "Fourth part with extra words. Fifth and final part of this document.";
        List<LexicalChunk> chunks = chunker.chunk(text);
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }

    @Test
    void configValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LexicalChunkingConfig(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LexicalChunkingConfig(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LexicalChunkingConfig(100, -1));
    }

    @Test
    void defaultConfigValues() {
        LexicalChunkingConfig config = new LexicalChunkingConfig();
        assertEquals(350, config.chunkSizeTokens());
        assertEquals(1, config.overlapSentences());
    }
}
