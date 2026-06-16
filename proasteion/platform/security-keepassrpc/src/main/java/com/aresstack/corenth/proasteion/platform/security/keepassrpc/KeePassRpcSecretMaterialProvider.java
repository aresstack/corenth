package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretMaterialFactory;
import com.aresstack.corenth.adyton.SecretMaterialProvider;
import com.aresstack.corenth.adyton.SecretUnavailableException;

/**
 * Adyton secret material provider backed by KeePassRPC.
 * <p>
 * The provider is deliberately small: KeePass-specific lookup details stay in
 * {@link KeePassRpcSecretLookup}; adyton receives only a short-lived
 * {@link SecretMaterial} instance created through {@link SecretMaterialFactory}.
 */
public final class KeePassRpcSecretMaterialProvider implements SecretMaterialProvider {

    private final KeePassRpcSecretLookup secretLookup;

    public KeePassRpcSecretMaterialProvider(KeePassRpcSecretLookup secretLookup) {
        if (secretLookup == null) {
            throw new IllegalArgumentException("KeePassRPC secret lookup must not be null");
        }
        this.secretLookup = secretLookup;
    }

    @Override
    public SecretMaterial resolve(AccessRequest request) throws SecretUnavailableException {
        if (request == null) {
            throw new IllegalArgumentException("Access request must not be null");
        }
        KeePassRpcSecret secret = secretLookup.findSecret(request);
        if (secret == null) {
            throw new SecretUnavailableException("KeePassRPC secret is unavailable");
        }
        try {
            return SecretMaterialFactory.fromSecret(request.credentialRef(), secret.principal(), secret.secret());
        } finally {
            secret.close();
        }
    }

    @Override
    public void release(SecretMaterial material) {
        if (material != null) {
            material.close();
        }
    }
}
