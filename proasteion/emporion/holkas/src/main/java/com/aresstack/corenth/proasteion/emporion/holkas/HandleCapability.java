package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionCapability;

/**
 * Acquisition capability backed by an Adyton {@link AccessHandle}.
 *
 * <p>Only Holkas can unwrap the handle; the archive counter sees the opaque capability.
 * Closing the capability closes the handle exactly once.
 */
final class HandleCapability implements AcquisitionCapability {

    private final AccessHandle handle;
    private boolean closed;

    HandleCapability(AccessHandle handle) {
        if (handle == null) throw new IllegalArgumentException("handle must not be null");
        this.handle = handle;
    }

    AccessHandle handle() {
        return handle;
    }

    @Override
    public String grantId() {
        AccessGrant grant = handle.grant();
        return grant == null ? "unknown" : grant.grantId();
    }

    @Override
    public String targetSystem() {
        AccessGrant grant = handle.grant();
        return grant == null ? "unknown" : grant.targetSystem();
    }

    @Override
    public long expiresAtEpochMillis() {
        AccessGrant grant = handle.grant();
        return grant == null ? 0L : grant.expiresAtEpochMillis();
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            handle.close();
        }
    }

    synchronized boolean isClosed() {
        return closed;
    }
}
