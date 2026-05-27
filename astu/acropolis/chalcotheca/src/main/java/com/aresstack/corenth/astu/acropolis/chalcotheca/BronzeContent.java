package com.aresstack.corenth.astu.acropolis.chalcotheca;

import com.aresstack.corenth.astu.BookmarkUri;

/**
 * Bronze-level content snapshot: the actual acquired content for a resource.
 */
public final class BronzeContent {

    private final BookmarkUri uri;
    private final byte[] content;
    private final ResourceDigest digest;
    private final long fetchedAtMillis;

    public BronzeContent(BookmarkUri uri, byte[] content, ResourceDigest digest, long fetchedAtMillis) {
        if (uri == null) throw new IllegalArgumentException("uri must not be null");
        if (content == null) throw new IllegalArgumentException("content must not be null");
        this.uri = uri;
        this.content = content;
        this.digest = digest;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    public BookmarkUri uri() { return uri; }
    public byte[] content() { return content; }
    public ResourceDigest digest() { return digest; }
    public long fetchedAtMillis() { return fetchedAtMillis; }

    @Override
    public String toString() {
        return "BronzeContent{" + uri + ", " + content.length + " bytes}";
    }
}
