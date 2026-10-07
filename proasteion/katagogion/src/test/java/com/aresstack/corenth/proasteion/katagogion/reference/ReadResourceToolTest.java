package com.aresstack.corenth.proasteion.katagogion.reference;

import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.katagogion.AllowlistToolAdmissionPolicy;
import com.aresstack.corenth.proasteion.katagogion.MediatedReading;
import com.aresstack.corenth.proasteion.katagogion.ReadOutcome;
import com.aresstack.corenth.proasteion.katagogion.SearchOutcome;
import com.aresstack.corenth.proasteion.katagogion.LexicalSearch;
import com.aresstack.corenth.proasteion.katagogion.ToolCapabilities;
import com.aresstack.corenth.proasteion.katagogion.ToolCapability;
import com.aresstack.corenth.proasteion.katagogion.ToolFailureReason;
import com.aresstack.corenth.proasteion.katagogion.ToolHost;
import com.aresstack.corenth.proasteion.katagogion.ToolInvocation;
import com.aresstack.corenth.proasteion.katagogion.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract coverage of {@link ReadResourceTool} against a fake {@link MediatedReading}.
 *
 * <p>The fake stands in for the coordinator-owned binding to {@code MediatedResourceAccess}; it is
 * not evidence of a production reading path.
 */
class ReadResourceToolTest {

    private final List<VirtualResourceRef> requested = new ArrayList<VirtualResourceRef>();

    private final MediatedReading fakeReading = new MediatedReading() {
        @Override
        public ReadOutcome read(VirtualResourceRef resourceRef) {
            requested.add(resourceRef);
            String path = resourceRef.uri().toString();
            if (path.contains("denied")) {
                return ReadOutcome.denied("outside configured roots");
            }
            if (path.contains("gone")) {
                return ReadOutcome.unavailable("acquisition failed");
            }
            return ReadOutcome.content("hello from " + path, "text/plain");
        }
    };

    private final ToolHost host = createHost();

    private ToolHost createHost() {
        ToolHost created = new ToolHost(AllowlistToolAdmissionPolicy.builder()
                .allow(CorenthReferencePlugin.ID, EnumSet.allOf(ToolCapability.class)).build(),
                ToolCapabilities.builder()
                        .lexicalSearch(new LexicalSearch() {
                            @Override
                            public SearchOutcome search(String queryText, int maxHits) {
                                return SearchOutcome.hits(null);
                            }
                        })
                        .mediatedReading(fakeReading)
                        .build());
        assertTrue(created.install(CorenthReferencePlugin.all()).isInstalled());
        return created;
    }

    @Test
    void readsContentThroughTheCapability() {
        ToolResult result = host.invoke(read("file:///work/a.txt", null));

        assertTrue(result.isSuccess());
        assertEquals("hello from file:///work/a.txt", result.text());
        assertEquals("text/plain", result.records().get(0).get("contentType"));
        assertEquals(VirtualResourceKind.FILE, requested.get(0).kind());
    }

    @Test
    void denialBecomesAccessDeniedWithoutContent() {
        ToolResult result = host.invoke(read("file:///work/denied.txt", null));

        assertEquals(ToolFailureReason.ACCESS_DENIED, result.failureReason());
        assertEquals("outside configured roots", result.text());
        assertTrue(result.records().isEmpty());
    }

    @Test
    void unavailabilityIsTyped() {
        assertEquals(ToolFailureReason.UNAVAILABLE, host.invoke(read("file:///work/gone.txt", null)).failureReason());
    }

    @Test
    void kindIsParsedAndMalformedInputIsRejectedBeforeReading() {
        assertTrue(host.invoke(read("file:///work", "directory")).isSuccess());
        assertEquals(VirtualResourceKind.DIRECTORY, requested.get(0).kind());

        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(read("no-scheme", null)).failureReason());
        assertEquals(ToolFailureReason.INVALID_ARGUMENTS, host.invoke(read("file:///x", "socket")).failureReason());
        assertEquals(1, requested.size());
    }

    private static ToolInvocation read(String uri, String kind) {
        Map<String, String> arguments = new LinkedHashMap<String, String>();
        arguments.put("uri", uri);
        if (kind != null) {
            arguments.put("kind", kind);
        }
        return new ToolInvocation(ReadResourceTool.NAME, arguments);
    }
}
