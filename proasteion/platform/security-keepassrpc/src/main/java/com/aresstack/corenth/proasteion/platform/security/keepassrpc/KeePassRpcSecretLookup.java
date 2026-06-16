package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretUnavailableException;

/**
 * Narrow lookup port around the concrete KeePassRPC Java client.
 */
public interface KeePassRpcSecretLookup {

    KeePassRpcSecret findSecret(AccessRequest request) throws SecretUnavailableException;
}
