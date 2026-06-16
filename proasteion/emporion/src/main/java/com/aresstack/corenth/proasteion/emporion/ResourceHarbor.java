package com.aresstack.corenth.proasteion.emporion;

import com.aresstack.corenth.proasteion.emporion.holkas.ResourceListing;

/**
 * Harbor coordination port for raw acquisition and shallow inspection.
 */
public interface ResourceHarbor {

    /** Fetches a raw resource through Holkas and runs shallow Deigma extraction. */
    HarborResult<HarborInspection> inspect(HarborRequest request);

    /** Lists child resources through Holkas without parsing or indexing them. */
    HarborResult<ResourceListing> list(HarborRequest request);
}
