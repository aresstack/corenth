package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
 *   <li>only targets inside one of the configured roots are served, both lexically and after
 *       resolving symbolic links; everything else is denied with
 *       {@link AccessReasonCode#NOT_WHITELISTED};</li>
 *   <li>only the read path of the archive counter is granted ({@link ResourceOperation#LIST_CHILDREN},
 *       {@link ResourceOperation#READ_METADATA}, {@link ResourceOperation#READ_CONTENT} and the
 *       acquisition step {@link ResourceOperation#FETCH_EXTERNAL}); refresh, index, search-result
 *       and archive-deletion decisions are denied with {@link AccessReasonCode#NOT_WHITELISTED}
 *       until #5 supplies the composed policies for them.</li>
 * </ul>
 *
 * <p>The policy is actor-neutral: humans, bots and services receive the same decision.
 * Actor-specific restrictions ({@link AccessReasonCode#BOT_RESTRICTED}) belong to the composed
 * policies of #5.
 *
 * <p>Containment is checked twice. The normalized absolute path must lie lexically below a root,
 * which rejects {@code ..} traversal before the file system is consulted. Then the path is
 * resolved to its real location (symbolic links, and on Windows junctions, followed through
 * {@link Path#toRealPath(LinkOption...)}) and must lie below the real location of a root.
 * The second check exists because the {@code file:} connector follows links when it reads or
 * lists; the policy therefore judges the object the connector will open, not only the name it
 * was given. A link inside a root that points into another configured root stays accessible.
 * For a path that does not exist yet, its nearest existing ancestor is resolved and the
 * remaining names are appended. If the real location cannot be determined (I/O error, link
 * loop), access is denied.
 *
 * <p>Resolving real paths reads file system metadata only, never content; acquisition stays in
 * the connector. This is the only containment check: the connector does not enforce the roots
 * a second time. A link that is swapped between this decision and the connector's read
 * (time-of-check to time-of-use) is not covered; exploiting it requires write access below an
 * accessible root.
 */
public final class LocalFileRootsAccessPolicy implements ResourceAccessPolicy {

    private static final Set<ResourceOperation> GRANTED_OPERATIONS = Collections.unmodifiableSet(EnumSet.of(
            ResourceOperation.LIST_CHILDREN,
            ResourceOperation.READ_METADATA,
            ResourceOperation.READ_CONTENT,
            ResourceOperation.FETCH_EXTERNAL));

    private final List<Path> roots;
    private final List<Path> realRoots;

    /**
     * @param roots the local directories whose contents may be read; must not be empty
     */
    public LocalFileRootsAccessPolicy(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("at least one accessible root is required");
        }
        List<Path> normalized = new ArrayList<Path>();
        List<Path> real = new ArrayList<Path>();
        for (Path root : roots) {
            if (root == null) {
                throw new IllegalArgumentException("root must not be null");
            }
            Path absolute = root.toAbsolutePath().normalize();
            normalized.add(absolute);
            real.add(realPathOrSelf(absolute));
        }
        this.roots = Collections.unmodifiableList(normalized);
        this.realRoots = Collections.unmodifiableList(real);
    }

    /** Returns the normalized absolute roots this policy grants access to. */
    public List<Path> roots() {
        return roots;
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
        if (!isInside(path, roots)) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "target is outside the accessible local roots: " + path);
        }
        Path realPath;
        try {
            realPath = realLocation(path);
        } catch (IOException e) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "real location of the target cannot be determined: " + path);
        }
        if (!isInside(realPath, realRoots)) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "target resolves outside the accessible local roots: " + path + " -> " + realPath);
        }
        if (!GRANTED_OPERATIONS.contains(request.operation())) {
            return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                    "operation " + request.operation() + " is not granted by the read-only local access policy");
        }
        return ResourceAccessDecision.allow();
    }

    private static boolean isInside(Path path, List<Path> candidates) {
        for (Path root : candidates) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the real location of a normalized absolute path: the nearest existing ancestor
     * (or the path itself) with all links resolved, followed by the names that do not exist yet.
     */
    private static Path realLocation(Path path) throws IOException {
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return path;
        }
        Path real = existing.toRealPath();
        return real.resolve(existing.relativize(path)).normalize();
    }

    /** Resolves a root to its real location; a root that cannot be resolved stays as given. */
    private static Path realPathOrSelf(Path root) {
        try {
            return realLocation(root);
        } catch (IOException e) {
            return root;
        }
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
