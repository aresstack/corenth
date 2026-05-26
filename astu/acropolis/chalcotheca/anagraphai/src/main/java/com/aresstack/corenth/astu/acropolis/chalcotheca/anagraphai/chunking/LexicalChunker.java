package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalChunk;
import java.util.List;

/**
 * Port interface for splitting text into token-budgeted lexical chunks.
 */
public interface LexicalChunker {
    /**
     * Splits the given text into token-budgeted chunks suitable for lexical indexing.
     *
     * @param text the text to chunk (may contain Markdown)
     * @return ordered list of lexical chunks with stable zero-based indexes
     */
    List<LexicalChunk> chunk(String text);
}
