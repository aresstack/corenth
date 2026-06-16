package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;

/**
 * Request to open a routed MVS client session from an authenticated handle.
 */
public final class FtpSessionOpenRequest {

    private final AccessGrant grant;
    private final NetworkRoutePlan routePlan;

    public FtpSessionOpenRequest(AccessGrant grant, NetworkRoutePlan routePlan) {
        if (grant == null) {
            throw new IllegalArgumentException("grant must not be null");
        }
        if (routePlan == null) {
            throw new IllegalArgumentException("routePlan must not be null");
        }
        this.grant = grant;
        this.routePlan = routePlan;
    }

    public AccessGrant grant() {
        return grant;
    }

    public NetworkRoutePlan routePlan() {
        return routePlan;
    }
}
