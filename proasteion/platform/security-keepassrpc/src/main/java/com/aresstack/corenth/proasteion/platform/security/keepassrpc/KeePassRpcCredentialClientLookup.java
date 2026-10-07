package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretRef;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import com.aresstack.keepassrpc.client.KeePassRpcCredentialClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed {@link KeePassRpcSecretLookup} bound to the real {@code keepassrpc-java} client API.
 * <p>
 * The client addresses KeePass entries by entry title ({@code getUserName(String)} and
 * {@code getPassword(String)}). This lookup therefore needs an explicit mapping from the opaque
 * {@link SecretRef} of an {@link AccessRequest} to the KeePass entry title; it never derives a
 * title from the reference text. An unmapped reference, a missing entry and an unreachable
 * KeePassRPC endpoint all end in {@link SecretUnavailableException}.
 * <p>
 * The lookup owns the client: it connects lazily on the first lookup, reconnects after a failed
 * call and closes the client in {@link #close()}.
 * <p>
 * Limitation: the library returns the password as an immutable {@link String}. The lookup copies
 * it into a {@code char[]} immediately and drops the reference, but it cannot wipe the string.
 */
public final class KeePassRpcCredentialClientLookup implements KeePassRpcSecretLookup, AutoCloseable {

    private final KeePassRpcCredentialClient client;
    private final Map<SecretRef, String> entryTitlesByRef;
    private boolean connected;
    private boolean closed;

    /**
     * Creates a lookup that resolves the given secret references through the client.
     *
     * @param client           the KeePassRPC credential client; ownership passes to this lookup
     * @param entryTitlesByRef KeePass entry titles keyed by the secret reference they serve
     */
    public KeePassRpcCredentialClientLookup(KeePassRpcCredentialClient client,
                                            Map<SecretRef, String> entryTitlesByRef) {
        if (client == null) {
            throw new IllegalArgumentException("KeePassRPC credential client must not be null");
        }
        if (entryTitlesByRef == null) {
            throw new IllegalArgumentException("Entry title mapping must not be null");
        }
        Map<SecretRef, String> copy = new HashMap<SecretRef, String>();
        for (Map.Entry<SecretRef, String> mapping : entryTitlesByRef.entrySet()) {
            if (mapping.getKey() == null) {
                throw new IllegalArgumentException("Entry title mapping must not contain a null secret reference");
            }
            String title = mapping.getValue();
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Entry title mapping must not contain an empty entry title");
            }
            copy.put(mapping.getKey(), title.trim());
        }
        this.client = client;
        this.entryTitlesByRef = Collections.unmodifiableMap(copy);
    }

    @Override
    public synchronized KeePassRpcSecret findSecret(AccessRequest request) throws SecretUnavailableException {
        if (request == null) {
            throw new IllegalArgumentException("Access request must not be null");
        }
        if (closed) {
            throw new SecretUnavailableException("KeePassRPC lookup is closed");
        }
        String entryTitle = entryTitlesByRef.get(request.credentialRef());
        if (entryTitle == null) {
            throw new SecretUnavailableException("No KeePass entry is configured for the requested secret reference");
        }
        try {
            ensureConnected();
            char[] secret = passwordOf(entryTitle);
            try {
                return new KeePassRpcSecret(principalOf(entryTitle, request), secret);
            } finally {
                Arrays.fill(secret, '\0');
            }
        } catch (SecretUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            disconnectQuietly();
            throw new SecretUnavailableException("KeePassRPC lookup failed", e);
        }
    }

    /** Closes the underlying client. Further lookups fail with {@link SecretUnavailableException}. */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            disconnectQuietly();
        }
    }

    private void ensureConnected() {
        if (!connected) {
            client.connect();
            connected = true;
        }
    }

    private char[] passwordOf(String entryTitle) throws SecretUnavailableException {
        String password = client.getPassword(entryTitle);
        if (password == null) {
            throw new SecretUnavailableException("KeePass entry does not provide a password");
        }
        return password.toCharArray();
    }

    private String principalOf(String entryTitle, AccessRequest request) {
        String userName = client.getUserName(entryTitle);
        if (userName == null || userName.isEmpty()) {
            return request.principal();
        }
        return userName;
    }

    private void disconnectQuietly() {
        connected = false;
        try {
            client.close();
        } catch (RuntimeException ignored) {
            // Keep the lookup failure or shutdown as the observable outcome.
        }
    }
}
