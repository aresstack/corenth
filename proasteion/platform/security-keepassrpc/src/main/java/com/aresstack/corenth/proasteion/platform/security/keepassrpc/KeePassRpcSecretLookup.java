package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretUnavailableException;

/**
 * Narrow lookup port that wraps a concrete KeePassRPC Java client.
 * <p>
 * The production implementation is {@link KeePassRpcCredentialClientLookup}, which binds to
 * the bundled {@code com.aresstack.keepassrpc.client.KeePassRpcCredentialClient}
 * ({@code getUserName(String)}/{@code getPassword(String)}).
 */
public interface KeePassRpcSecretLookup {

    KeePassRpcSecret findSecret(AccessRequest request) throws SecretUnavailableException;
}
