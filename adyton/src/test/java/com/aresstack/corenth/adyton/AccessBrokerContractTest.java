package com.aresstack.corenth.adyton;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the access-broker contracts introduced in the rework.
 */
public class AccessBrokerContractTest {

    // ── AccessRequest tests ──────────────────────────────────────────────────

    @Test
    public void accessRequestCarriesAllFields() {
        AccessRequest req = new AccessRequest(
                "ftp:mainframe", "BATCH_USER", "nightly-job",
                "upload-jcl", AuthenticationMethod.FTP_PASSWORD, 300000L);
        assertEquals("ftp:mainframe", req.targetSystem());
        assertEquals("BATCH_USER", req.principal());
        assertEquals("nightly-job", req.purpose());
        assertEquals("upload-jcl", req.scope());
        assertEquals(AuthenticationMethod.FTP_PASSWORD, req.method());
        assertEquals(300000L, req.requestedTtlMillis());
    }

    @Test
    public void accessRequestToStringDoesNotRevealSensitiveInfo() {
        AccessRequest req = new AccessRequest(
                "wiki:internal", "svc-account", "sync",
                "read", AuthenticationMethod.MEDIA_WIKI_LOGIN, 60000L);
        String s = req.toString();
        // Does show target and principal (non-secret metadata)
        assertTrue(s.contains("wiki:internal"));
        assertTrue(s.contains("svc-account"));
        // Does not reveal any password/token
        assertFalse(s.contains("password"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void accessRequestRejectsNullTarget() {
        new AccessRequest(null, "user", "p", "s", AuthenticationMethod.FTP_PASSWORD, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void accessRequestRejectsNullMethod() {
        new AccessRequest("sys", "user", "p", "s", null, 0L);
    }

    // ── AccessGrant tests ────────────────────────────────────────────────────

    @Test
    public void accessGrantCarriesGrantSemantics() {
        AccessGrant grant = new AccessGrant(
                "grant-001", "mainframe", "user1", "login", "read-only", 10000L);
        assertEquals("grant-001", grant.grantId());
        assertEquals("mainframe", grant.targetSystem());
        assertEquals("user1", grant.principal());
        assertEquals("login", grant.purpose());
        assertEquals("read-only", grant.scope());
        assertEquals(10000L, grant.expiresAtEpochMillis());
    }

    @Test
    public void accessGrantExpiresAndIsRejected() {
        AccessGrant grant = new AccessGrant("g1", "sys", "user", "p", null, 5000L);
        assertFalse(grant.isExpired(4999L));
        assertTrue(grant.isExpired(5000L));
        assertTrue(grant.isExpired(6000L));
    }

    @Test
    public void accessGrantToStringDoesNotRevealGrantId() {
        AccessGrant grant = new AccessGrant(
                "secret-grant-token-abc", "sys", "user", null, null, 9999L);
        assertFalse(grant.toString().contains("secret-grant-token-abc"));
    }

    // ── AccessBroker withAccess flow ─────────────────────────────────────────

    @Test
    public void brokerWithAccessFlowWithoutSecretRef() throws Exception {
        // Demonstrates a connector using the broker without ever touching
        // SecretRef, CredentialRef, or SecretMaterial directly

        AuthenticationStrategy<TestHandle> strategy = new AuthenticationStrategy<TestHandle>() {
            @Override
            public boolean supports(AuthenticationMethod method) {
                return AuthenticationMethod.FTP_PASSWORD.equals(method);
            }

            @Override
            public TestHandle authenticate(AccessRequest request, SecretMaterial material) {
                AccessGrant grant = new AccessGrant(
                        "g-" + request.targetSystem(),
                        request.targetSystem(),
                        request.principal(),
                        request.purpose(),
                        request.scope(),
                        System.currentTimeMillis() + 60000L);
                return new TestHandle(grant);
            }
        };

        AccessBroker broker = new AccessBroker() {
            @Override
            public <H extends AccessHandle, R> R withAccess(
                    AccessRequest request,
                    AuthenticationStrategy<H> strat,
                    AccessOperation<H, R> operation)
                    throws AccessException {
                SecretMaterial material = new DefaultSecretMaterial("internal-ref");
                try {
                    @SuppressWarnings("unchecked")
                    H handle = (H) strat.authenticate(request, material);
                    try {
                        return operation.execute(handle);
                    } catch (Exception e) {
                        throw new AccessException("Operation failed", e);
                    } finally {
                        handle.close();
                    }
                } catch (AccessException e) {
                    throw e;
                }
            }

            @Override
            public <H extends AccessHandle> H acquire(
                    AccessRequest request,
                    AuthenticationStrategy<H> strat)
                    throws AccessException {
                SecretMaterial material = new DefaultSecretMaterial("internal-ref");
                return strat.authenticate(request, material);
            }

            @Override
            public void revoke(AccessGrant grant) {
                // no-op for test
            }
        };

        AccessRequest req = new AccessRequest(
                "ftp:mainframe", "BATCH_USER", "nightly-job",
                "upload-jcl", AuthenticationMethod.FTP_PASSWORD, 60000L);

        // Connector code: uses broker, never sees SecretRef or raw passwords
        String result = broker.withAccess(req, strategy, handle -> {
            assertNotNull(handle.grant());
            assertEquals("ftp:mainframe", handle.grant().targetSystem());
            return "uploaded-ok";
        });

        assertEquals("uploaded-ok", result);
    }

    @Test
    public void brokerAcquireReturnsReusableHandle() throws Exception {
        AuthenticationStrategy<TestHandle> strategy = new AuthenticationStrategy<TestHandle>() {
            @Override
            public boolean supports(AuthenticationMethod method) {
                return method == AuthenticationMethod.MEDIA_WIKI_LOGIN;
            }

            @Override
            public TestHandle authenticate(AccessRequest request, SecretMaterial material) {
                AccessGrant grant = new AccessGrant(
                        "wiki-grant", request.targetSystem(),
                        request.principal(), request.purpose(),
                        request.scope(),
                        System.currentTimeMillis() + 300000L);
                return new TestHandle(grant);
            }
        };

        AccessBroker broker = new AccessBroker() {
            @Override
            public <H extends AccessHandle, R> R withAccess(
                    AccessRequest request, AuthenticationStrategy<H> strat,
                    AccessOperation<H, R> operation) throws AccessException {
                throw new UnsupportedOperationException("use acquire for this test");
            }

            @Override
            public <H extends AccessHandle> H acquire(
                    AccessRequest request, AuthenticationStrategy<H> strat)
                    throws AccessException {
                SecretMaterial material = new DefaultSecretMaterial("wiki-secret");
                return strat.authenticate(request, material);
            }

            @Override
            public void revoke(AccessGrant grant) {}
        };

        AccessRequest req = new AccessRequest(
                "wiki:internal", "svc-user", "search",
                "read", AuthenticationMethod.MEDIA_WIKI_LOGIN, 300000L);

        // Acquire for reuse (search-as-you-type, repeated operations)
        TestHandle handle = broker.acquire(req, strategy);
        assertNotNull(handle);
        assertEquals("wiki:internal", handle.grant().targetSystem());

        // Reuse across multiple operations
        assertFalse(handle.isClosed());
        handle.close();
        assertTrue(handle.isClosed());
    }

    // ── SecretMaterial is internal ───────────────────────────────────────────

    @Test
    public void secretMaterialToStringDoesNotRevealContent() {
        SecretMaterial material = new DefaultSecretMaterial("super-secret-ref-id");
        assertFalse(material.toString().contains("super-secret-ref-id"));
        assertEquals("SecretMaterial{***}", material.toString());
    }

    // ── SecretCachePolicy tests ──────────────────────────────────────────────

    @Test
    public void defaultPolicyValues() {
        SecretCachePolicy policy = SecretCachePolicy.defaultPolicy();
        assertTrue(policy.isEnabled());
        assertEquals(60 * 60 * 1000L, policy.ttlMillis());
        assertEquals(10 * 60 * 1000L, policy.idleTimeoutMillis());
    }

    @Test
    public void disabledPolicy() {
        SecretCachePolicy policy = SecretCachePolicy.disabled();
        assertFalse(policy.isEnabled());
    }

    // ── SecretMaterialCache tests ────────────────────────────────────────────

    @Test
    public void cacheRespectsPolicy() {
        SecretCachePolicy policy = SecretCachePolicy.defaultPolicy();
        SecretMaterialCache cache = new SecretMaterialCache(policy);
        assertEquals(policy, cache.policy());
    }

    @Test
    public void cacheDisabledDoesNotStore() {
        SecretMaterialCache cache = new SecretMaterialCache(SecretCachePolicy.disabled());
        SecretCacheKey key = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", "purpose",
                AuthenticationMethod.FTP_PASSWORD);
        cache.put(key, new DefaultSecretMaterial("ref"));
        assertNull(cache.get(key));
        assertEquals(0, cache.size());
    }

    @Test
    public void cacheClearsOnClose() {
        SecretMaterialCache cache = new SecretMaterialCache(SecretCachePolicy.defaultPolicy());
        SecretCacheKey key = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", "purpose",
                AuthenticationMethod.FTP_PASSWORD);
        cache.put(key, new DefaultSecretMaterial("ref"));
        assertEquals(1, cache.size());
        cache.close();
        assertEquals(0, cache.size());
    }

    // ── SecretCacheKey tests ─────────────────────────────────────────────────

    @Test
    public void cacheKeyEquality() {
        SecretCacheKey a = new SecretCacheKey(
                new SecretRef("keepass://ftp"), "ftp:mainframe", "USER",
                "nightly", AuthenticationMethod.FTP_PASSWORD);
        SecretCacheKey b = new SecretCacheKey(
                new SecretRef("keepass://ftp"), "ftp:mainframe", "USER",
                "nightly", AuthenticationMethod.FTP_PASSWORD);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void cacheKeyDiffersByMethod() {
        SecretCacheKey a = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", null,
                AuthenticationMethod.FTP_PASSWORD);
        SecretCacheKey b = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", null,
                AuthenticationMethod.HTTP_BASIC);
        assertNotEquals(a, b);
    }

    @Test
    public void cacheKeyDiffersByPurpose() {
        SecretCacheKey a = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", "read",
                AuthenticationMethod.FTP_PASSWORD);
        SecretCacheKey b = new SecretCacheKey(
                new SecretRef("ref"), "sys", "user", "write",
                AuthenticationMethod.FTP_PASSWORD);
        assertNotEquals(a, b);
    }

    @Test
    public void cacheKeyFromAccessRequest() {
        AccessRequest req = new AccessRequest(
                new SecretRef("keepass://wiki"), "wiki:internal",
                "svc-user", "sync", "read",
                AuthenticationMethod.MEDIA_WIKI_LOGIN, 60000L);
        SecretCacheKey key = SecretCacheKey.from(req);
        assertEquals(new SecretRef("keepass://wiki"), key.credentialRef());
        assertEquals("wiki:internal", key.targetSystem());
        assertEquals("svc-user", key.principal());
        assertEquals("sync", key.purpose());
        assertEquals(AuthenticationMethod.MEDIA_WIKI_LOGIN, key.method());
    }

    @Test
    public void cacheRevokeAllByTarget() {
        SecretMaterialCache cache = new SecretMaterialCache(SecretCachePolicy.defaultPolicy());
        SecretCacheKey ftpKey = new SecretCacheKey(
                new SecretRef("keepass://ftp"), "ftp:mainframe", "USER",
                "job", AuthenticationMethod.FTP_PASSWORD);
        SecretCacheKey wikiKey = new SecretCacheKey(
                new SecretRef("keepass://wiki"), "wiki:internal", "svc",
                "sync", AuthenticationMethod.MEDIA_WIKI_LOGIN);
        cache.put(ftpKey, new DefaultSecretMaterial("ftp-ref"));
        cache.put(wikiKey, new DefaultSecretMaterial("wiki-ref"));
        assertEquals(2, cache.size());

        cache.revokeAll("ftp:mainframe");
        assertEquals(1, cache.size());
        assertNull(cache.get(ftpKey));
        assertNotNull(cache.get(wikiKey));
    }

    // ── AuthenticationMethod tests ───────────────────────────────────────────

    @Test
    public void authenticationMethodCoversResearchFlows() {
        // All methods discovered in the MainframeMate research analysis
        assertNotNull(AuthenticationMethod.FTP_PASSWORD);
        assertNotNull(AuthenticationMethod.NDV_PASSWORD);
        assertNotNull(AuthenticationMethod.MEDIA_WIKI_LOGIN);
        assertNotNull(AuthenticationMethod.HTTP_BASIC);
        assertNotNull(AuthenticationMethod.MTLS_CERTIFICATE);
        assertNotNull(AuthenticationMethod.SMB_NET_USE);
        assertNotNull(AuthenticationMethod.SSO);
    }

    @Test
    public void authenticationMethodEquality() {
        assertEquals(AuthenticationMethod.FTP_PASSWORD, AuthenticationMethod.of("ftp-password"));
        assertEquals(AuthenticationMethod.FTP_PASSWORD.hashCode(),
                AuthenticationMethod.of("ftp-password").hashCode());
        assertNotEquals(AuthenticationMethod.FTP_PASSWORD, AuthenticationMethod.NDV_PASSWORD);
    }

    @Test
    public void authenticationMethodIsExtensible() {
        // Adapter modules can introduce new methods without editing adyton core
        AuthenticationMethod custom = AuthenticationMethod.of("custom-oauth2");
        assertNotNull(custom);
        assertEquals("custom-oauth2", custom.name());
        assertEquals(AuthenticationMethod.of("custom-oauth2"), custom);
    }

    @Test(expected = IllegalArgumentException.class)
    public void authenticationMethodRejectsNull() {
        AuthenticationMethod.of(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void authenticationMethodRejectsEmpty() {
        AuthenticationMethod.of("");
    }

    // ── Exception hierarchy tests ────────────────────────────────────────────

    @Test
    public void exceptionHierarchy() {
        AccessException access = new AccessException("base");
        SecretUnavailableException unavail = new SecretUnavailableException("no secret");
        AuthCancelledException cancelled = new AuthCancelledException();

        assertTrue(unavail instanceof AccessException);
        assertTrue(cancelled instanceof SecretUnavailableException);
        assertTrue(cancelled instanceof AccessException);
    }

    // ── Test handle implementation ───────────────────────────────────────────

    private static class TestHandle implements AccessHandle {
        private final AccessGrant grant;
        private boolean closed = false;

        TestHandle(AccessGrant grant) {
            this.grant = grant;
        }

        @Override
        public AccessGrant grant() {
            return grant;
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
