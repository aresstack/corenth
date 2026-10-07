package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ScopeDecisionTest {

    @Test
    public void everyReasonCode_impliesItsVerdict() {
        for (ScopeReasonCode code : ScopeReasonCode.values()) {
            assertEquals(code.verdict(), new ScopeDecision(code, "x").verdict());
        }
    }

    @Test
    public void reasonCodes_areStable() {
        assertEquals("IN_SCOPE, OUT_OF_SCOPE, WITHIN_DEPTH, DEPTH_EXCEEDED, DESCENT_ALLOWED, DEPTH_EXHAUSTED, "
                        + "NO_SIZE_LIMIT, SIZE_WITHIN_LIMIT, SIZE_OVER_LIMIT, SIZE_UNKNOWN",
                java.util.Arrays.toString(ScopeReasonCode.values()).replace("[", "").replace("]", ""));
        assertEquals(ScopeVerdict.UNDETERMINED, ScopeReasonCode.SIZE_UNKNOWN.verdict());
    }

    @Test
    public void decisions_areValueObjects() {
        assertEquals(new ScopeDecision(ScopeReasonCode.IN_SCOPE, "a"), new ScopeDecision(ScopeReasonCode.IN_SCOPE, "a"));
        assertNotEquals(new ScopeDecision(ScopeReasonCode.IN_SCOPE, "a"), new ScopeDecision(ScopeReasonCode.OUT_OF_SCOPE, "a"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingReasonCode_isRejected() {
        new ScopeDecision(null, "x");
    }
}
