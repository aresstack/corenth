package com.aresstack.corenth.proasteion.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable host-supplied settings for composing the local Corenth backend.
 *
 * <p>The settings only carry values a host decides (where the index lives, which local roots
 * may be read, which files should be indexed). They contain no adapter or policy types; the
 * {@link CorenthComposition} translates them into the Tamias policies and adapters.
 */
public final class ApplicationSettings {

    /** Subject id used for the lifecycle actor when the host does not choose one. */
    public static final String DEFAULT_LIFECYCLE_ACTOR_ID = "corenth-resource-lifecycle";

    private final Path indexDirectory;
    private final List<Path> accessibleRoots;
    private final List<String> indexedPatterns;
    private final List<String> excludedPatterns;
    private final long maxIndexedBytes;
    private final String lifecycleActorId;
    private final boolean sourceRefresh;

    private ApplicationSettings(Builder builder) {
        this.indexDirectory = builder.indexDirectory;
        this.accessibleRoots = Collections.unmodifiableList(new ArrayList<Path>(builder.accessibleRoots));
        this.indexedPatterns = Collections.unmodifiableList(new ArrayList<String>(builder.indexedPatterns));
        this.excludedPatterns = Collections.unmodifiableList(new ArrayList<String>(builder.excludedPatterns));
        this.maxIndexedBytes = builder.maxIndexedBytes;
        this.lifecycleActorId = builder.lifecycleActorId;
        this.sourceRefresh = builder.sourceRefresh;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Directory of the lexical index. */
    public Path indexDirectory() {
        return indexDirectory;
    }

    /** Local directories whose contents the mediated access may read; never empty. */
    public List<Path> accessibleRoots() {
        return accessibleRoots;
    }

    /** Glob patterns of files to index; empty means every file below the accessible roots. */
    public List<String> indexedPatterns() {
        return indexedPatterns;
    }

    /** Glob patterns of files that are never indexed. */
    public List<String> excludedPatterns() {
        return excludedPatterns;
    }

    /** Maximum size of an indexed file in bytes; {@code 0} means unlimited. */
    public long maxIndexedBytes() {
        return maxIndexedBytes;
    }

    /** Subject id of the service actor under which the lifecycle requests resources. */
    public String lifecycleActorId() {
        return lifecycleActorId;
    }

    /**
     * Returns whether the lifecycle may re-read a local source whose payload the archive counter
     * already holds (Tamias {@code REFRESH_EXTERNAL}, #10 Slice 5). Defaults to {@code true}, so
     * changed local files are picked up; {@code false} serves cached payloads until they are
     * invalidated.
     */
    public boolean sourceRefresh() {
        return sourceRefresh;
    }

    public static final class Builder {
        private Path indexDirectory;
        private final List<Path> accessibleRoots = new ArrayList<Path>();
        private final List<String> indexedPatterns = new ArrayList<String>();
        private final List<String> excludedPatterns = new ArrayList<String>();
        private long maxIndexedBytes;
        private String lifecycleActorId = DEFAULT_LIFECYCLE_ACTOR_ID;
        private boolean sourceRefresh = true;

        private Builder() {}

        public Builder indexDirectory(Path indexDirectory) {
            this.indexDirectory = indexDirectory;
            return this;
        }

        public Builder accessibleRoot(Path root) {
            if (root == null) {
                throw new IllegalArgumentException("root must not be null");
            }
            this.accessibleRoots.add(root);
            return this;
        }

        public Builder indexedPattern(String pattern) {
            this.indexedPatterns.add(requireText(pattern, "indexed pattern"));
            return this;
        }

        public Builder excludedPattern(String pattern) {
            this.excludedPatterns.add(requireText(pattern, "excluded pattern"));
            return this;
        }

        public Builder maxIndexedBytes(long maxIndexedBytes) {
            if (maxIndexedBytes < 0) {
                throw new IllegalArgumentException("maxIndexedBytes must not be negative");
            }
            this.maxIndexedBytes = maxIndexedBytes;
            return this;
        }

        public Builder lifecycleActorId(String lifecycleActorId) {
            this.lifecycleActorId = requireText(lifecycleActorId, "lifecycle actor id");
            return this;
        }

        /** Grants or withholds source refresh for local files (#10 Slice 5); default {@code true}. */
        public Builder sourceRefresh(boolean sourceRefresh) {
            this.sourceRefresh = sourceRefresh;
            return this;
        }

        public ApplicationSettings build() {
            if (indexDirectory == null) {
                throw new IllegalStateException("indexDirectory is required");
            }
            if (accessibleRoots.isEmpty()) {
                throw new IllegalStateException("at least one accessible root is required");
            }
            return new ApplicationSettings(this);
        }

        private static String requireText(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be null or blank");
            }
            return value;
        }
    }
}
