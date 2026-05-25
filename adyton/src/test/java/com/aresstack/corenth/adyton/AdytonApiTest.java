package com.aresstack.corenth.adyton;

import org.junit.Test;

import static org.junit.Assert.*;

public class AdytonApiTest {

    // ── CredentialRequest tests ──────────────────────────────────────────────

    @Test
    public void credentialRequestFields() {
        CredentialRequest req = new CredentialRequest("mainframe", "user1", "login");
        assertEquals("mainframe", req.targetSystem());
        assertEquals("user1", req.principal());
        assertEquals("login", req.purpose());
        assertNull(req.scope());
        assertEquals(0L, req.requestedTtlMillis());
    }

    @Test
    public void credentialRequestFullConstructor() {
        CredentialRequest req = new CredentialRequest(
                "mainframe", "user1", "batch-job", "read-only", 60000L);
        assertEquals("mainframe", req.targetSystem());
        assertEquals("user1", req.principal());
        assertEquals("batch-job", req.purpose());
        assertEquals("read-only", req.scope());
        assertEquals(60000L, req.requestedTtlMillis());
    }

    @Test
    public void credentialRequestCarriesTargetPurposeAndScope() {
        // Demonstrates that a request carries all required scoping information
        CredentialRequest req = new CredentialRequest(
                "host:3270", "BATCH_USER", "nightly-job", "submit-jcl", 300000L);
        assertEquals("host:3270", req.targetSystem());
        assertEquals("BATCH_USER", req.principal());
        assertEquals("nightly-job", req.purpose());
        assertEquals("submit-jcl", req.scope());
        assertEquals(300000L, req.requestedTtlMillis());
    }

