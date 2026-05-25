package com.aresstack.corenth.adyton;

import org.junit.Test;

import static org.junit.Assert.*;

public class AdytonApiTest {

    @Test
    public void secretRefRejectsNull() {
        try {
            new SecretRef(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void secretRefEquality() {
        SecretRef a = new SecretRef("s1");
        SecretRef b = new SecretRef("s1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void secretRefToStringDoesNotRevealId() {
        SecretRef ref = new SecretRef("my-secret-id");
        assertFalse(ref.toString().contains("my-secret-id"));
    }

    @Test
    public void credentialRefRejectsNulls() {
        try {
            new CredentialRef(null, new SecretRef("x"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            new CredentialRef("user", null);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void credentialRefExposesIdentityNotSecret() {
        CredentialRef ref = new CredentialRef("admin", new SecretRef("pwd123"));
        assertEquals("admin", ref.principal());
        assertEquals(new SecretRef("pwd123"), ref.secretRef());
        // toString must not leak secret ref id
        assertFalse(ref.toString().contains("pwd123"));
    }

    @Test
    public void credentialRequestFields() {
        CredentialRequest req = new CredentialRequest("mainframe", "user1", "login");
        assertEquals("mainframe", req.targetSystem());
        assertEquals("user1", req.principal());
        assertEquals("login", req.purpose());
    }

    @Test
    public void credentialRequestAllowsNullPurpose() {
        CredentialRequest req = new CredentialRequest("sys", "user", null);
        assertNull(req.purpose());
    }

    @Test
    public void credentialLeaseExpiration() {
        CredentialRef cred = new CredentialRef("u", new SecretRef("s"));
        CredentialLease lease = new CredentialLease(cred, 5000L);

        assertFalse(lease.isExpired(4999L));
        assertTrue(lease.isExpired(5000L));
        assertTrue(lease.isExpired(6000L));
    }

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
    public void sessionCachePutAndGet() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialRef cred = new CredentialRef("u", new SecretRef("s"));
        CredentialLease lease = new CredentialLease(cred, 10000L);

        cache.put("sys:u", lease);
        assertEquals(lease, cache.get("sys:u", 5000L));
        assertEquals(1, cache.size());
    }

    @Test
    public void sessionCacheExpiredEntryReturnsNull() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialRef cred = new CredentialRef("u", new SecretRef("s"));
        CredentialLease lease = new CredentialLease(cred, 1000L);

        cache.put("key", lease);
        assertNull(cache.get("key", 2000L));
        assertEquals(0, cache.size());
    }

    @Test
    public void sessionCacheClear() {
        SessionCredentialCache cache = new SessionCredentialCache();
        CredentialRef cred = new CredentialRef("u", new SecretRef("s"));
        cache.put("a", new CredentialLease(cred, 9999L));
        cache.put("b", new CredentialLease(cred, 9999L));

        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    public void secretUnavailableExceptionMessage() {
        SecretUnavailableException ex = new SecretUnavailableException("not found");
        assertEquals("not found", ex.getMessage());

        RuntimeException cause = new RuntimeException("root");
        SecretUnavailableException ex2 = new SecretUnavailableException("wrapped", cause);
        assertEquals(cause, ex2.getCause());
    }
}
