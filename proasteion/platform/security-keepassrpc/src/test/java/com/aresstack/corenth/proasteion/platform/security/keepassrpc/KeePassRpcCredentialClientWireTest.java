package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.SecretRef;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import com.aresstack.keepassrpc.client.DefaultKeePassRpcCredentialClient;
import com.aresstack.keepassrpc.client.KeePassNotAvailableException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Drives the real {@link DefaultKeePassRpcCredentialClient} from the bundled library over a local
 * WebSocket. Covered: unreachable endpoint and the first KeePassRPC setup exchange ending in a
 * server-side error. Not covered: a successful SRP handshake, encrypted FindLogins calls and a
 * real KeePass vault.
 */
public class KeePassRpcCredentialClientWireTest {

    private static final SecretRef WIKI_REF = new SecretRef("keepass://corenth/wiki");
    private static final String CLIENT_ID = "corenth-wire-test";
    private static final String PAIRING_KEY = "pairing-key-that-must-not-be-sent";
    private static final String ORIGIN = "corenth-test://wire";

    private RejectingKeePassRpcServer server;

    @After
    public void stopServer() throws InterruptedException {
        if (server != null) {
            server.stop(1000);
        }
    }

    @Test
    public void unreachableEndpointIsReportedAsUnavailableSecret() throws Exception {
        int closedPort = freePort();
        KeePassRpcCredentialClientLookup lookup = lookupFor(closedPort);

        try {
            lookup.findSecret(request());
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertTrue(expected.getCause() instanceof KeePassNotAvailableException);
        } finally {
            lookup.close();
        }
    }

    @Test
    public void setupErrorFromServerIsReportedAsUnavailableSecret() throws Exception {
        int port = freePort();
        server = new RejectingKeePassRpcServer(port);
        server.start();
        assertTrue("server did not start", server.started.await(10, TimeUnit.SECONDS));
        KeePassRpcCredentialClientLookup lookup = lookupFor(port);

        try {
            lookup.findSecret(request());
            fail("Expected SecretUnavailableException");
        } catch (SecretUnavailableException expected) {
            assertTrue(expected.getCause() instanceof KeePassNotAvailableException);
        } finally {
            lookup.close();
        }

        assertEquals(ORIGIN, server.origin);
        assertEquals(1, server.messages.size());
        String identify = server.messages.get(0);
        assertTrue(identify, identify.contains("\"protocol\":\"setup\""));
        assertTrue(identify, identify.contains("\"username\":\"" + CLIENT_ID + "\""));
        assertFalse("the pairing key must not travel in clear text", identify.contains(PAIRING_KEY));
    }

    private static KeePassRpcCredentialClientLookup lookupFor(int port) {
        DefaultKeePassRpcCredentialClient client =
                new DefaultKeePassRpcCredentialClient("127.0.0.1", port, CLIENT_ID, PAIRING_KEY, ORIGIN);
        return new KeePassRpcCredentialClientLookup(client, Collections.singletonMap(WIKI_REF, "Corenth Wiki"));
    }

    private static AccessRequest request() {
        return new AccessRequest(WIKI_REF, "https://wiki.example.invalid", "request-user",
                "read wiki pages", "read", AuthenticationMethod.MEDIA_WIKI_LOGIN, 0L);
    }

    private static int freePort() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        try {
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    /** Answer every client message with a KeePassRPC setup error. */
    private static final class RejectingKeePassRpcServer extends WebSocketServer {
        private final CountDownLatch started = new CountDownLatch(1);
        private final List<String> messages = new CopyOnWriteArrayList<String>();
        private volatile String origin;

        private RejectingKeePassRpcServer(int port) {
            super(new InetSocketAddress("127.0.0.1", port));
            setReuseAddr(true);
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) {
            origin = handshake.getFieldValue("Origin");
        }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        }

        @Override
        public void onMessage(WebSocket connection, String message) {
            messages.add(message);
            connection.send("{\"protocol\":\"setup\",\"error\":{\"code\":\"AUTH_RESTART\"}}");
        }

        @Override
        public void onError(WebSocket connection, Exception failure) {
        }

        @Override
        public void onStart() {
            started.countDown();
        }
    }
}
