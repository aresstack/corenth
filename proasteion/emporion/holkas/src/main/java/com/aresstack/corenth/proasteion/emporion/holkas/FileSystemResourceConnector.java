package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Connector for local filesystem resources using the {@code file:} scheme.
 *
 * <p>Reads raw bytes from the local filesystem. The URI must be a valid
 * {@code file:} URI (e.g. {@code file:///C:/Users/example/note.txt}).
 */
public final class FileSystemResourceConnector implements ResourceConnector {

    private static final ResourceScheme FILE_SCHEME = ResourceScheme.of("file");

    @Override
    public ResourceScheme supportedScheme() {
        return FILE_SCHEME;
    }

    @Override
    public RawResource fetch(VirtualResourceRef ref) throws IOException {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        if (!FILE_SCHEME.equals(ref.uri().scheme())) {
            throw new IllegalArgumentException(
                    "FileSystemResourceConnector only supports file: scheme, got: " + ref.uri().scheme());
        }

        URI uri = ref.uri().toURI();
        if (uri == null) {
            throw new IllegalArgumentException("file: bookmark URI did not produce a standard URI");
        }

        Path path = Paths.get(uri);
        byte[] bytes = Files.readAllBytes(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();

        String filename = path.getFileName() != null ? path.getFileName().toString() : null;

        RawResourceContent content = new RawResourceContent(bytes);
        return new RawResource(ref, content, filename, lastModified);
    }
}