    @Test
    public void credentialRequestAllowsNullPurpose() {
        CredentialRequest req = new CredentialRequest("sys", "user", null);
        assertNull(req.purpose());
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialRequestRejectsNullTarget() {
        new CredentialRequest(null, "user", "purpose");
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialRequestRejectsEmptyPrincipal() {
        new CredentialRequest("sys", "", "purpose");
    }

    // ── CredentialLease tests ────────────────────────────────────────────────

    @Test
    public void leaseCarriesGrantSemantics() {
        CredentialLease lease = new CredentialLease(
                "lease-001", "mainframe", "user1", "login", "read-only", 10000L);
        assertEquals("lease-001", lease.leaseId());
        assertEquals("mainframe", lease.targetSystem());
        assertEquals("user1", lease.principal());
        assertEquals("login", lease.purpose());
        assertEquals("read-only", lease.scope());
        assertEquals(10000L, lease.expiresAtEpochMillis());
    }

    @Test
    public void leaseExpiresAndIsRejected() {
        CredentialLease lease = new CredentialLease(
                "id1", "sys", "user", "purpose", null, 5000L);
        assertFalse("Lease should be valid before expiration", lease.isExpired(4999L));
        assertTrue("Lease should be expired at exact boundary", lease.isExpired(5000L));
        assertTrue("Lease should be expired after boundary", lease.isExpired(6000L));
    }

    @Test
    public void leaseToStringDoesNotRevealLeaseId() {
        CredentialLease lease = new CredentialLease(
                "secret-lease-token-xyz", "sys", "user", null, null, 9999L);
        // toString must not reveal the opaque lease id
        assertFalse(lease.toString().contains("secret-lease-token-xyz"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void leaseRejectsNullLeaseId() {
        new CredentialLease(null, "sys", "user", null, null, 1000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void leaseRejectsEmptyTarget() {
        new CredentialLease("id", "", "user", null, null, 1000L);
    }

    // ── SecretRef tests ──────────────────────────────────────────────────────

    @Test
    public void secretRefToStringDoesNotRevealId() {
        SecretRef ref = new SecretRef("my-secret-id");
        assertFalse("toString must not reveal secret id", ref.toString().contains("my-secret-id"));
    }

    @Test
    public void secretRefEquality() {
        SecretRef a = new SecretRef("s1");
        SecretRef b = new SecretRef("s1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void secretRefRejectsNull() {
        new SecretRef(null);
    }

    // ── CredentialRef tests ──────────────────────────────────────────────────

    @Test
    public void credentialRefDoesNotExposePlaintextCredentials() {
        CredentialRef ref = new CredentialRef("admin", new SecretRef("pwd123"));
        assertEquals("admin", ref.principal());
        // toString must not leak secret ref id
        assertFalse(ref.toString().contains("pwd123"));
        // The API has no method to get plaintext password
        assertEquals(new SecretRef("pwd123"), ref.secretRef());
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialRefRejectsNullPrincipal() {
        new CredentialRef(null, new SecretRef("x"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialRefRejectsNullSecret() {
        new CredentialRef("user", null);
    }

    // ── DelegatedAccessResult tests ──────────────────────────────────────────

    @Test
    public void delegatedAccessResultFactory() {
        DelegatedAccessResult ok = DelegatedAccessResult.success("tok-123");
        assertTrue(ok.isSuccess());
        assertEquals("tok-123", ok.token());

        DelegatedAccessResult fail = DelegatedAccessResult.failure();
        assertFalse(fail.isSuccess());
        assertNull(fail.token());
    }

    @Test
    public void delegatedAccessResultToStringDoesNotRevealToken() {
        DelegatedAccessResult result = DelegatedAccessResult.success("sensitive-session-token");
        assertFalse(result.toString().contains("sensitive-session-token"));
    }

    // ── SessionCredentialCache tests ─────────────────────────────────────────

    @Test
    public void sessionCachePutAndGet() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialLease lease = new CredentialLease(
                "l1", "sys", "u", "p", null, 10000L);
        cache.put("sys:u", lease);
        assertEquals(lease, cache.get("sys:u", 5000L));
        assertEquals(1, cache.size());
    }

    @Test
    public void sessionCacheExpiredEntryReturnsNull() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialLease lease = new CredentialLease(
                "l1", "sys", "u", "p", null, 1000L);
        cache.put("key", lease);
        assertNull("Expired lease must be rejected", cache.get("key", 2000L));
        assertEquals(0, cache.size());
    }

    @Test
    public void sessionCacheClear() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialLease lease = new CredentialLease(
                "l1", "sys", "u", "p", null, 9999L);
        cache.put("a", lease);
        cache.put("b", lease);
        cache.clear();
        assertEquals(0, cache.size());
    }

    // ── Delegated access flow without SecretRef ──────────────────────────────

    @Test
    public void delegatedAccessFlowWithoutSecretRef() throws SecretUnavailableException {
        // Demonstrates that a normal module can use the delegated access API
        // without ever touching SecretRef or CredentialRef

        DelegatedAccessProvider provider = new DelegatedAccessProvider() {
            @Override
            public CredentialLease request(CredentialRequest request) {
                return new CredentialLease(
                        "grant-" + request.targetSystem(),
                        request.targetSystem(),
                        request.principal(),
                        request.purpose(),
                        request.scope(),
                        System.currentTimeMillis() + 60000L);
            }

            @Override
            public DelegatedAccessResult authenticate(CredentialLease lease, String target) {
                if (lease.isExpired(System.currentTimeMillis())) {
                    return DelegatedAccessResult.failure();
                }
                return DelegatedAccessResult.success("session-ok");
            }

            @Override
            public void revoke(CredentialLease lease) {
                // no-op for test
            }
        };

        // Module-facing flow: request → lease → authenticate
        CredentialRequest req = new CredentialRequest(
                "mainframe", "BATCH_USER", "nightly-job", "submit-jcl", 60000L);
        CredentialLease lease = provider.request(req);

        assertNotNull(lease);
        assertEquals("mainframe", lease.targetSystem());
        assertEquals("BATCH_USER", lease.principal());
        assertEquals("nightly-job", lease.purpose());
        assertEquals("submit-jcl", lease.scope());

        DelegatedAccessResult result = provider.authenticate(lease, "host:3270");
        assertTrue(result.isSuccess());
    }

    // ── Exception tests ──────────────────────────────────────────────────────

    @Test
    public void secretUnavailableExceptionMessage() {
        SecretUnavailableException ex = new SecretUnavailableException("not found");
        assertEquals("not found", ex.getMessage());

        RuntimeException cause = new RuntimeException("root");
        SecretUnavailableException ex2 = new SecretUnavailableException("wrapped", cause);
        assertEquals(cause, ex2.getCause());
    }

    @Test
    public void authCancelledIsSubtypeOfSecretUnavailable() {
        AuthCancelledException ex = new AuthCancelledException();
        assertTrue(ex instanceof SecretUnavailableException);
    }
}
