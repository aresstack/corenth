package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.scope;

import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourceSizePolicyTest {

    private final ResourceSizePolicy limit = ResourceSizePolicy.maxBytes(1024);

    @Test
    public void sizeWithinAndExactlyAtLimit_isAdmitted() {
        assertEquals(ScopeReasonCode.SIZE_WITHIN_LIMIT, limit.evaluate(0).reasonCode());
        assertEquals(ScopeReasonCode.SIZE_WITHIN_LIMIT, limit.evaluate(1024).reasonCode());
        assertTrue(limit.evaluate(1024).isAdmitted());
    }

    @Test
    public void sizeOverLimit_isRejected() {
        ScopeDecision decision = limit.evaluate(1025);

        assertEquals(ScopeReasonCode.SIZE_OVER_LIMIT, decision.reasonCode());
        assertTrue(decision.isRejected());
    }

    @Test
    public void unknownSize_isUndeterminedAndNeverTreatedAsEmpty() {
        ScopeDecision decision = limit.evaluate(ResourcePolicy.SIZE_UNKNOWN);

        assertEquals(ScopeReasonCode.SIZE_UNKNOWN, decision.reasonCode());
        assertEquals(ScopeVerdict.UNDETERMINED, decision.verdict());
        assertFalse(decision.isAdmitted());
        assertFalse(decision.isRejected());
        assertTrue(decision.isUndetermined());
    }

    @Test
    public void unlimited_admitsKnownAndUnknownSizes() {
        ResourceSizePolicy unlimited = ResourceSizePolicy.unlimited();

        assertFalse(unlimited.isLimited());
        assertEquals(ScopeReasonCode.NO_SIZE_LIMIT, unlimited.evaluate(Long.MAX_VALUE).reasonCode());
        assertEquals(ScopeReasonCode.NO_SIZE_LIMIT, unlimited.evaluate(ResourcePolicy.SIZE_UNKNOWN).reasonCode());
    }

    @Test
    public void zeroLimit_admitsOnlyEmptyResources() {
        ResourceSizePolicy emptyOnly = ResourceSizePolicy.maxBytes(0);

        assertTrue(emptyOnly.evaluate(0).isAdmitted());
        assertTrue(emptyOnly.evaluate(1).isRejected());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeLimit_isRejected() {
        ResourceSizePolicy.maxBytes(-1);
    }
}
