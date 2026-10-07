package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.BookmarkUri;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
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

    @Test(expected = IllegalArgumentException.class)
    public void requiresAtLeastOneRoot() {
        new LocalFileRootsAccessPolicy(Collections.<Path>emptyList());
    }

    private static BookmarkUri uri(Path path) {
        return BookmarkUri.parse(path.toUri().toString());
    }
}
