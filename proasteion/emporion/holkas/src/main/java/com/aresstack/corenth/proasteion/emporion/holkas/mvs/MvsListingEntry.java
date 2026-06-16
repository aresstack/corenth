package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import com.aresstack.corenth.astu.VirtualResourceKind;

/**
 * Parsed MVS listing entry independent of FTP client details.
 */
public final class MvsListingEntry {

    private final MvsLocation location;
    private final VirtualResourceKind kind;

    public MvsListingEntry(MvsLocation location, VirtualResourceKind kind) {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.location = location;
        this.kind = kind;
    }

    public MvsLocation location() {
        return location;
    }

    public String name() {
        return location.displayName();
    }

    public VirtualResourceKind kind() {
        return kind;
    }
}
