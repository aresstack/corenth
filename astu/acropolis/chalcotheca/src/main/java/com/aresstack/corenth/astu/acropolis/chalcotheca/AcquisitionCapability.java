package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * An opaque, short-lived capability that authorises one external acquisition.
 *
 * <p>The archive counter obtains a capability from the {@link AcquisitionAccessPort} when an
 * acquisition needs authentication and hands it to the {@link AcquisitionPort} unchanged. The
 * capability describes the grant (identifier, target, expiry) but never exposes secret
 * material, passwords or authorisation headers; how it authenticates is known only to the
 * outer adapter that created it (#10 Slice 3).
 *
 * <p>The counter closes every capability it receives, also when the acquisition fails.
 */
public interface AcquisitionCapability extends AutoCloseable {

    /** Returns the opaque grant identifier; never secret material. */
    String grantId();

    /** Returns the target system this capability authorises access to. */
    String targetSystem();

    /** Returns the epoch millis after which the capability is no longer valid. */
    long expiresAtEpochMillis();

    /** Releases the capability and any authenticated session behind it. */
    @Override
    void close();
}
