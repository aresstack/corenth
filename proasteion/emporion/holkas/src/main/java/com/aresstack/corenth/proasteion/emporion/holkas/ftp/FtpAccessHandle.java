package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;

import java.io.IOException;

/**
 * Access handle that opens routed sessions without exposing credentials.
 */
public final class FtpAccessHandle implements AccessHandle {

    private final AccessGrant grant;
    private final FtpClientSession fixedSession;
    private final FtpClientSessionFactory sessionFactory;
    private FtpClientSession openedSession;

    public FtpAccessHandle(AccessGrant grant, FtpClientSession session) {
        if (grant == null) {
            throw new IllegalArgumentException("grant must not be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        this.grant = grant;
        this.fixedSession = session;
        this.sessionFactory = null;
    }

    public FtpAccessHandle(AccessGrant grant, FtpClientSessionFactory sessionFactory) {
        if (grant == null) {
            throw new IllegalArgumentException("grant must not be null");
        }
        if (sessionFactory == null) {
            throw new IllegalArgumentException("sessionFactory must not be null");
        }
        this.grant = grant;
        this.fixedSession = null;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public AccessGrant grant() {
        return grant;
    }

    public FtpClientSession openSession(NetworkRoutePlan routePlan) throws IOException {
        if (routePlan == null) {
            throw new IllegalArgumentException("routePlan must not be null");
        }
        if (fixedSession != null) {
            openedSession = fixedSession;
            return fixedSession;
        }
        if (openedSession == null) {
            openedSession = sessionFactory.open(new FtpSessionOpenRequest(grant, routePlan));
        }
        return openedSession;
    }

    public FtpClientSession session() {
        return fixedSession != null ? fixedSession : openedSession;
    }

    @Override
    public void close() {
        if (openedSession != null) {
            openedSession.close();
            openedSession = null;
        } else if (fixedSession != null) {
            fixedSession.close();
        }
    }
}
