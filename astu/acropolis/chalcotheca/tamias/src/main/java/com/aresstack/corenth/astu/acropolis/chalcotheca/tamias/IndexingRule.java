package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single indexing rule: match resources by scheme, patterns, and size.
 */
public final class IndexingRule {

    private final String name;
    private final List<String> schemes;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final long maxBytes;

    public IndexingRule(String name, List<String> schemes,
                        List<String> includePatterns, List<String> excludePatterns,
                        long maxBytes) {
        this.name = name != null ? name : "unnamed";
        this.schemes = schemes != null
                ? Collections.unmodifiableList(new ArrayList<String>(schemes))
                : Collections.<String>emptyList();
        this.includePatterns = includePatterns != null
                ? Collections.unmodifiableList(new ArrayList<String>(includePatterns))
                : Collections.<String>emptyList();
        this.excludePatterns = excludePatterns != null
                ? Collections.unmodifiableList(new ArrayList<String>(excludePatterns))
                : Collections.<String>emptyList();
        this.maxBytes = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
    }

    public String name() { return name; }
    public List<String> schemes() { return schemes; }
    public List<String> includePatterns() { return includePatterns; }
    public List<String> excludePatterns() { return excludePatterns; }
    public long maxBytes() { return maxBytes; }
}
