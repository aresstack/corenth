package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AcceptanceDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Document how a caller composes the new scope/traversal/size policies with the existing
 * include/exclude rules: each answers its own question, none duplicates the other.
 */
public class ScopeAndPatternPolicyCompositionTest {

    private final TraversalPolicy traversal = new TraversalPolicy(ResourceScope.of(BookmarkUri.parse("file:///data")), 3);
    private final ResourcePolicy patterns = new PatternResourcePolicy(Collections.singletonList(
            new IndexingRule("docs", Collections.singletonList("file"),
                    Arrays.asList("**/*.txt", "**/*.md"), Collections.singletonList("**/tmp/**"), 0)));
    private final ResourceSizePolicy size = ResourceSizePolicy.maxBytes(100);

    @Test
    public void inScopeButExcludedByPattern_isDeniedByThePatternPolicyOnly() {
        BookmarkUri uri = BookmarkUri.parse("file:///data/tmp/a.txt");

        assertTrue(traversal.evaluate(uri).isAdmitted());
        assertEquals(AcceptanceDecision.DENY, patterns.evaluate(file(uri), 10).decision());
    }

    @Test
    public void includedByPatternButOutOfScope_isRejectedByTheScopeOnly() {
        BookmarkUri uri = BookmarkUri.parse("file:///other/a.txt");

        assertEquals(AcceptanceDecision.ACCEPT, patterns.evaluate(file(uri), 10).decision());
        assertEquals(ScopeReasonCode.OUT_OF_SCOPE, traversal.evaluate(uri).reasonCode());
    }

    @Test
    public void unknownSizeBeforeAcquisition_keepsThePatternDecisionAndDefersTheSizeDecision() {
        BookmarkUri uri = BookmarkUri.parse("file:///data/a.md");

        assertEquals(AcceptanceDecision.ACCEPT, patterns.evaluate(file(uri), ResourcePolicy.SIZE_UNKNOWN).decision());
        assertTrue(size.evaluate(ResourcePolicy.SIZE_UNKNOWN).isUndetermined());
        assertTrue(size.evaluate(101).isRejected());
    }

    private static VirtualResourceRef file(BookmarkUri uri) {
        return new VirtualResourceRef(uri, VirtualResourceKind.FILE);
    }
}
