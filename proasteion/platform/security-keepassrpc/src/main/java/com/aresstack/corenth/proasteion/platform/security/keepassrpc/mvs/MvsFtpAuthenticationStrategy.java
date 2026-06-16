package com.aresstack.corenth.proasteion.platform.security.keepassrpc.mvs;

import com.aresstack.corenth.adyton.AccessException;
import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.AuthenticationStrategy;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpAccessHandle;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSessionFactory;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpSessionOpenRequest;

import java.io.IOException;
import java.util.Arrays;

/**
 * Trusted authentication strategy for MVS sessions.
 */
public final class MvsFtpAuthenticationStrategy implements AuthenticationStrategy<FtpAccessHandle> {

    private static final long DEFAULT_TTL_MILLIS = 300000L;

    private final MvsFtpSessionAuthenticator authenticator;

    public MvsFtpAuthenticationStrategy(MvsFtpSessionAuthenticator authenticator) {
        if (authenticator == null) {
            throw new IllegalArgumentException("authenticator must not be null");
        }
        this.authenticator = authenticator;
    }

    @Override
    public boolean supports(AuthenticationMethod method) {
        return AuthenticationMethod.FTP_PASSWORD.equals(method);
    }

    @Override
    public FtpAccessHandle authenticate(AccessRequest request, SecretMaterial material) throws AccessException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (material == null) {
            throw new IllegalArgumentException("material must not be null");
        }
        if (!supports(request.method())) {
            throw new AccessException("Unsupported authentication method: " + request.method());
        }
        AccessGrant grant = new AccessGrant(
                "mvs-" + System.nanoTime(),
                request.targetSystem(),
                material.principal(),
                request.purpose(),
                request.scope(),
                expiresAt(request));
        return new FtpAccessHandle(grant,
                new SecretBackedSessionFactory(request.targetSystem(), material.principal(), material.secret(), authenticator));
    }

    private long expiresAt(AccessRequest request) {
        long ttl = request.requestedTtlMillis();
        if (ttl <= 0L) {
            ttl = DEFAULT_TTL_MILLIS;
        }
        return System.currentTimeMillis() + ttl;
    }

    private static final class SecretBackedSessionFactory implements FtpClientSessionFactory {
        private final String targetSystem;
        private final String principal;
        private final char[] secret;
        private final MvsFtpSessionAuthenticator authenticator;
        private boolean closed;

        private SecretBackedSessionFactory(String targetSystem, String principal, char[] secret,
                                           MvsFtpSessionAuthenticator authenticator) {
            this.targetSystem = targetSystem;
            this.principal = principal;
            this.secret = Arrays.copyOf(secret, secret.length);
            this.authenticator = authenticator;
        }

        @Override
        public FtpClientSession open(FtpSessionOpenRequest request) throws IOException {
            if (closed) {
                throw new IOException("session factory is closed");
            }
            MvsFtpSessionAuthenticationRequest authenticationRequest =
                    new MvsFtpSessionAuthenticationRequest(targetSystem, principal, secret, request.routePlan());
            try {
                return authenticator.authenticate(authenticationRequest);
            } finally {
                authenticationRequest.close();
            }
        }

        @Override
        public void close() {
            if (!closed) {
                Arrays.fill(secret, '\0');
                closed = true;
            }
        }
    }
}
