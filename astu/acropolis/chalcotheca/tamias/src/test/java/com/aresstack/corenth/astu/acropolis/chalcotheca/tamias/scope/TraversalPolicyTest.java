package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.BookmarkUri;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TraversalPolicyTest {

    private final ResourceScope scope = ResourceScope.of(BookmarkUri.parse("file:///data"));
    private final TraversalPolicy maxTwo = new TraversalPolicy(scope, 2);

    @Test
    public void resourceAtMaxDepth_isWithinDepth() {
        ScopeDecision decision = maxTwo.evaluate(BookmarkUri.parse("file:///data/a/b.txt"));

        assertEquals(ScopeReasonCode.WITHIN_DEPTH, decision.reasonCode());
        assertTrue(decision.isAdmitted());
    }

    @Test
    public void resourceBelowMaxDepth_exceedsDepth() {
        ScopeDecision decision = maxTwo.evaluate(BookmarkUri.parse("file:///data/a/b/c.txt"));

        assertEquals(ScopeReasonCode.DEPTH_EXCEEDED, decision.reasonCode());
        assertTrue(decision.isRejected());
    }

    @Test
    public void outOfScope_isReportedBeforeDepth() {
        assertEquals(ScopeReasonCode.OUT_OF_SCOPE, maxTwo.evaluate(BookmarkUri.parse("file:///other/a.txt")).reasonCode());
        assertEquals(ScopeReasonCode.OUT_OF_SCOPE, maxTwo.evaluateDescent(BookmarkUri.parse("file:///other")).reasonCode());
    }

    @Test
    public void descent_isAllowedBelowMaxDepthAndExhaustedAtIt() {
        assertEquals(ScopeReasonCode.DESCENT_ALLOWED, maxTwo.evaluateDescent(BookmarkUri.parse("file:///data/a")).reasonCode());
        assertEquals(ScopeReasonCode.DEPTH_EXHAUSTED, maxTwo.evaluateDescent(BookmarkUri.parse("file:///data/a/b")).reasonCode());
    }

    @Test
    public void maxDepthZero_admitsOnlyTheRoot() {
        TraversalPolicy rootOnly = new TraversalPolicy(scope, 0);

        assertTrue(rootOnly.evaluate(BookmarkUri.parse("file:///data")).isAdmitted());
        assertTrue(rootOnly.evaluate(BookmarkUri.parse("file:///data/a.txt")).isRejected());
        assertTrue(rootOnly.evaluateDescent(BookmarkUri.parse("file:///data")).isRejected());
    }

    @Test
    public void unlimited_admitsAnyDepthInsideScope() {
        TraversalPolicy unlimited = TraversalPolicy.unlimited(scope);

        assertFalse(unlimited.isLimited());
        assertTrue(unlimited.evaluate(BookmarkUri.parse("file:///data/a/b/c/d/e/f.txt")).isAdmitted());
        assertTrue(unlimited.evaluateDescent(BookmarkUri.parse("file:///data/a/b/c/d/e")).isAdmitted());
        assertTrue(unlimited.evaluate(BookmarkUri.parse("file:///elsewhere/f.txt")).isRejected());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaxDepthOtherThanUnlimited_isRejected() {
        new TraversalPolicy(scope, -2);
    }
}
