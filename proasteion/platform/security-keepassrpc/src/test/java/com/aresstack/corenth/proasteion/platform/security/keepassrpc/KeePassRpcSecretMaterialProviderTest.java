package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretRef;
import org.junit.Test;

import static org.junit.Assert.*;

public class KeePassRpcSecretMaterialProviderTest {

    @Test
    public void resolvesKeePassSecretIntoAdytonMaterial() throws Exception {
        KeePassRpcSecretMaterialProvider provider = new KeePassRpcSecretMaterialProvider(
                new KeePassRpcSecretLookup() {
                    @Override
                    public KeePassRpcSecret findSecret(AccessRequest request) {
                        return new KeePassRpcSecret("resolved-user", "resolved-password".toCharArray());
                    }
                });

        SecretMaterial material = provider.resolve(request());

        assertEquals("resolved-user", material.principal());
        assertArrayEquals("resolved-password".toCharArray(), material.secret());
        assertEquals("keepass://corenth/wiki", material.secretRefId());
        provider.release(material);
        assertEquals(0, material.secret().length);
    }

    @Test
    public void reflectiveLookupAdaptsMapResult() throws Exception {
        ReflectiveKeePassRpcSecretLookup lookup = new ReflectiveKeePassRpcSecretLookup(new MapBackedKeePassClient());

        KeePassRpcSecret secret = lookup.findSecret(request());

        assertEquals("map-user", secret.principal());
        assertArrayEquals("map-secret".toCharArray(), secret.secret());
    }

    public static final class MapBackedKeePassClient {
        public java.util.Map<String, String> find(String refId) {
            java.util.Map<String, String> result = new java.util.HashMap<String, String>();
            result.put("username", "map-user");
            result.put("password", "map-secret");
            return result;
        }
    }

    private static AccessRequest request() {
        return new AccessRequest(
                new SecretRef("keepass://corenth/wiki"),
                "https://wiki.local",
                "fallback-user",
                "test",
                "read",
                AuthenticationMethod.MEDIA_WIKI_LOGIN,
                60000L);
    }
}
