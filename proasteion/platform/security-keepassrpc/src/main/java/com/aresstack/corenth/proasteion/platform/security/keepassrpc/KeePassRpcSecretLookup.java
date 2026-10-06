package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretUnavailableException;

/**
 * Narrow lookup port intended to wrap a concrete KeePassRPC Java client.
 * <p>
 * No implementation currently binds to the bundled
 * {@code com.aresstack.keepassrpc.client.KeePassRpcCredentialClient}
 * ({@code getUserName(String)}/{@code getPassword(String)});
 * {@link ReflectiveKeePassRpcSecretLookup} only matches objects exposing
 * {@code findByRef/find/get/resolve(String)} or {@code findLogin(String,String)}.
 * A typed lookup against the real client is still required for production use.
 */
public interface KeePassRpcSecretLookup {

    KeePassRpcSecret findSecret(AccessRequest request) throws SecretUnavailableException;
}
