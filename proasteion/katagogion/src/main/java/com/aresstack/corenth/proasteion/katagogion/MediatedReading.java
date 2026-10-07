package com.aresstack.corenth.proasteion.katagogion;

import com.aresstack.corenth.astu.VirtualResourceRef;

/**
 * Tool capability for reading resource content through the mediated archive counter.
 *
 * <p>The acting identity is bound by the host when it builds this capability; a tool cannot
 * choose or change it. Implementations delegate to the Chalcotheca {@code MediatedResourceAccess}
 * contract so that Tamias decides every read; a denial surfaces as {@link ReadOutcome#denied},
 * never as content.
 */
public interface MediatedReading {

    /**
     * Read the textual content of a resource.
     *
     * @param resourceRef the resource to read
     * @return the typed outcome; never {@code null}
     */
    ReadOutcome read(VirtualResourceRef resourceRef);
}
