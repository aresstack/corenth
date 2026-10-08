package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.BookmarkUri;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalFileRootsAccessPolicyTest {

    private static final ActorIdentity SERVICE = new ActorIdentity("lifecycle", ActorType.SERVICE);
    private static final ActorIdentity BOT = new ActorIdentity("assistant", ActorType.BOT);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path root;
    private Path outside;
    private LocalFileRootsAccessPolicy policy;

    @Before
    public void setUp() throws Exception {
        root = tempFolder.newFolder("root").toPath();
        outside = tempFolder.newFolder("outside").toPath();
        policy = new LocalFileRootsAccessPolicy(Collections.singletonList(root));
    }

    @Test
    public void allowsReadPathOperationsInsideRoot() {
        BookmarkUri file = uri(root.resolve("notes.txt"));
        for (ResourceOperation operation : Arrays.asList(ResourceOperation.READ_CONTENT,
                ResourceOperation.FETCH_EXTERNAL, ResourceOperation.LIST_CHILDREN, ResourceOperation.READ_METADATA)) {
            ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(SERVICE, file, operation));
            assertEquals(operation.name(), AccessDecisionType.ALLOW, decision.type());
        }
    }

    @Test
    public void rootItselfIsAccessible() {
        ResourceAccessDecision decision = policy.evaluate(
                new ResourceAccessRequest(SERVICE, uri(root), ResourceOperation.LIST_CHILDREN));
        assertEquals(AccessDecisionType.ALLOW, decision.type());
    }

    @Test
    public void deniesTargetsOutsideRoots() {
        ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(outside.resolve("secret.txt")), ResourceOperation.READ_CONTENT));
        assertEquals(AccessDecisionType.DENY, decision.type());
        assertEquals(AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
    }

    @Test
    public void deniesTraversalOutOfRoot() {
        BookmarkUri traversal = BookmarkUri.parse(root.toUri().toString() + "../outside/secret.txt");
        ResourceAccessDecision decision = policy.evaluate(
                new ResourceAccessRequest(SERVICE, traversal, ResourceOperation.READ_CONTENT));
        assertEquals(AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
    }

    @Test
    public void deniesSiblingWithSharedNamePrefix() throws Exception {
        File sibling = tempFolder.newFolder("root-sibling");
        ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(sibling.toPath().resolve("a.txt")), ResourceOperation.READ_CONTENT));
        assertEquals(AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
    }

    @Test
    public void deniesNonFileSchemes() {
        ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(
                SERVICE, BookmarkUri.parse("https://example.org/a.txt"), ResourceOperation.READ_CONTENT));
        assertEquals(AccessDecisionType.DENY, decision.type());
        assertEquals(AccessReasonCode.SOURCE_DENIED, decision.reasonCode());
    }

    @Test
    public void deniesOperationsBeyondTheReadPath() {
        BookmarkUri file = uri(root.resolve("notes.txt"));
        for (ResourceOperation operation : Arrays.asList(ResourceOperation.DELETE_ARCHIVE_ENTRY,
                ResourceOperation.REFRESH_EXTERNAL, ResourceOperation.INDEX_CONTENT, ResourceOperation.SEARCH_RESULT)) {
            ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(SERVICE, file, operation));
            assertFalse(operation.name(), decision.isAllowed());
            assertEquals(AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
        }
    }

    @Test
    public void isActorNeutral() {
        BookmarkUri file = uri(root.resolve("notes.txt"));
        assertTrue(policy.evaluate(new ResourceAccessRequest(BOT, file, ResourceOperation.READ_CONTENT)).isAllowed());
    }

    @Test
    public void allowsExistingRegularFileInsideRoot() throws Exception {
        Path file = write(root.resolve("docs").resolve("guide.md"));
        for (ResourceOperation operation : Arrays.asList(ResourceOperation.READ_CONTENT,
                ResourceOperation.FETCH_EXTERNAL, ResourceOperation.READ_METADATA)) {
            assertEquals(operation.name(), AccessDecisionType.ALLOW,
                    policy.evaluate(new ResourceAccessRequest(SERVICE, uri(file), operation)).type());
        }
        assertEquals(AccessDecisionType.ALLOW, policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(file.getParent()), ResourceOperation.LIST_CHILDREN)).type());
    }

    @Test
    public void deniesFileSymlinkInsideRootPointingOutside() throws Exception {
        Path secret = write(outside.resolve("secret.txt"));
        Path link = symlink(root.resolve("secret-link.txt"), secret);
        for (ResourceOperation operation : Arrays.asList(ResourceOperation.READ_CONTENT,
                ResourceOperation.FETCH_EXTERNAL, ResourceOperation.READ_METADATA)) {
            ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(SERVICE, uri(link), operation));
            assertEquals(operation.name(), AccessDecisionType.DENY, decision.type());
            assertEquals(operation.name(), AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
        }
    }

    @Test
    public void deniesListingAndReadingThroughDirectorySymlinkPointingOutside() throws Exception {
        write(outside.resolve("secret.txt"));
        Path link = symlink(root.resolve("link"), outside);

        ResourceAccessDecision listing = policy.evaluate(
                new ResourceAccessRequest(SERVICE, uri(link), ResourceOperation.LIST_CHILDREN));
        assertEquals(AccessDecisionType.DENY, listing.type());
        assertEquals(AccessReasonCode.NOT_WHITELISTED, listing.reasonCode());

        for (ResourceOperation operation : Arrays.asList(ResourceOperation.READ_CONTENT,
                ResourceOperation.FETCH_EXTERNAL, ResourceOperation.READ_METADATA)) {
            ResourceAccessDecision decision = policy.evaluate(new ResourceAccessRequest(
                    SERVICE, uri(link.resolve("secret.txt")), operation));
            assertEquals(operation.name(), AccessReasonCode.NOT_WHITELISTED, decision.reasonCode());
        }
        ResourceAccessDecision notYetExisting = policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(link.resolve("later").resolve("new.txt")), ResourceOperation.READ_CONTENT));
        assertEquals(AccessReasonCode.NOT_WHITELISTED, notYetExisting.reasonCode());
    }

    @Test
    public void deniesNestedDirectorySymlinkPointingOutside() throws Exception {
        write(outside.resolve("deep").resolve("secret.txt"));
        Files.createDirectories(root.resolve("a"));
        Path link = symlink(root.resolve("a").resolve("b"), outside.resolve("deep"));

        assertEquals(AccessReasonCode.NOT_WHITELISTED, policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(link.resolve("secret.txt")), ResourceOperation.READ_CONTENT)).reasonCode());
    }

    @Test
    public void deniesDanglingSymlinkInsideRoot() throws Exception {
        Path link = symlink(root.resolve("dangling.txt"), outside.resolve("missing.txt"));

        assertEquals(AccessDecisionType.DENY, policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(link), ResourceOperation.READ_CONTENT)).type());
    }

    @Test
    public void allowsSymlinkInsideRootPointingInsideRoot() throws Exception {
        Path target = write(root.resolve("real").resolve("a.txt"));
        Path link = symlink(root.resolve("alias"), target.getParent());

        assertEquals(AccessDecisionType.ALLOW, policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(link), ResourceOperation.LIST_CHILDREN)).type());
        assertEquals(AccessDecisionType.ALLOW, policy.evaluate(new ResourceAccessRequest(
                SERVICE, uri(link.resolve("a.txt")), ResourceOperation.READ_CONTENT)).type());
    }

    @Test
    public void rootConfiguredThroughSymlinkStaysListableAndReadable() throws Exception {
        Path file = write(root.resolve("a.txt"));
        Path rootAlias = symlink(tempFolder.getRoot().toPath().resolve("root-alias"), root);
        LocalFileRootsAccessPolicy aliased = new LocalFileRootsAccessPolicy(Collections.singletonList(rootAlias));

        assertEquals(AccessDecisionType.ALLOW, aliased.evaluate(new ResourceAccessRequest(
                SERVICE, uri(rootAlias), ResourceOperation.LIST_CHILDREN)).type());
        assertEquals(AccessDecisionType.ALLOW, aliased.evaluate(new ResourceAccessRequest(
                SERVICE, uri(rootAlias.resolve(file.getFileName())), ResourceOperation.READ_CONTENT)).type());
        assertEquals(AccessReasonCode.NOT_WHITELISTED, aliased.evaluate(new ResourceAccessRequest(
                SERVICE, uri(outside.resolve("x.txt")), ResourceOperation.READ_CONTENT)).reasonCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiresAtLeastOneRoot() {
        new LocalFileRootsAccessPolicy(Collections.<Path>emptyList());
    }

    private static Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, "content".getBytes(StandardCharsets.UTF_8));
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

    private static BookmarkUri uri(Path path) {
        return BookmarkUri.parse(path.toUri().toString());
    }
}
