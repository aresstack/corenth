package com.aresstack.corenth.adyton;

import org.junit.Test;

import static org.junit.Assert.*;

public class ProviderBackedAccessBrokerTest {

    @Test
    public void brokerResolvesMaterialThroughProviderAndClosesHandleAfterOperation() throws Exception {
        CountingMaterialProvider provider = new CountingMaterialProvider();
        ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(provider, SecretCachePolicy.disabled());
        TestAuthenticationStrategy strategy = new TestAuthenticationStrategy();
        AccessRequest request = request();

        String result = broker.withAccess(request, strategy, new AccessOperation<TestAccessHandle, String>() {
            @Override
            public String execute(TestAccessHandle handle) {
                assertFalse(handle.isClosed());
                return handle.materialPrincipal();
            }
        });

        assertEquals("user", result);
        assertEquals(1, provider.resolveCount());
        assertEquals(1, provider.releaseCount());
        assertTrue(strategy.lastHandle().isClosed());
        assertEquals(0, broker.activeHandleCount());
    }

    @Test
    public void brokerReusesCachedMaterialWhenPolicyAllowsIt() throws Exception {
        CountingMaterialProvider provider = new CountingMaterialProvider();
        ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(
                provider, new SecretCachePolicy(true, 60000L, 0L));
        TestAuthenticationStrategy strategy = new TestAuthenticationStrategy();

        broker.withAccess(request(), strategy, noopOperation());
        broker.withAccess(request(), strategy, noopOperation());

        assertEquals(1, provider.resolveCount());
        assertEquals(0, provider.releaseCount());
        broker.close();
    }

    @Test
    public void brokerRejectsUnsupportedAuthenticationStrategy() throws Exception {
        ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(
                new CountingMaterialProvider(), SecretCachePolicy.disabled());
        try {
            broker.acquire(request(), new UnsupportedAuthenticationStrategy());
            fail("Expected AccessException");
        } catch (AccessException expected) {
            assertTrue(expected.getMessage().contains("does not support"));
        }
    }

    @Test
    public void brokerRevokesActiveHandleAndTargetCache() throws Exception {
        CountingMaterialProvider provider = new CountingMaterialProvider();
        ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(
                provider, new SecretCachePolicy(true, 60000L, 0L));
        TestAccessHandle handle = broker.acquire(request(), new TestAuthenticationStrategy());

        broker.revoke(handle.grant());

        assertTrue(handle.isClosed());
        assertEquals(0, broker.activeHandleCount());
    }

    private static AccessRequest request() {
        return new AccessRequest(
                new SecretRef("keepass://corenth/test"),
                "https://example.invalid",
                "user",
                "test",
                "read",
                AuthenticationMethod.HTTP_BASIC,
                60000L);
    }

    private static AccessOperation<TestAccessHandle, String> noopOperation() {
        return new AccessOperation<TestAccessHandle, String>() {
            @Override
            public String execute(TestAccessHandle handle) {
                return "ok";
            }
        };
    }

    private static final class CountingMaterialProvider implements SecretMaterialProvider {
        private int resolveCount;
        private int releaseCount;

        @Override
        public SecretMaterial resolve(AccessRequest request) {
            resolveCount++;
            return SecretMaterialFactory.fromSecret(
                    request.credentialRef(), request.principal(), "secret".toCharArray());
        }

        @Override
        public void release(SecretMaterial material) {
            releaseCount++;
            material.close();
        }

        int resolveCount() {
            return resolveCount;
        }

        int releaseCount() {
            return releaseCount;
        }
    }

    private static final class TestAuthenticationStrategy implements AuthenticationStrategy<TestAccessHandle> {
        private TestAccessHandle lastHandle;

        @Override
        public boolean supports(AuthenticationMethod method) {
            return AuthenticationMethod.HTTP_BASIC.equals(method);
        }

        @Override
        public TestAccessHandle authenticate(AccessRequest request, SecretMaterial material) {
            lastHandle = new TestAccessHandle(new AccessGrant(
                    "grant-" + System.nanoTime(),
                    request.targetSystem(),
                    request.principal(),
                    request.purpose(),
                    request.scope(),
                    System.currentTimeMillis() + 60000L), material.principal());
            return lastHandle;
        }

        TestAccessHandle lastHandle() {
            return lastHandle;
        }
    }

    private static final class UnsupportedAuthenticationStrategy implements AuthenticationStrategy<TestAccessHandle> {
        @Override
        public boolean supports(AuthenticationMethod method) {
            return false;
        }

        @Override
        public TestAccessHandle authenticate(AccessRequest request, SecretMaterial material) {
            throw new AssertionError("Must not authenticate with unsupported strategy");
        }
    }

    private static final class TestAccessHandle implements AccessHandle {
        private final AccessGrant grant;
        private final String materialPrincipal;
        private boolean closed;

        TestAccessHandle(AccessGrant grant, String materialPrincipal) {
            this.grant = grant;
            this.materialPrincipal = materialPrincipal;
        }

        @Override
        public AccessGrant grant() {
            return grant;
        }

        String materialPrincipal() {
            return materialPrincipal;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
