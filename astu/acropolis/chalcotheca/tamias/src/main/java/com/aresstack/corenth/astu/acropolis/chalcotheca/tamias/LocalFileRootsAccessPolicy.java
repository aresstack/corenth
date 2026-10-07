package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deny-by-default access policy that grants read-only access to local {@code file:} resources
 * below explicitly configured root directories.
 *
 * <p>This is the first productive {@link ResourceAccessPolicy} (#10 Slice 2, ADR-0001 guard
 * rail 5). It is deliberately narrow instead of an "allow everything" placeholder:
 * <ul>
 *   <li>only the {@code file:} scheme is served; every other scheme is denied with
 *       {@link AccessReasonCode#SOURCE_DENIED};</li>
 *   <li>only targets lexically inside one of the configured roots are served; everything else
 *       is denied with {@link AccessReasonCode#NOT_WHITELISTED};</li>
 *   <li>only the read path of the archive counter is granted ({@link ResourceOperation#LIST_CHILDREN},
 *       {@link ResourceOperation#READ_METADATA}, {@link ResourceOperation#READ_CONTENT} and the
 *       acquisition step {@link ResourceOperation#FETCH_EXTERNAL}); index, search-result and
 *       archive-deletion decisions are denied with {@link AccessReasonCode#NOT_WHITELISTED}
 *       until #5 supplies the composed policies for them;</li>
 *   <li>{@link ResourceOperation#REFRESH_EXTERNAL} (re-reading a source whose payload is cached)
 *       is granted only when the policy is constructed with source refresh enabled
 *       (#10 Slice 5); otherwise it is denied like the other operations.</li>
 * </ul>
 *
 * <p>The policy is actor-neutral: humans, bots and services receive the same decision.
 * Actor-specific restrictions ({@link AccessReasonCode#BOT_RESTRICTED}) belong to the composed
 * policies of #5.
 *
 * <p>Containment is checked lexically on normalized absolute paths; the policy performs no file
 * system access. Symbolic links inside a root that point outside of it are therefore not
 * detected here (known limitation, to be revisited with the #5 source checks).
 */
public final class LocalFileRootsAccessPolicy implements ResourceAccessPolicy {

    private static final Set<ResourceOperation> GRANTED_OPERATIONS = Collections.unmodifiableSet(EnumSet.of(
            ResourceOperation.LIST_CHILDREN,
            ResourceOperation.READ_METADATA,
            ResourceOperation.READ_CONTENT,
            ResourceOperation.FETCH_EXTERNAL));

    private final List<Path> roots;
    private final boolean sourceRefresh;

    /**
     * @param roots the local directories whose contents may be read; must not be empty
     */
    public LocalFileRootsAccessPolicy(List<Path> roots) {
        this(roots, false);
    }

    /**
     * Creates the policy with an explicit refresh setting (#10 Slice 5).
     *
     * @param roots         the accessible root directories; at least one
     * @param sourceRefresh whether {@link ResourceOperation#REFRESH_EXTERNAL} is granted inside
     *                      the roots, so that a cached payload is re-read from the file system
     */
    public LocalFileRootsAccessPolicy(List<Path> roots, boolean sourceRefresh) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("at least one accessible root is required");
        }
        List<Path> normalized = new ArrayList<Path>();
        for (Path root : roots) {
            if (root == null) {
                throw new IllegalArgumentException("root must not be null");
            }
            normalized.add(root.toAbsolutePath().normalize());
        }
        this.roots = Collections.unmodifiableList(normalized);
        this.sourceRefresh = sourceRefresh;
    }

    /** Returns the normalized absolute roots this policy grants access to. */
    public List<Path> roots() {
        return roots;
    }

    /** Returns whether {@link ResourceOperation#REFRESH_EXTERNAL} is granted inside the roots. */
    public boolean sourceRefresh() {
        return sourceRefresh;
    }

    @Override
    public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
        if (request == null) {
            return ResourceAccessDecision.deny(AccessReasonCode.UNKNOWN_RESOURCE, "request must not be null");
        }
        BookmarkUri target = request.target();
        if (!ResourceScheme.FILE.equals(target.scheme())) {
            return ResourceAccessDecision.deny(AccessReasonCode.SOURCE_DENIED,
                    "only local file: resources are served, got scheme " + target.scheme());
        }
        Path path = localPath(target);
        if (path == null) {
            return ResourceAccessDecision.deny(AccessReasonCode.UNKNOWN_RESOURCE,
                    "file: target cannot be resolved to a local path: " + target);
        }
        if (!isInsideRoot(path)) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "target is outside the accessible local roots: " + path);
        }
        if (request.operation() == ResourceOperation.REFRESH_EXTERNAL && sourceRefresh) {
            return ResourceAccessDecision.allow();
        }
        if (!GRANTED_OPERATIONS.contains(request.operation())) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "operation " + request.operation() + " is not granted by the read-only local access policy");
        }
        return ResourceAccessDecision.allow();
    }

    private boolean isInsideRoot(Path path) {
        for (Path root : roots) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static Path localPath(BookmarkUri target) {
        URI uri = target.toURI();
        if (uri == null) {
            return null;
        }
        try {
            return Paths.get(uri).toAbsolutePath().normalize();
        } catch (IllegalArgumentException e) {
            return null;
        } catch (FileSystemNotFoundException e) {
            return null;
        }
    }
}
