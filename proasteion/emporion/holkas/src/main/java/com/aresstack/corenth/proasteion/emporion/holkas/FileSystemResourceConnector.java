package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
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
        if (!Files.isRegularFile(path)) {
            throw new ResourceConnectorException("file: resource is not a regular file: " + path);
        }

        byte[] bytes = Files.readAllBytes(path);
        RawResourceContent content = new RawResourceContent(bytes);
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
