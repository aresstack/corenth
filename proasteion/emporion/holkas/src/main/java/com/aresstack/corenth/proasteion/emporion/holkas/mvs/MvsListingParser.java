package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import java.util.List;

/**
 * Compatibility name for converting MVS listing names into logical locations.
 */
public final class MvsListingParser {

    private final MvsListingMapper mapper = new MvsListingMapper();

    public List<MvsListingEntry> parseNames(MvsLocation parent, List<String> names) {
        return mapper.mapNames(parent, names);
    }
}
