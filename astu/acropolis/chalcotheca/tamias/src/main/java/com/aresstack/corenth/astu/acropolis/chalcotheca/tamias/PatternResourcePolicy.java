package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.VirtualResourceRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A deny-by-default resource policy backed by configurable rules.
 *
 * <p>Evaluates rules in order. The first matching rule that passes all checks
 * (scheme, include, exclude, size) accepts the resource. If no rule matches,
 * the resource is denied.
 */
public final class PatternResourcePolicy implements ResourcePolicy {

    private final List<IndexingRule> rules;

    public PatternResourcePolicy(List<IndexingRule> rules) {
        this.rules = rules != null
                ? Collections.unmodifiableList(new ArrayList<IndexingRule>(rules))
                : Collections.<IndexingRule>emptyList();
    }

    @Override
    public PolicyReason evaluate(VirtualResourceRef ref, long sizeBytes) {
        if (ref == null) {
            return new PolicyReason(AcceptanceDecision.DENY, "null resource reference");
        }

        String scheme = ref.uri().scheme().name();
        String path = ref.uri().schemeSpecificPart();

        for (IndexingRule rule : rules) {
            // Check scheme match
            if (!rule.schemes().isEmpty() && !rule.schemes().contains(scheme)) {
                continue;
            }

            // Check exclude patterns first
            if (matchesAny(path, rule.excludePatterns())) {
                return new PolicyReason(AcceptanceDecision.DENY,
                        "excluded by pattern in rule '" + rule.name() + "'");
            }

            // Check include patterns
            if (!rule.includePatterns().isEmpty() && !matchesAny(path, rule.includePatterns())) {
                continue;
            }

            // Check size
            if (sizeBytes > rule.maxBytes()) {
                return new PolicyReason(AcceptanceDecision.DENY,
                        "exceeds maxBytes (" + sizeBytes + " > " + rule.maxBytes()
                                + ") in rule '" + rule.name() + "'");
            }

            return new PolicyReason(AcceptanceDecision.ACCEPT,
                    "accepted by rule '" + rule.name() + "'");
        }

        return new PolicyReason(AcceptanceDecision.DENY, "no matching rule (default deny)");
    }

    /**
     * Simple glob matching supporting {@code *} and {@code **}.
     */
    static boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (globMatches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Minimal glob matcher:
     * - {@code **} matches any sequence of characters including path separators
     * - {@code *} matches any sequence of characters except path separators
     */
    static boolean globMatches(String pattern, String text) {
        // Convert glob to regex
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i += 2;
                    // Skip trailing /
                    if (i < pattern.length() && (pattern.charAt(i) == '/' || pattern.charAt(i) == '\\')) {
                        regex.append("[/\\\\]?");
                        i++;
                    }
                } else {
                    regex.append("[^/\\\\]*");
                    i++;
                }
            } else if (c == '?') {
                regex.append("[^/\\\\]");
                i++;
            } else if (c == '.' || c == '(' || c == ')' || c == '+'
                    || c == '{' || c == '}' || c == '[' || c == ']'
                    || c == '^' || c == '$' || c == '|' || c == '\\') {
                regex.append('\\').append(c);
                i++;
            } else {
                regex.append(c);
                i++;
            }
        }
        return text.matches(regex.toString());
    }
}
