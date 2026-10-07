package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.adyton.AccessException;
import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.adyton.AuthenticationMethod;
import com.aresstack.corenth.adyton.AuthenticationStrategy;
import com.aresstack.corenth.adyton.ProviderBackedAccessBroker;
import com.aresstack.corenth.adyton.SecretCachePolicy;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretMaterialFactory;
import com.aresstack.corenth.adyton.SecretMaterialProvider;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessDecisionType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessDecision;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpAccessHandle;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession;
import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpMvsResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * #10 Slice 3 end to end below the lifecycle: Tamias requires authentication for FTP, the
 * Adyton-backed station prepares a handle through the real {@link ProviderBackedAccessBroker},
 * the counter hands it as an opaque capability to the Holkas bridge, and the FTP connector reads
 * with it. Local files never reach the broker. No real vault or FTP server is involved: the
 * secret provider and the FTP session are test doubles.
 */
public class AuthenticatedAcquisitionSliceTest {

    private static final ActorIdentity ACTOR = new ActorIdentity("lifecycle", ActorType.SERVICE);
    private static final BookmarkUri FTP_MEMBER = BookmarkUri.parse("ftp" + "://host/USERID.PDS(MEMBER)");
    private static final BookmarkUri FTP_PDS = BookmarkUri.parse("ftp" + "://host/USERID.PDS");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void authenticatedFetch_usesBrokerHandle_andClosesItAfterAcquisition() throws Exception {
        Fixture fixture = new Fixture(new CountingProvider(false, false));

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertTrue(result.toString(), result.isSuccess());
        assertEquals("member content", new String(result.value().content(), StandardCharsets.UTF_8));
        assertEquals(1, fixture.provider.resolveCalls);
        assertEquals(1, fixture.strategy.handles);
        assertTrue("the counter closes the prepared handle", fixture.session.closed);
    }

    @Test
    public void authenticatedListing_usesBrokerHandle() throws Exception {
        Fixture fixture = new Fixture(new CountingProvider(false, false));

        MediatedResult<BronzeListing> result = fixture.counter.listChildren(new ResourceAccessRequest(
                ACTOR, FTP_PDS, ResourceOperation.LIST_CHILDREN, "test"));

        assertTrue(result.toString(), result.isSuccess());
        assertEquals(2, result.value().entries().size());
        assertEquals(1, fixture.provider.resolveCalls);
        assertTrue(fixture.session.closed);
    }

    @Test
    public void localFile_neverContactsTheVault() throws Exception {
        Fixture fixture = new Fixture(new CountingProvider(false, false));
        File file = folder.newFile("local.txt");
        Files.write(file.toPath(), "local".getBytes(StandardCharsets.UTF_8));

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(BookmarkUri.parse(file.toURI().toString())));

