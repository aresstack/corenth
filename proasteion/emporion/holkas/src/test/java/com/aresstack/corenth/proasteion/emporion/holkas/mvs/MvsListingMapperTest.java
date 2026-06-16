package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import com.aresstack.corenth.astu.VirtualResourceKind;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MvsListingMapperTest {

    @Test
    public void mapsHlqChildrenToQualifierContexts() {
        MvsListingMapper mapper = new MvsListingMapper();

        List<MvsListingEntry> entries = mapper.mapNames(MvsLocation.hlq("USERID"),
                Arrays.asList("DATA", "USERID.SOURCE"));

        assertEquals(2, entries.size());
        assertEquals("'USERID.DATA'", entries.get(0).location().logicalPath());
        assertEquals(VirtualResourceKind.DIRECTORY, entries.get(0).kind());
        assertEquals("'USERID.SOURCE'", entries.get(1).location().logicalPath());
    }

    @Test
    public void mapsDatasetChildrenToMembers() {
        MvsListingMapper mapper = new MvsListingMapper();

        List<MvsListingEntry> entries = mapper.mapNames(MvsLocation.dataset("USERID.PDS"),
                Arrays.asList("MEMBER1"));

        assertEquals(1, entries.size());
        assertEquals("'USERID.PDS(MEMBER1)'", entries.get(0).location().logicalPath());
        assertEquals(VirtualResourceKind.FILE, entries.get(0).kind());
        assertEquals("MEMBER1", entries.get(0).name());
    }
}
