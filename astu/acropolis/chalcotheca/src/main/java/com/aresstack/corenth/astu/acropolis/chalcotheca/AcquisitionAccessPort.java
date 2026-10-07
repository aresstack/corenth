package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * Narrow port through which the archive counter prepares authenticated acquisitions
 * (#10 Slice 3, the Adyton station).
 *
 * <p>The counter calls it before every external acquisition that Tamias permits. An adapter in
 * the outer ring decides per source whether authentication is needed: unauthenticated sources
 * return {@link AcquisitionAccess#notRequired()} without contacting the vault; authenticated
 * sources obtain a capability through Adyton. Neither Chalcotheca nor Acropolis ever sees
 * secret material, credential references or protocol handles.
 */
public interface AcquisitionAccessPort {

    /**
     * Prepares access for one acquisition.
     *
     * @param request the acquisition to prepare; never {@code null}
     * @return the typed preparation result; never {@code null}
     */
    AcquisitionAccess prepare(AcquisitionAccessRequest request);

    /**
     * Returns a port for compositions without authenticated sources; it never requires
     * authentication.
     */
    static AcquisitionAccessPort unauthenticated() {
        return new AcquisitionAccessPort() {
            @Override
            public AcquisitionAccess prepare(AcquisitionAccessRequest request) {
                return AcquisitionAccess.notRequired();
            }
        };
    }
}