        assertTrue(result.toString(), result.isSuccess());
        assertEquals(0, fixture.provider.resolveCalls);
        assertEquals(0, fixture.strategy.handles);
    }

    @Test
    public void cancelledCredentialRequest_isATypedOutcome_withoutAcquisition() {
        Fixture fixture = new Fixture(new CountingProvider(true, false));

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertFalse(result.isSuccess());
        assertNull("cancellation is not a policy decision", result.decision());
        assertEquals(MediatedResult.Failure.AUTHENTICATION_CANCELLED, result.failure());
        assertEquals(0, fixture.session.reads);
    }

    @Test
    public void unavailableCredential_isATypedOutcome_distinctFromCancellation() {
        Fixture fixture = new Fixture(new CountingProvider(false, true));

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertEquals(MediatedResult.Failure.AUTHENTICATION_UNAVAILABLE, result.failure());
        assertEquals(0, fixture.session.reads);
    }

    @Test
    public void rejectedAuthentication_isATypedOutcome_andLeaksNoExceptionDetail() {
        Fixture fixture = new Fixture(new CountingProvider(false, false));
        fixture.strategy.reject = true;

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertEquals(MediatedResult.Failure.AUTHENTICATION_FAILED, result.failure());
        assertFalse(result.errorMessage().contains(CountingStrategy.REJECTION_DETAIL));
        assertEquals(0, fixture.session.reads);
    }

    @Test
    public void tamiasDenial_staysAPolicyDecision_andSkipsPreparation() {
        Fixture fixture = new Fixture(new CountingProvider(false, false));
        fixture.policy.denyFetch = true;

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertTrue(result.isDenied());
        assertEquals(AccessReasonCode.SOURCE_DENIED, result.decision().reasonCode());
        assertNull(result.failure());
        assertEquals(0, fixture.provider.resolveCalls);
    }

    @Test
    public void failedAcquisitionWithPreparedHandle_stillClosesTheHandle() {
        Fixture fixture = new Fixture(new CountingProvider(false, false));
        fixture.session.failReads = true;

        MediatedResult<BronzeContent> result = fixture.counter.readContent(read(FTP_MEMBER));

        assertEquals(MediatedResult.Failure.ACQUISITION_FAILED, result.failure());
        assertTrue(fixture.session.closed);
    }

    private static ResourceAccessRequest read(BookmarkUri uri) {
        return new ResourceAccessRequest(ACTOR, uri, ResourceOperation.READ_CONTENT, "test");
    }

    private static final class Fixture {
        final CountingProvider provider;
        final RecordingSession session = new RecordingSession();
        final CountingStrategy strategy = new CountingStrategy(session);
        final FtpRequiresAuthPolicy policy = new FtpRequiresAuthPolicy();
        final MediatedResourceService counter;

        Fixture(CountingProvider provider) {
            this.provider = provider;
            ProviderBackedAccessBroker broker = new ProviderBackedAccessBroker(provider, SecretCachePolicy.disabled());
            AccessRequest ftpAccess = new AccessRequest(
                    "ftp" + "://host", "user", "test", "read", AuthenticationMethod.FTP_PASSWORD, 0L);
            BrokeredAcquisitionAccess station = BrokeredAcquisitionAccess.on(broker)
                    .authenticate(ResourceScheme.FTP, ftpAccess, strategy)
                    .build();
            HolkasAcquisitionPort port = new HolkasAcquisitionPort(new DefaultResourceConnectorRegistry(Arrays.<ResourceConnector>asList(
                    new FileSystemResourceConnector(),
                    new FtpMvsResourceConnector(broker, ftpAccess, strategy))));
            this.counter = new MediatedResourceService(policy, port, new InMemoryResourceArchive(), station);
        }
    }

    /** Tamias stand-in: FTP acquisition needs authentication, local acquisition is allowed. */
    private static final class FtpRequiresAuthPolicy implements ResourceAccessPolicy {
        boolean denyFetch;

        @Override
        public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
            if (request.operation() == ResourceOperation.FETCH_EXTERNAL
                    && ResourceScheme.FTP.equals(request.target().scheme())) {
                if (denyFetch) {
                    return ResourceAccessDecision.deny(AccessReasonCode.SOURCE_DENIED, "test denial");
                }
                return new ResourceAccessDecision(AccessDecisionType.REQUIRE_AUTH,
                        AccessReasonCode.SOURCE_AUTH_REQUIRED, "ftp needs credentials");
            }
            return ResourceAccessDecision.allow();
        }
    }

    private static final class CountingProvider implements SecretMaterialProvider {
        private final boolean cancel;
        private final boolean unavailable;
        int resolveCalls;

        CountingProvider(boolean cancel, boolean unavailable) {
            this.cancel = cancel;
            this.unavailable = unavailable;
        }

        @Override
        public SecretMaterial resolve(AccessRequest request) throws SecretUnavailableException {
            resolveCalls++;
            if (cancel) {
                throw new AuthCancelledException("user cancelled");
            }
            if (unavailable) {
                throw new SecretUnavailableException("no entry");
            }
            return SecretMaterialFactory.fromSecret(request.credentialRef(), request.principal(), "test-only".toCharArray());
        }

        @Override
        public void release(SecretMaterial material) {
            if (material != null) {
                material.close();
            }
        }
    }

    private static final class CountingStrategy implements AuthenticationStrategy<FtpAccessHandle> {
        static final String REJECTION_DETAIL = "server said: bad password for user";
        private final RecordingSession session;
        int handles;
        boolean reject;

        CountingStrategy(RecordingSession session) {
            this.session = session;
        }

        @Override
        public boolean supports(AuthenticationMethod method) {
            return AuthenticationMethod.FTP_PASSWORD.equals(method);
        }

        @Override
        public FtpAccessHandle authenticate(AccessRequest request, SecretMaterial material) throws AccessException {
            if (reject) {
                throw new AccessException(REJECTION_DETAIL);
            }
            handles++;
            AccessGrant grant = new AccessGrant("grant-" + handles, request.targetSystem(),
                    request.principal(), request.purpose(), request.scope(), Long.MAX_VALUE);
            return new FtpAccessHandle(grant, session);
        }
    }

    private static final class RecordingSession implements FtpClientSession {
        boolean closed;
        boolean failReads;
        int reads;

        @Override
        public byte[] readBytes(MvsLocation location, ResourceReadMode readMode) throws IOException {
            if (failReads) {
                throw new IOException("read failed");
            }
            reads++;
            return "member content".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public List<String> listNames(MvsLocation location) {
            return Arrays.asList("MEMBER1", "MEMBER2");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
