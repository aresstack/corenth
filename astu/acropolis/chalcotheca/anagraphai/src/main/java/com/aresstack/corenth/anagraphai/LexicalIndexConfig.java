package com.aresstack.corenth.anagraphai;

import java.nio.file.Path;

/**
 * Configuration for a lexical index instance.
 *
 * <p>Encapsulates the storage location and any index-level settings needed
 * by the implementation. This object is the sole configuration entry point
 * — the index never reads global settings or hard-coded paths.
 */
public final class LexicalIndexConfig {

    private final Path indexDirectory;

    /**
     * Creates a configuration with the given index storage directory.
     *
     * @param indexDirectory the directory where the Lucene index is stored
     */
    public LexicalIndexConfig(Path indexDirectory) {
        if (indexDirectory == null) {
            throw new IllegalArgumentException("indexDirectory must not be null");
        }
        this.indexDirectory = indexDirectory;
    }

    /** Returns the directory where the lexical index is stored. */
    public Path indexDirectory() {
        return indexDirectory;
    }
}
