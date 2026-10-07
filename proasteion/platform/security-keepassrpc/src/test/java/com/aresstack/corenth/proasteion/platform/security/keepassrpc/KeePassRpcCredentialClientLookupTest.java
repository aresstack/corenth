package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretRef;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import com.aresstack.keepassrpc.client.KeePassNotAvailableException;
import com.aresstack.keepassrpc.client.KeePassRpcCredentialClient;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Binds the lookup to the real {@link KeePassRpcCredentialClient} interface of the bundled
 * library. The client double implements that interface; it is API-binding coverage, not a
 * KeePassRPC wire or live-vault test.
 */
public class KeePassRpcCredentialClientLookupTest {

    private static final SecretRef WIKI_REF = new SecretRef("keepass://corenth/wiki");

    @Test
    public void resolvesUserNameAndPasswordThroughMappedEntryTitle() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        KeePassRpcSecret secret = lookup.findSecret(request(WIKI_REF));

        assertEquals("vault-user", secret.principal());
        assertArrayEquals("vault-password".toCharArray(), secret.secret());
        assertEquals(1, client.connectCount);
        assertEquals(Collections.singletonList("Corenth Wiki"), client.passwordTitles);
        assertEquals(Collections.singletonList("Corenth Wiki"), client.userNameTitles);
    }

    @Test
    public void connectsOnlyOnceForConsecutiveLookups() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        lookup.findSecret(request(WIKI_REF));
        lookup.findSecret(request(WIKI_REF));

        assertEquals(1, client.connectCount);
    }

    @Test
    public void fallsBackToRequestPrincipalWhenEntryHasNoUserName() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"", "vault-password"});
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        KeePassRpcSecret secret = lookup.findSecret(request(WIKI_REF));

        assertEquals("request-user", secret.principal());
    }

    @Test
    public void unmappedReferenceIsUnavailableWithoutContactingKeePass() {
        RecordingClient client = new RecordingClient();
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        try {
            lookup.findSecret(request(new SecretRef("keepass://corenth/other")));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertEquals(0, client.connectCount);
            assertTrue(client.passwordTitles.isEmpty());
        }
    }

    @Test
    public void missingEntryIsUnavailable() {
        RecordingClient client = new RecordingClient();
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        try {
            lookup.findSecret(request(WIKI_REF));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertEquals(Collections.singletonList("Corenth Wiki"), client.passwordTitles);
            assertTrue(client.userNameTitles.isEmpty());
        }
    }

    @Test
    public void unreachableKeePassIsUnavailableAndReconnectsOnNextLookup() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        client.failNextConnect = new KeePassNotAvailableException("not running");
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());

        try {
            lookup.findSecret(request(WIKI_REF));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertTrue(expected.getCause() instanceof KeePassNotAvailableException);
        }
        KeePassRpcSecret secret = lookup.findSecret(request(WIKI_REF));

        assertEquals("vault-user", secret.principal());
        assertEquals(2, client.connectCount);
        assertEquals(1, client.closeCount);
    }

    @Test
    public void failingRpcCallDisconnectsSoTheNextLookupReconnects() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());
        lookup.findSecret(request(WIKI_REF));
        client.failNextPassword = new KeePassNotAvailableException("connection lost");

        try {
            lookup.findSecret(request(WIKI_REF));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertEquals(1, client.closeCount);
        }
        lookup.findSecret(request(WIKI_REF));

        assertEquals(2, client.connectCount);
    }

    @Test
    public void closeClosesClientAndRejectsFurtherLookups() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, wikiMapping());
        lookup.findSecret(request(WIKI_REF));

        lookup.close();

        assertEquals(1, client.closeCount);
        try {
            lookup.findSecret(request(WIKI_REF));
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertEquals(1, client.connectCount);
        }
    }

    @Test
    public void providerTurnsLookupResultIntoAdytonMaterial() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        KeePassRpcSecretMaterialProvider provider = new KeePassRpcSecretMaterialProvider(
                new KeePassRpcCredentialClientLookup(client, wikiMapping()));

        SecretMaterial material = provider.resolve(request(WIKI_REF));

        assertEquals("vault-user", material.principal());
        assertArrayEquals("vault-password".toCharArray(), material.secret());
        assertEquals(WIKI_REF.id(), material.secretRefId());
        provider.release(material);
        assertEquals(0, material.secret().length);
    }

    @Test
    public void rejectsEmptyEntryTitleInMapping() {
        Map<SecretRef, String> mapping = new HashMap<SecretRef, String>();
        mapping.put(WIKI_REF, " ");
        try {
            new KeePassRpcCredentialClientLookup(new RecordingClient(), mapping);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("entry title"));
        }
    }

    @Test
    public void mappingIsCopiedDefensively() throws Exception {
        RecordingClient client = new RecordingClient();
        client.entries.put("Corenth Wiki", new String[]{"vault-user", "vault-password"});
        Map<SecretRef, String> mapping = wikiMapping();
        KeePassRpcCredentialClientLookup lookup = new KeePassRpcCredentialClientLookup(client, mapping);
        mapping.clear();

        KeePassRpcSecret secret = lookup.findSecret(request(WIKI_REF));

        assertEquals("vault-user", secret.principal());
    }

    private static Map<SecretRef, String> wikiMapping() {
        Map<SecretRef, String> mapping = new HashMap<SecretRef, String>();
        mapping.put(WIKI_REF, "Corenth Wiki");
        return mapping;
    }

    private static AccessRequest request(SecretRef ref) {
        return new AccessRequest(ref, "https://wiki.example.invalid", "request-user",
                "read wiki pages", "read", AuthenticationMethod.MEDIA_WIKI_LOGIN, 0L);
    }

    private static final class RecordingClient implements KeePassRpcCredentialClient {
        private final Map<String, String[]> entries = new HashMap<String, String[]>();
        private final List<String> passwordTitles = new ArrayList<String>();
        private final List<String> userNameTitles = new ArrayList<String>();
        private int connectCount;
        private int closeCount;
        private RuntimeException failNextConnect;
        private RuntimeException failNextPassword;

        @Override
        public void connect() {
            connectCount++;
            if (failNextConnect != null) {
                RuntimeException failure = failNextConnect;
                failNextConnect = null;
                throw failure;
            }
        }

        @Override
        public String getUserName(String entryTitle) {
            userNameTitles.add(entryTitle);
            String[] entry = entries.get(entryTitle);
            return entry == null ? null : entry[0];
        }

        @Override
        public String getPassword(String entryTitle) {
            passwordTitles.add(entryTitle);
            if (failNextPassword != null) {
                RuntimeException failure = failNextPassword;
                failNextPassword = null;
                throw failure;
            }
            String[] entry = entries.get(entryTitle);
            return entry == null ? null : entry[1];
        }

        @Override
        public String getDatabaseFileName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLogin(String title, String userName, String password, String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLogin(String title, String userName, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String listEntries() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
