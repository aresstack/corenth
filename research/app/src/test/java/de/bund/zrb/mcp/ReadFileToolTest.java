package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadFileToolTest {

    private Settings originalSettings;

    @BeforeEach
    void setUp() {
        originalSettings = SettingsHelper.load();
        LoginManager.getInstance().clearCache();
    }

    @AfterEach
    void tearDown() {
        SettingsHelper.save(originalSettings);
        LoginManager.getInstance().clearCache();
    }

    @Test
    void execute_ndvPathDelegatesToNdvHandler() {
        TestableReadFileTool tool = new TestableReadFileTool();
        JsonObject input = new JsonObject();
        input.addProperty("path", "ndv://TEST/xyz.NSN");
        input.addProperty("maxLines", 25);

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertTrue(tool.ndvCalled);
        assertEquals("TEST/xyz.NSN", tool.capturedNdvPath);
        assertEquals(Integer.valueOf(25), tool.capturedMaxLines);
        assertEquals("success", json.get("status").getAsString());
        assertEquals("ndv://TEST/xyz.NSN", json.get("path").getAsString());
    }

    @Test
    void execute_ndvPathWithoutConfiguredCredentialsReturnsAuthFailed() {
        Settings settings = SettingsHelper.load();
        settings.host = "";
        settings.user = "";
        SettingsHelper.save(settings);

        ReadFileTool tool = new ReadFileTool(null);
        JsonObject input = new JsonObject();
        input.addProperty("path", "ndv://TEST/xyz.NSN");

        McpToolResponse response = tool.execute(input, null);
        JsonObject json = response.asJson();

        assertEquals("error", json.get("status").getAsString());
        assertEquals("AUTH_FAILED", json.get("errorCode").getAsString());
        assertTrue(json.get("message").getAsString().contains("NDV-Zugangsdaten"));
    }

    @Test
    void trimToNull_normalizesWhitespaceSettings() {
        assertEquals("host.example", ReadFileTool.trimToNull("  host.example  "));
        assertEquals("USER1", ReadFileTool.trimToNull("  USER1\t"));
        assertNull(ReadFileTool.trimToNull("   "));
        assertNull(ReadFileTool.trimToNull(null));
    }

    private static final class TestableReadFileTool extends ReadFileTool {
        private boolean ndvCalled;
        private String capturedNdvPath;
        private Integer capturedMaxLines;

        private TestableReadFileTool() {
            super(null);
        }

        @Override
        McpToolResponse readNdvResource(String ndvPath, Integer maxLines, String resultVar) throws FileServiceException {
            ndvCalled = true;
            capturedNdvPath = ndvPath;
            capturedMaxLines = maxLines;

            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("path", "ndv://" + ndvPath);
            return new McpToolResponse(response, resultVar, null);
        }
    }
}
