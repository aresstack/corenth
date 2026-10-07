package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.ProcessingResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.proasteion.katagogion.AllowlistToolAdmissionPolicy;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;
import com.aresstack.corenth.proasteion.katagogion.ToolFailureReason;
import com.aresstack.corenth.proasteion.katagogion.ToolHost;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;
import com.aresstack.corenth.proasteion.katagogion.reference.CorenthReferencePlugin;
import com.aresstack.corenth.proasteion.katagogion.reference.LexicalSearchTool;
import com.aresstack.corenth.proasteion.katagogion.reference.ReadResourceTool;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Runs both reference tools through the productive composition and Tamias. */
public class ToolCapabilityBindingTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path root;
    private CorenthApplication application;
    private ToolHost host;

    @Before
    public void setUp() throws IOException {
        root = tempFolder.newFolder("documents").toPath();
        application = new CorenthComposition().composeLocal(ApplicationSettings.builder()
                .accessibleRoot(root)
                .indexDirectory(tempFolder.newFolder("index").toPath())
                .build());
        host = new ToolHost(AllowlistToolAdmissionPolicy.builder()
                .allow(CorenthReferencePlugin.ID, EnumSet.allOf(ToolCapability.class))
                .build(),
                ToolCapabilityBinding.bind(application, new ActorIdentity("tool-agent", ActorType.BOT)));
        assertTrue(host.install(CorenthReferencePlugin.all()).isInstalled());
    }

    @After
    public void tearDown() throws IOException {
        application.close();
    }

    @Test
    public void searchToolFindsWhatTheLifecycleIndexed() throws IOException {
        Path notes = write(root.resolve("notes.txt"), "Tamias decides before Holkas acquires anything.");
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(notes)).status());

        ToolResult result = host.invoke(new ToolInvocation(LexicalSearchTool.NAME,
                Collections.singletonMap("query", "Holkas")));

        assertTrue(result.toString(), result.isSuccess());
        assertEquals(1, result.records().size());
        assertEquals(ref(notes).uri().toString(), result.records().get(0).get("uri"));
    }

    @Test
    public void readToolReadsInsideTheAccessibleRootThroughTamias() throws IOException {
        Path notes = write(root.resolve("notes.txt"), "bronze counter");

        ToolResult result = host.invoke(new ToolInvocation(ReadResourceTool.NAME,
                Collections.singletonMap("uri", notes.toUri().toString())));

        assertTrue(result.toString(), result.isSuccess());
        assertEquals("bronze counter", result.text());
    }

    @Test
    public void readToolIsDeniedOutsideTheAccessibleRoot() throws IOException {
        Path outside = write(tempFolder.newFolder("private").toPath().resolve("secret.txt"), "not for tools");

        ToolResult result = host.invoke(new ToolInvocation(ReadResourceTool.NAME,
                Collections.singletonMap("uri", outside.toUri().toString())));

        assertEquals(ToolFailureReason.ACCESS_DENIED, result.failureReason());
        assertTrue(result.text(), result.text().contains("NOT_WHITELISTED"));
        assertTrue(result.records().isEmpty());
    }

    private static Path write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(Charset.forName("UTF-8")));
        return file;
    }

    private static VirtualResourceRef ref(Path file) {
        return new VirtualResourceRef(BookmarkUri.parse(file.toUri().toString()), VirtualResourceKind.FILE);
    }
}
