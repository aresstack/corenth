package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.adyton.*;
import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.holkas.RawResource;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListing;
import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import com.aresstack.corenth.proasteion.platform.network.NetworkAccessPolicy;
import com.aresstack.corenth.proasteion.platform.network.NetworkAccessRequest;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlanner;
import com.aresstack.corenth.proasteion.platform.network.NetworkRouteStage;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class FtpMvsResourceConnectorTest {

    @Test
    public void fetchUsesAccessBrokerAndSession() throws Exception {
        RecordingSession session = new RecordingSession();
        FixedAccessBroker broker = new FixedAccessBroker(handle(session));
        FtpMvsResourceConnector connector = connector(broker);

        RawResource resource = connector.fetch(ref("USERID.PDS(MEMBER)", VirtualResourceKind.FILE));

        assertEquals(1, broker.calls);
        assertArrayEquals("member content".getBytes("UTF-8"), resource.content().bytes());
        assertEquals("MEMBER", session.lastReadLocation.displayName());
        assertEquals(ResourceReadMode.DEFAULT, session.lastReadMode);
    }

    @Test
    public void routePlannerIsCalledBeforeSessionOperation() throws Exception {
        RecordingSession session = new RecordingSession();
        RecordingSessionFactory factory = new RecordingSessionFactory(session);
        RecordingRoutePlanner planner = new RecordingRoutePlanner();
        FtpMvsResourceConnector connector = new FtpMvsResourceConnector(
                new FixedAccessBroker(handle(factory)), accessRequest(), new NoopFtpStrategy(),
                planner, NetworkAccessPolicy.direct());

        connector.fetch(ref("USERID.PDS(MEMBER)", VirtualResourceKind.FILE));

        assertEquals(1, planner.calls);
        assertEquals("mvs-fetch", planner.lastRequest.operation());
        assertSame(planner.plan, factory.lastRequest.routePlan());
    }

    @Test
    public void listUsesAccessBrokerAndMapsMembers() throws Exception {
        RecordingSession session = new RecordingSession();
        FtpMvsResourceConnector connector = connector(session);

        ResourceListing listing = connector.list(ref("USERID.PDS", VirtualResourceKind.DIRECTORY));

        assertEquals(2, listing.entries().size());
        assertEquals("MEMBER1", listing.entries().get(0).name());
        assertEquals(VirtualResourceKind.FILE, listing.entries().get(0).kind());
    }

    private FtpMvsResourceConnector connector(RecordingSession session) {
        return new FtpMvsResourceConnector(new FixedAccessBroker(handle(session)), accessRequest(), new NoopFtpStrategy());
    }

    private FtpMvsResourceConnector connector(FixedAccessBroker broker) {
        return new FtpMvsResourceConnector(broker, accessRequest(), new NoopFtpStrategy());
    }

    private FtpAccessHandle handle(RecordingSession session) {
        AccessGrant grant = new AccessGrant("grant-1", "target", "user", "test", "read", Long.MAX_VALUE);
        return new FtpAccessHandle(grant, session);
    }

    private FtpAccessHandle handle(FtpClientSessionFactory factory) {
        AccessGrant grant = new AccessGrant("grant-1", "target", "user", "test", "read", Long.MAX_VALUE);
        return new FtpAccessHandle(grant, factory);
    }

    private AccessRequest accessRequest() {
        return new AccessRequest("target", "user", "test", "read", AuthenticationMethod.FTP_PASSWORD, 0L);
    }

    private VirtualResourceRef ref(String path, VirtualResourceKind kind) {
        return new VirtualResourceRef(BookmarkUri.parse("ftp" + "://host/" + path), kind);
    }

    private static final class RecordingSession implements FtpClientSession {
        private MvsLocation lastReadLocation;
        private ResourceReadMode lastReadMode;

        public byte[] readBytes(MvsLocation location, ResourceReadMode readMode) throws IOException {
            this.lastReadLocation = location;
            this.lastReadMode = readMode;
            return "member content".getBytes("UTF-8");
        }

        public List<String> listNames(MvsLocation location) {
            return Arrays.asList("MEMBER1", "MEMBER2");
        }

        public void close() {
        }
    }

    private static final class RecordingSessionFactory implements FtpClientSessionFactory {
        private final FtpClientSession session;
        private FtpSessionOpenRequest lastRequest;

        private RecordingSessionFactory(FtpClientSession session) {
            this.session = session;
        }

        public FtpClientSession open(FtpSessionOpenRequest request) {
            this.lastRequest = request;
            return session;
        }

        public void close() {
        }
    }

    private static final class RecordingRoutePlanner implements NetworkRoutePlanner {
        private final NetworkRoutePlan plan = new NetworkRoutePlan(
                java.net.URI.create("ftp" + "://host/USERID.PDS(MEMBER)"),
                Collections.singletonList(NetworkRouteStage.direct("test-direct")));
        private int calls;
        private NetworkAccessRequest lastRequest;

        public NetworkRoutePlan plan(NetworkAccessRequest request) {
            calls++;
            lastRequest = request;
            return plan;
        }
    }

    private static final class FixedAccessBroker implements AccessBroker {
        private final FtpAccessHandle handle;
        private int calls;

        private FixedAccessBroker(FtpAccessHandle handle) {
            this.handle = handle;
        }

        public <H extends AccessHandle, R> R withAccess(AccessRequest request,
                                                        AuthenticationStrategy<H> strategy,
                                                        AccessOperation<H, R> operation)
                throws AccessException, AuthCancelledException {
            calls++;
            try {
                return operation.execute((H) handle);
            } catch (AccessException e) {
                throw e;
            } catch (Exception e) {
                throw new AccessException("operation failed", e);
            }
        }

        public <H extends AccessHandle> H acquire(AccessRequest request,
                                                  AuthenticationStrategy<H> strategy)
                throws AccessException, AuthCancelledException {
            return (H) handle;
        }

        public void revoke(AccessGrant grant) {
        }
    }

    private static final class NoopFtpStrategy implements AuthenticationStrategy<FtpAccessHandle> {
        public boolean supports(AuthenticationMethod method) {
            return AuthenticationMethod.FTP_PASSWORD.equals(method);
        }

        public FtpAccessHandle authenticate(AccessRequest request, SecretMaterial material) {
            throw new UnsupportedOperationException("not used");
        }
    }
}

