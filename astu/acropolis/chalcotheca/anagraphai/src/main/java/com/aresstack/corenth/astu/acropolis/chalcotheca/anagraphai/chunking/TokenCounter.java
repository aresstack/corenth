package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

/**
 * Port interface for counting tokens using the same analyzer assumptions as lexical indexing.
 */
public interface TokenCounter {
    /**
     * Counts the number of tokens that the shared analyzer produces for the given text.
     *
     * @param text the input text
     * @return the token count
     */
    int countTokens(String text);
}
