package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceScopeTest {

    private final ResourceScope scope = ResourceScope.of(BookmarkUri.parse("file:///data/docs"));

    @Test
    public void root_isInScopeAtDepthZero() {
        assertEquals(0, scope.depthOf(BookmarkUri.parse("file:///data/docs")));
        assertEquals(0, scope.depthOf(BookmarkUri.parse("file:///data/docs/")));
    }

    @Test
    public void descendants_areInScopeWithSegmentDepth() {
        assertEquals(1, scope.depthOf(BookmarkUri.parse("file:///data/docs/a.txt")));
        assertEquals(3, scope.depthOf(BookmarkUri.parse("file:///data/docs/x/y/z.txt")));
        assertEquals(ScopeReasonCode.IN_SCOPE, scope.evaluate(BookmarkUri.parse("file:///data/docs/a.txt")).reasonCode());
    }

    @Test
    public void siblingWithSharedStringPrefix_isOutOfScope() {
        assertFalse(scope.contains(BookmarkUri.parse("file:///data/docsarchive/a.txt")));
        assertFalse(scope.contains(BookmarkUri.parse("file:///data")));
    }

    @Test
    public void otherScheme_isOutOfScope() {
        ScopeDecision decision = scope.evaluate(BookmarkUri.parse("ftp:///data/docs/a.txt"));

        assertEquals(ScopeReasonCode.OUT_OF_SCOPE, decision.reasonCode());
        assertTrue(decision.isRejected());
    }

    @Test
    public void otherAuthority_isOutOfScope() {
        ResourceScope share = ResourceScope.of(BookmarkUri.parse("file://server/share"));

        assertTrue(share.contains(BookmarkUri.parse("file://SERVER/share/a.txt")));
        assertFalse(share.contains(BookmarkUri.parse("file://other/share/a.txt")));
    }

    @Test
    public void dotSegments_areNormalizedBeforeContainment() {
        assertEquals(1, scope.depthOf(BookmarkUri.parse("file:///data/docs/x/../a.txt")));
        assertFalse(scope.contains(BookmarkUri.parse("file:///data/docs/../secret/a.txt")));
    }

    @Test
    public void escapingAboveFirstSegment_isNeverInScope() {
        ResourceScope opaque = ResourceScope.of(BookmarkUri.parse("ndv://host/lib"));

        assertFalse(opaque.contains(BookmarkUri.parse("ndv://../host/lib/prog")));
    }

    @Test
    public void opaqueSchemes_useTheSameSegmentRules() {
        ResourceScope library = ResourceScope.of(BookmarkUri.parse("ndv://mainframe/system"));

        assertEquals(1, library.depthOf(BookmarkUri.parse("ndv://mainframe/system/program.cgp")));
        assertEquals(ResourceScope.NOT_IN_SCOPE, library.depthOf(BookmarkUri.parse("ndv://mainframe/systems/program.cgp")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullRoot_isRejected() {
        ResourceScope.of(null);
    }
}
