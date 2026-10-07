package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Connector for local filesystem resources using the {@code file:} scheme.
 *
 * <p>Reads raw bytes and directory listings from the local filesystem.
 */
public final class FileSystemResourceConnector implements ResourceConnector {

    private static final ResourceScheme FILE_SCHEME = ResourceScheme.FILE;

    @Override
    public ResourceScheme supportedScheme() {
        return FILE_SCHEME;
    }

    @Override
    public RawResource fetch(VirtualResourceRef ref) throws IOException {
        Path path = pathFrom(ref);
        requireRegularFile(path);

        byte[] bytes = Files.readAllBytes(path);
        RawResourceContent content = new RawResourceContent(bytes);
        RawResourceMetadata metadata = metadataFor(path, VirtualResourceKind.FILE, content.sizeBytes());
        return new RawResource(ref, content, metadata);
    }

    /**
     * Reads the file through a stream and stops after {@code maxBytes + 1} bytes, so that a file
     * that grew beyond its reported size is never read completely.
     */
    @Override
    public RawResource fetch(VirtualResourceRef ref, long maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0");
        }
        if (maxBytes >= Integer.MAX_VALUE - 8) {
            return fetch(ref);
        }
        Path path = pathFrom(ref);
        requireRegularFile(path);

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(maxBytes + 1, 8192));
        byte[] buffer = new byte[8192];
        long budget = maxBytes + 1;
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while (budget > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, budget))) != -1) {
                out.write(buffer, 0, read);
                budget -= read;
            }
        }
        if (out.size() > maxBytes) {
            throw new ResourceSizeLimitExceededException(out.size(),
                    "file: resource holds more than " + maxBytes + " bytes: " + path);
        }
        RawResourceContent content = new RawResourceContent(out.toByteArray());
        RawResourceMetadata metadata = metadataFor(path, VirtualResourceKind.FILE, content.sizeBytes());
        return new RawResource(ref, content, metadata);
    }

    @Override
    public ResourceListing list(VirtualResourceRef ref) throws IOException {
        Path path = pathFrom(ref);
        if (!Files.isDirectory(path)) {
            throw new ResourceConnectorException("file: resource is not a directory: " + path);
        }

        List<ResourceListingEntry> entries = new ArrayList<ResourceListingEntry>();
        long observedAtMillis = System.currentTimeMillis();
        try (java.util.stream.Stream<Path> children = Files.list(path)) {
            java.util.Iterator<Path> iterator = children.sorted().iterator();
            while (iterator.hasNext()) {
                Path child = iterator.next();
                VirtualResourceKind kind = Files.isDirectory(child)
                        ? VirtualResourceKind.DIRECTORY
                        : VirtualResourceKind.FILE;
                String name = filename(child);
                VirtualResourceRef childRef = new VirtualResourceRef(
                        BookmarkUri.parse(child.toUri().toString()), kind);
                RawResourceMetadata metadata = metadataFor(child, kind, sizeOrZero(child));
                entries.add(new ResourceListingEntry(childRef, name, kind, metadata));
            }
        }
        return new ResourceListing(ref, entries, observedAtMillis);
    }

    /**
     * Reads size, name, type and modification time of a regular file without reading its content.
     */
    @Override
    public RawResourceMetadata metadata(VirtualResourceRef ref) throws IOException {
        Path path = pathFrom(ref);
        requireRegularFile(path);
        return metadataFor(path, VirtualResourceKind.FILE, Files.size(path));
    }

    /** A missing path is a confirmed absence; an existing non-file is an ordinary error. */
    private static void requireRegularFile(Path path) throws ResourceConnectorException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.exists(path)) {
            throw new ResourceNotFoundException("file: resource does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ResourceConnectorException("file: resource is not a regular file: " + path);
        }
    }

    private Path pathFrom(VirtualResourceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (!supports(ref.uri().scheme())) {
            throw new IllegalArgumentException(
                    "FileSystemResourceConnector only supports file: scheme, got: " + ref.uri().scheme());
        }

        URI uri = ref.uri().toURI();
        if (uri == null) {
            throw new IllegalArgumentException("file: bookmark URI did not produce a standard URI");
        }
        return Paths.get(uri);
    }

    private RawResourceMetadata metadataFor(Path path, VirtualResourceKind kind, long sizeBytes) throws IOException {
        long modifiedAtMillis = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        long observedAtMillis = System.currentTimeMillis();
        String contentType = kind == VirtualResourceKind.FILE ? Files.probeContentType(path) : null;
        return new RawResourceMetadata(filename(path), contentType, sizeBytes,
                modifiedAtMillis, observedAtMillis, kind);
    }

    private long sizeOrZero(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.size(path) : 0L;
    }

    private String filename(Path path) {
        return path.getFileName() != null ? path.getFileName().toString() : null;
    }
}
