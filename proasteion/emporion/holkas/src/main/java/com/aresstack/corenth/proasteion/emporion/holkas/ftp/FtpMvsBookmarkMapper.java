package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsQuoteNormalizer;

import java.net.URI;

public final class FtpMvsBookmarkMapper {

    public MvsLocation locationOf(BookmarkUri uri) {
        if (uri == null || uri.toURI() == null) {
            throw new IllegalArgumentException("bookmark URI must be a standard URI");
        }
        URI standardUri = uri.toURI();
        String path = standardUri.getPath();
        if (path == null || path.length() == 0 || "/".equals(path)) {
            return MvsLocation.root();
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.indexOf('(') < 0 && path.indexOf('.') >= 0) {
            return MvsLocation.dataset(path);
        }
        return MvsLocation.parse(path);
    }

    public BookmarkUri childUri(BookmarkUri parentUri, MvsLocation child) {
        if (parentUri == null || parentUri.toURI() == null) {
            throw new IllegalArgumentException("parentUri must be a standard URI");
        }
        if (child == null) {
            throw new IllegalArgumentException("child must not be null");
        }
        URI parent = parentUri.toURI();
        String host = parent.getHost();
        int port = parent.getPort();
        String path = MvsQuoteNormalizer.unquote(child.logicalPath());
        String authority = port > 0 ? host + ":" + port : host;
        return BookmarkUri.parse("ftp" + "://" + authority + "/" + path);
    }
}
