package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import com.aresstack.corenth.astu.VirtualResourceKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts raw MVS listing names into logical MVS locations.
 */
public final class MvsListingMapper {

    public List<MvsListingEntry> mapNames(MvsLocation parent, List<String> names) {
        if (parent == null) {
            throw new IllegalArgumentException("parent must not be null");
        }
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<MvsListingEntry> entries = new ArrayList<MvsListingEntry>();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            MvsLocation child = parent.createChild(name);
            VirtualResourceKind kind = child.isDirectory()
                    ? VirtualResourceKind.DIRECTORY
                    : VirtualResourceKind.FILE;
            entries.add(new MvsListingEntry(child, kind));
        }
        return entries;
    }
}
