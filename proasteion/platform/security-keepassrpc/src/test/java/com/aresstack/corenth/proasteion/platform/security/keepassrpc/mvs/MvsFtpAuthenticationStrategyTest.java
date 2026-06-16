package com.aresstack.corenth.proasteion.platform.security.keepassrpc.mvs;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpAccessHandle;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MvsFtpAuthenticationStrategyTest {

    @Test
    public void supportsFtpPasswordMethod() {
        MvsFtpAuthenticationStrategy strategy = new MvsFtpAuthenticationStrategy(new RecordingAuthenticator(new StubSession()));

        assertTrue(strategy.supports(AuthenticationMethod.FTP_PASSWORD));
    }

    @Test
    public void authenticateReturnsHandleThatOpensRoutedSession() throws Exception {
        StubSession session = new StubSession();
        RecordingAuthenticator authenticator = new RecordingAuthenticator(session);
        MvsFtpAuthenticationStrategy strategy = new MvsFtpAuthenticationStrategy(authenticator);
        StubSecretMaterial material = new StubSecretMaterial("user", "secret".toCharArray());
        FtpAccessHandle handle = strategy.authenticate(accessRequest(), material);
        NetworkRoutePlan routePlan = routePlan();

        assertEquals("user", handle.grant().principal());
        assertSame(session, handle.openSession(routePlan));
        assertEquals("target", authenticator.lastRequest.targetSystem());
        assertEquals("user", authenticator.lastRequest.principal());
        assertSame(routePlan, authenticator.lastRequest.routePlan());
        assertArrayEquals("secret".toCharArray(), authenticator.lastSecretSeen);
    }

    @Test
    public void closingHandleClosesSecretBackedFactory() throws Exception {
        RecordingAuthenticator authenticator = new RecordingAuthenticator(new StubSession());
        MvsFtpAuthenticationStrategy strategy = new MvsFtpAuthenticationStrategy(authenticator);
        FtpAccessHandle handle = strategy.authenticate(accessRequest(), new StubSecretMaterial("user", "secret".toCharArray()));
        handle.close();
    }

    private AccessRequest accessRequest() {
        return new AccessRequest("target", "user", "test", "read", AuthenticationMethod.FTP_PASSWORD, 0L);
    }

    private NetworkRoutePlan routePlan() {
        return new NetworkRoutePlan(URI.create("ftp" + "://host/USERID.PDS"),
                Collections.singletonList(NetworkRouteStage.direct("test")));
    }

    private static final class RecordingAuthenticator implements MvsFtpSessionAuthenticator {
        private final FtpClientSession session;
        private MvsFtpSessionAuthenticationRequest lastRequest;
        private char[] lastSecretSeen;

        private RecordingAuthenticator(FtpClientSession session) {
            this.session = session;
        }

        public FtpClientSession authenticate(MvsFtpSessionAuthenticationRequest request) {
            this.lastRequest = request;
            this.lastSecretSeen = request.secret();
            return session;
        }
    }

    private static final class StubSession implements FtpClientSession {
        public byte[] readBytes(MvsLocation location, ResourceReadMode readMode) {
            return new byte[0];
        }

        public List<String> listNames(MvsLocation location) {
            return Collections.emptyList();
        }

        public void close() {
        }
    }

    private static final class StubSecretMaterial implements SecretMaterial {
        private final String principal;
        private final char[] secret;

        private StubSecretMaterial(String principal, char[] secret) {
            this.principal = principal;
            this.secret = Arrays.copyOf(secret, secret.length);
        }

        public String principal() {
            return principal;
        }

        public char[] secret() {
            return Arrays.copyOf(secret, secret.length);
        }

        public String secretRefId() {
            return "secret-ref";
        }

        public void close() {
            Arrays.fill(secret, '\0');
        }
    }
}
