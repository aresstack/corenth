package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.ContentInspector;
import com.aresstack.corenth.astu.acropolis.ProcessingResult;
import com.aresstack.corenth.astu.acropolis.ResourceLifecycleCoordinator;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeListing;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalSearchResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.AccessReasonCode;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Proves the productive composition point: no test-only wiring, everything below is the
 * {@link CorenthComposition} output.
 */
public class CorenthCompositionTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path root;
    private Path indexDirectory;
    private final List<CorenthApplication> opened = new ArrayList<CorenthApplication>();

    @Before
    public void setUp() throws IOException {
        root = tempFolder.newFolder("documents").toPath();
        indexDirectory = tempFolder.newFolder("index").toPath();
    }

    @After
    public void tearDown() throws IOException {
        for (CorenthApplication application : opened) {
            application.close();
        }
    }

    @Test
    public void composesHeadlessWithoutDisplay() throws IOException {
        assertEquals("tests run headless", "true", System.getProperty("java.awt.headless"));

        CorenthApplication application = compose(settings().build());

        assertNotNull(application.resourceLifecycle());
        assertNotNull(application.search());
        assertNotNull(application.mediatedResourceAccess());
    }

    @Test
    public void localFileWalkingSkeletonRunsThroughTheComposedMediatedPath() throws IOException {
        Path markdown = write(root.resolve("guide.md"), "# Harbor\n\nThe bronze counter mediates every acquisition.");
        Path text = write(root.resolve("notes.txt"), "Tamias decides before Holkas acquires anything.");
        CorenthApplication application = compose(settings().build());

        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(markdown)).status());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(text)).status());

        List<LexicalSearchResult> bronze = application.search().search("bronze", 10);
        assertEquals(1, bronze.size());
        assertEquals(ref(markdown).uri(), bronze.get(0).resourceRef().uri());
        assertEquals(1, application.search().search("Holkas", 10).size());

        assertEquals(ProcessingResult.Status.UNCHANGED, application.resourceLifecycle().process(ref(markdown)).status());
    }

    @Test
    public void tamiasDeniesFilesOutsideTheAccessibleRoots() throws IOException {
        Path outside = write(tempFolder.newFolder("private").toPath().resolve("secret.txt"), "not for the index");
        CorenthApplication application = compose(settings().build());

        ProcessingResult result = application.resourceLifecycle().process(ref(outside));

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().contains(AccessReasonCode.NOT_WHITELISTED.name()));
        assertTrue(application.search().search("index", 10).isEmpty());
    }

    @Test
    public void hostIndexingPatternsParameteriseTheTamiasIndexingPolicy() throws IOException {
        Path text = write(root.resolve("notes.txt"), "plain text that is not wanted");
        Path markdown = write(root.resolve("readme.md"), "markdown that is wanted");
        CorenthApplication application = compose(settings().indexedPattern("**/*.md").build());

        assertEquals(ProcessingResult.Status.DENIED, application.resourceLifecycle().process(ref(text)).status());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(markdown)).status());
    }

    @Test
    public void unsupportedContentFailsInInspectionWithoutLeavingTheMediatedPath() throws IOException {
        Path binary = write(root.resolve("archive.bin"), "opaque");
        CorenthApplication application = compose(settings().build());

        ProcessingResult result = application.resourceLifecycle().process(ref(binary));

        assertEquals(ProcessingResult.Status.FAILED, result.status());
        assertTrue(result.message(), result.message().contains("No extractor"));
    }

    @Test
    public void hostsBrowseThroughTheMediatedAccessContract() throws IOException {
        write(root.resolve("a.txt"), "a");
        CorenthApplication application = compose(settings().build());
        MediatedResourceAccess access = application.mediatedResourceAccess();
        ActorIdentity human = new ActorIdentity("angelo", ActorType.HUMAN);

        MediatedResult<BronzeListing> listing = access.listChildren(new ResourceAccessRequest(
                human, BookmarkUri.parse(root.toUri().toString()), ResourceOperation.LIST_CHILDREN));

        assertTrue(listing.isSuccess());
        assertEquals(1, listing.value().entries().size());
        assertFalse("the public contract hides the counter's administrative operations",
                hasPublicMethod(MediatedResourceAccess.class, "deleteEntry"));
    }

    @Test
    public void symlinksInsideTheRootCannotEscapeItThroughTheComposedMediatedPath() throws IOException {
        Path privateDir = tempFolder.newFolder("private").toPath();
        write(privateDir.resolve("secret.txt"), "not for the index");
        write(root.resolve("a.txt"), "a");
        Path dirLink = symlink(root.resolve("link"), privateDir);
        Path fileLink = symlink(root.resolve("secret-link.txt"), privateDir.resolve("secret.txt"));
        CorenthApplication application = compose(settings().build());
        MediatedResourceAccess access = application.mediatedResourceAccess();
        ActorIdentity human = new ActorIdentity("angelo", ActorType.HUMAN);

        MediatedResult<BronzeListing> rootListing = access.listChildren(new ResourceAccessRequest(
                human, uri(root), ResourceOperation.LIST_CHILDREN));
        assertTrue("the root itself stays listable", rootListing.isSuccess());

        MediatedResult<BronzeListing> linkListing = access.listChildren(new ResourceAccessRequest(
                human, uri(dirLink), ResourceOperation.LIST_CHILDREN));
        assertFalse(linkListing.isSuccess());
        assertEquals(AccessReasonCode.NOT_WHITELISTED, linkListing.decision().reasonCode());

        for (Path escaping : Arrays.asList(dirLink.resolve("secret.txt"), fileLink)) {
            MediatedResult<BronzeContent> read = access.readContent(new ResourceAccessRequest(
                    human, uri(escaping), ResourceOperation.READ_CONTENT));
            assertFalse(escaping.toString(), read.isSuccess());
            assertEquals(escaping.toString(), AccessReasonCode.NOT_WHITELISTED, read.decision().reasonCode());

            ProcessingResult processed = application.resourceLifecycle().process(ref(escaping));
            assertEquals(escaping.toString(), ProcessingResult.Status.DENIED, processed.status());
        }
        assertTrue(application.search().search("index", 10).isEmpty());

        MediatedResult<BronzeContent> regular = access.readContent(new ResourceAccessRequest(
                human, uri(root.resolve("a.txt")), ResourceOperation.READ_CONTENT));
        assertTrue("a regular file inside the root stays readable", regular.isSuccess());
    }

    @Test
    public void contentInspectorIsWiredProductivelyToDeigma() throws Exception {
        CorenthApplication application = compose(settings().build());

        Object inspector = fieldValue(application.resourceLifecycle(), ContentInspector.class);

        assertEquals(DeigmaContentInspector.class, inspector.getClass());
    }

    @Test
    public void acropolisSeesNoHolkasImplementation() throws Exception {
        CorenthApplication application = compose(settings().build());

        for (Field field : ResourceLifecycleCoordinator.class.getDeclaredFields()) {
            assertFalse(field + " exposes an outer adapter type to Acropolis",
                    field.getType().getName().startsWith("com.aresstack.corenth.proasteion."));
        }
        Object mediated = fieldValue(application.resourceLifecycle(), MediatedResourceAccess.class);
        assertFalse("the lifecycle receives the counter, never the Holkas bridge",
                mediated.getClass().getName().startsWith("com.aresstack.corenth.proasteion."));
    }

    @Test
    public void publicApiExposesOnlyInnerContracts() {
        for (Class<?> type : Arrays.<Class<?>>asList(CorenthComposition.class, CorenthApplication.class,
                ApplicationSettings.class, ApplicationSettings.Builder.class)) {
            for (Method method : type.getMethods()) {
                assertInnerType(method, method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertInnerType(method, parameter);
                }
            }
        }
    }

    @Test
    public void eachCompositionBuildsAnIndependentObjectGraph() throws IOException {
        CorenthComposition composition = new CorenthComposition();
        CorenthApplication first = composition.composeLocal(settings().build());
        opened.add(first);
        Path otherIndex = tempFolder.newFolder("other-index").toPath();
        CorenthApplication second = composition.composeLocal(ApplicationSettings.builder()
                .indexDirectory(otherIndex).accessibleRoot(root).build());
        opened.add(second);

        assertNotSame(first.resourceLifecycle(), second.resourceLifecycle());
        assertNotSame(first.mediatedResourceAccess(), second.mediatedResourceAccess());
    }

    @Test
    public void compositionHoldsNoStaticMutableState() {
        for (Class<?> type : Arrays.<Class<?>>asList(CorenthComposition.class, CorenthApplication.class,
                ApplicationSettings.class, ApplicationSettings.Builder.class, DeigmaContentInspector.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    assertTrue(field + " must be a constant", Modifier.isFinal(field.getModifiers()));
                    assertTrue(field + " must be a constant",
                            field.getType().isPrimitive() || field.getType() == String.class);
                }
            }
        }
        assertEquals("the composition carries no instance state", 0, instanceFieldCount(CorenthComposition.class));
    }

    @Test
    public void closeReleasesTheIndexSoItCanBeReopened() throws IOException {
        Path text = write(root.resolve("notes.txt"), "persisted lexical content");
        CorenthApplication first = new CorenthComposition().composeLocal(settings().build());
        assertEquals(ProcessingResult.Status.INDEXED, first.resourceLifecycle().process(ref(text)).status());
        first.close();

        CorenthApplication reopened = compose(settings().build());

        assertEquals(1, reopened.search().search("persisted", 10).size());
    }

    @Test
    public void settingsRequireIndexDirectoryAndRoot() {
        try {
            ApplicationSettings.builder().accessibleRoot(root).build();
            fail("index directory is required");
        } catch (IllegalStateException expected) {
            // expected
        }
        try {
            ApplicationSettings.builder().indexDirectory(indexDirectory).build();
            fail("an accessible root is required");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private ApplicationSettings.Builder settings() {
        return ApplicationSettings.builder().indexDirectory(indexDirectory).accessibleRoot(root);
    }

    private CorenthApplication compose(ApplicationSettings settings) throws IOException {
        CorenthApplication application = new CorenthComposition().composeLocal(settings);
        opened.add(application);
        return application;
    }

    private static VirtualResourceRef ref(Path path) {
        return new VirtualResourceRef(BookmarkUri.parse(path.toUri().toString()), VirtualResourceKind.FILE);
    }

    private static BookmarkUri uri(Path path) {
        return BookmarkUri.parse(path.toUri().toString());
    }

    /** Creates a symbolic link; skips the test where the platform or account cannot create one. */
    private static Path symlink(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (IOException e) {
            Assume.assumeNoException("symbolic links are not available here", e);
        } catch (UnsupportedOperationException e) {
            Assume.assumeNoException("symbolic links are not supported here", e);
        }
        throw new AssertionError("unreachable");
    }

    private static Path write(Path path, String content) throws IOException {
        File parent = path.toFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        return Files.write(path, content.getBytes(UTF_8));
    }

    private static Object fieldValue(Object owner, Class<?> declaredType) throws IllegalAccessException {
        for (Field field : owner.getClass().getDeclaredFields()) {
            if (field.getType() == declaredType) {
                field.setAccessible(true);
                return field.get(owner);
            }
        }
        throw new AssertionError("no field of type " + declaredType.getName() + " in " + owner.getClass());
    }

    private static int instanceFieldCount(Class<?> type) {
        int count = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasPublicMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void assertInnerType(Method method, Class<?> type) {
        String name = type.getName();
        boolean outerAdapter = name.startsWith("com.aresstack.corenth.proasteion.")
                && !name.startsWith("com.aresstack.corenth.proasteion.application.");
        boolean implementation = name.equals("com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService")
                || name.startsWith("com.aresstack.corenth.adyton.")
                || name.endsWith("AccessPolicy") && !type.isInterface()
                || name.endsWith("ResourcePolicy") && !type.isInterface();
        assertFalse(method + " exposes " + name, outerAdapter || implementation);
    }
}
