package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;

/**
 * FTP access handle that exposes an authenticated session, not credentials.
 */
public final class FtpAccessHandle implements AccessHandle {

    private final AccessGrant grant;
    private final FtpClientSession session;

    public FtpAccessHandle(AccessGrant grant, FtpClientSession session) {
        if (grant == null) {
            throw new IllegalArgumentException("grant must not be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        this.grant = grant;
        this.session = session;
    }

    @Override
    public AccessGrant grant() {
        return grant;
    }

    public FtpClientSession session() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }
}
