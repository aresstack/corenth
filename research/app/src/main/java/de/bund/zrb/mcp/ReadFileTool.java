package de.bund.zrb.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.bund.zrb.files.api.FileServiceErrorCode;
import de.bund.zrb.files.api.FileService;
import de.bund.zrb.files.api.FileServiceException;
import de.bund.zrb.files.auth.CredentialsProvider;
import de.bund.zrb.files.impl.auth.LoginManagerCredentialsProvider;
import de.bund.zrb.files.impl.factory.FileServiceFactory;
import de.bund.zrb.files.model.FileNode;
import de.bund.zrb.files.model.FilePayload;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.login.LoginManager;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ndv.NdvObjectInfo;
import de.bund.zrb.ndv.NdvService;
import de.bund.zrb.service.NdvSourceCacheService;
import de.bund.zrb.ui.VirtualResource;
import de.bund.zrb.ui.VirtualResourceKind;
import de.bund.zrb.ui.VirtualResourceResolver;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool that reads file content or directory listings without opening a tab.
 * Returns the data as JSON to the bot.
 */
public class ReadFileTool implements McpTool {

    private final MainframeContext context;
    private static final int NDV_DEFAULT_PORT = 8011;
    private static final List<String> NDV_AUTH_ERROR_PATTERNS = Arrays.asList(
            "login", "auth", "passwort", "password", "nat0873", "nat7734"
    );
    private static final List<String> NDV_NOT_FOUND_ERROR_PATTERNS = Arrays.asList(
            "not found", "nicht gefunden", "nat0082", "nat3048"
    );

    public ReadFileTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("path", new ToolSpec.Property("string",
                "Pfad zur Ressource (Datei, Verzeichnis, Mail etc.). "
                + "Akzeptiert: lokale Pfade (C:\\...), FTP-Pfade (ftp:/...), "
                + "oder URIs mit Prefix (local://..., ftp://..., mail://..., ndv://...) "
                + "wie sie von search_index zur\u00fcckgegeben werden."));
        properties.put("maxLines", new ToolSpec.Property("integer", "Maximale Anzahl Zeilen (optional, default: unbegrenzt)"));
        properties.put("encoding", new ToolSpec.Property("string", "Zeichenkodierung (optional, default: System-Default)"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("path"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("path", "local://C:\\TEST\\datei.txt");
        example.put("maxLines", 100);

        return new ToolSpec(
                "read_resource",
                "Liest den Inhalt einer Ressource (Datei, Verzeichnis, Mail, NDV-Quelle) ohne einen Tab zu \u00f6ffnen. " +
                "Akzeptiert Pfade mit Prefix (local://, ftp:, mail://, ndv://) " +
                "wie sie von search_index zur\u00fcckgegeben werden, sowie direkte lokale/FTP-Pfade. " +
                "Bei Dateien wird der Textinhalt zur\u00fcckgegeben, bei Verzeichnissen eine Liste der Eintr\u00e4ge.",
                inputSchema,
                example
        );
    }

    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            if (input == null || !input.has("path") || input.get("path").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Pflichtfeld fehlt: path");
                return new McpToolResponse(response, resultVar, null);
            }

            String path = input.get("path").getAsString();
            Integer maxLines = input.has("maxLines") && !input.get("maxLines").isJsonNull()
                    ? input.get("maxLines").getAsInt() : null;
            String encoding = input.has("encoding") && !input.get("encoding").isJsonNull()
                    ? input.get("encoding").getAsString() : null;

            // Handle mail:// paths directly (not resolvable via VirtualResourceResolver)
            de.bund.zrb.files.path.VirtualResourceRef ref = de.bund.zrb.files.path.VirtualResourceRef.of(path);
            if (ref.isMailPath()) {
                return readMailResource(ref.getMailPath(), maxLines, resultVar);
            }
            if (ref.isNdvPath()) {
                return readNdvResource(ref.getNdvPath(), maxLines, resultVar);
            }

            // Resolve the resource
            VirtualResourceResolver resolver = new VirtualResourceResolver();
            VirtualResource resource = resolver.resolve(path);

            // Create FileService
            CredentialsProvider credentialsProvider = new LoginManagerCredentialsProvider(
                    (host, user) -> LoginManager.getInstance().getCachedPassword(host, user)
            );

            try (FileService fs = new FileServiceFactory().create(resource, credentialsProvider)) {
                if (resource.getKind() == VirtualResourceKind.DIRECTORY) {
                    return readDirectory(fs, resource, resultVar);
                } else {
                    return readFile(fs, resource, maxLines, encoding, resultVar);
                }
            }

        } catch (FileServiceException e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            response.addProperty("errorCode", e.getErrorCode() != null ? e.getErrorCode().name() : "UNKNOWN");
            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }

    private McpToolResponse readDirectory(FileService fs, VirtualResource resource, String resultVar)
            throws FileServiceException {
        JsonObject response = new JsonObject();

        List<FileNode> entries = fs.list(resource.getResolvedPath());

        JsonArray entriesArray = new JsonArray();
        for (FileNode node : entries) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", node.getName());
            entry.addProperty("path", node.getPath());
            entry.addProperty("isDirectory", node.isDirectory());
            entry.addProperty("size", node.getSize());
            entry.addProperty("lastModified", node.getLastModifiedMillis());
            entriesArray.add(entry);
        }

        response.addProperty("status", "success");
        response.addProperty("typSchluessel", "directory");
        response.addProperty("path", resource.getResolvedPath());
        response.addProperty("local", resource.isLocal());
        response.addProperty("entryCount", entries.size());
        response.add("entries", entriesArray);

        return new McpToolResponse(response, resultVar, null);
    }

    private McpToolResponse readFile(FileService fs, VirtualResource resource,
                                     Integer maxLines, String encoding, String resultVar)
            throws FileServiceException {
        JsonObject response = new JsonObject();

        FilePayload payload = fs.readFile(resource.getResolvedPath());

        Charset charset;
        if (encoding != null && !encoding.trim().isEmpty()) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception e) {
                charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
            }
        } else {
            charset = payload.getCharset() != null ? payload.getCharset() : Charset.defaultCharset();
        }

        // IMPORTANT: Use getEditorText() for proper RECORD_STRUCTURE handling
        String content = payload.getEditorText();

        // Apply maxLines limit if specified
        int lineCount = countLines(content);
        boolean truncated = false;
        if (maxLines != null && maxLines > 0 && lineCount > maxLines) {
            content = truncateToLines(content, maxLines);
            truncated = true;
        }

        response.addProperty("status", "success");
        response.addProperty("typSchluessel", "file");
        response.addProperty("path", resource.getResolvedPath());
        response.addProperty("local", resource.isLocal());
        response.addProperty("encoding", charset.name());
        response.addProperty("size", payload.getBytes().length);
        response.addProperty("lineCount", lineCount);
        response.addProperty("truncated", truncated);
        if (truncated) {
            response.addProperty("maxLinesApplied", maxLines);
        }
        response.addProperty("content", content);

        return new McpToolResponse(response, resultVar, null);
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private String truncateToLines(String content, int maxLines) {
        if (content == null || content.isEmpty() || maxLines <= 0) {
            return content;
        }

        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            sb.append(c);
            if (c == '\n') {
                lineCount++;
                if (lineCount >= maxLines) {
                    sb.append("\n... [truncated after ").append(maxLines).append(" lines]");
                    break;
                }
            }
        }
        return sb.toString();
    }

    McpToolResponse readNdvResource(String ndvPath, Integer maxLines, String resultVar) throws FileServiceException {
        Settings settings = SettingsHelper.load();
        String host = trimToNull(settings.host);
        String user = trimToNull(settings.user);
        int port = settings.ndvPort > 0 ? settings.ndvPort : NDV_DEFAULT_PORT;

        if (host == null || user == null) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED,
                    "NDV-Zugangsdaten fehlen. Bitte Server-Einstellungen (Host/Benutzer) konfigurieren.");
        }

        String password = LoginManager.getInstance().getCachedPassword(host, user);
        if (isNullOrBlank(password)) {
            throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED,
                    "Keine gecachten NDV-Zugangsdaten vorhanden. Bitte zuerst bei NDV anmelden.");
        }

        NdvService ndvService = new NdvService();
        NdvService.ResolvedNdvPath resolved = ndvService.resolvePath(ndvPath);
        if (!resolved.isFile()) {
            if (resolved.isDirectory()) {
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "NDV-Pfad verweist auf eine Bibliothek, nicht auf eine Quelle: " + ndvPath);
            }
            throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                    "Ungültiger NDV-Pfad: " + ndvPath);
        }

        final String library = resolved.getLibrary();
        NdvObjectInfo initialObjectInfo = resolved.getObjectInfo();
        if (library == null || library.isEmpty() || initialObjectInfo == null) {
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "NDV-Pfad konnte nicht aufgelöst werden: " + ndvPath);
        }

        try {
            ndvService.connect(host, port, user, password);
            LoginManager.getInstance().onLoginSuccess(host, user);

            NdvObjectInfo resolvedObjectInfo = initialObjectInfo;
            boolean hasServerMetadata = false;
            try {
                NdvObjectInfo probed = ndvService.findObject(library, initialObjectInfo.getEffectiveName());
                if (probed != null) {
                    resolvedObjectInfo = probed;
                    hasServerMetadata = true;
                }
            } catch (Exception probeError) {
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.TOOL)
                        .fine("[read_resource] NDV probe failed, fallback to parsed metadata: " + safeMessage(probeError));
            }

            String source;
            try {
                source = ndvService.readSource(library, resolvedObjectInfo);
            } catch (Exception readError) {
                if (isNdvAuthError(readError)) {
                    LoginManager.getInstance().invalidatePassword(host, user);
                    throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED,
                            "NDV-Authentifizierung fehlgeschlagen: " + safeMessage(readError), readError);
                }
                if (isNdvNotFoundError(readError)) {
                    throw new FileServiceException(FileServiceErrorCode.NOT_FOUND,
                            "NDV-Objekt nicht gefunden: " + ndvPath, readError);
                }
                throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                        "NDV-Quelle konnte nicht gelesen werden: " + safeMessage(readError), readError);
            }

            if (source == null) {
                source = "";
            }

            NdvSourceCacheService.getInstance().cacheSource(
                    library,
                    resolvedObjectInfo.getEffectiveName(),
                    resolvedObjectInfo.getTypeExtension(),
                    source,
                    hasServerMetadata ? resolvedObjectInfo.getSourceSize() : -1,
                    hasServerMetadata ? resolvedObjectInfo.getSourceDate() : null
            );

            String resolvedPath = library + "/" + resolvedObjectInfo.getEffectiveName()
                    + (resolvedObjectInfo.getTypeExtension().isEmpty() ? "" : "." + resolvedObjectInfo.getTypeExtension());
            String content = source;
            int lineCount = countLines(content);
            boolean truncated = false;
            if (maxLines != null && maxLines > 0 && lineCount > maxLines) {
                content = truncateToLines(content, maxLines);
                truncated = true;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("typSchluessel", "file");
            response.addProperty("path", de.bund.zrb.files.path.VirtualResourceRef.NDV_PREFIX + resolvedPath);
            response.addProperty("local", false);
            response.addProperty("backend", "NDV");
            response.addProperty("encoding", StandardCharsets.UTF_8.name());
            response.addProperty("size", source.getBytes(StandardCharsets.UTF_8).length);
            response.addProperty("lineCount", lineCount);
            response.addProperty("truncated", truncated);
            if (truncated) {
                response.addProperty("maxLinesApplied", maxLines);
            }
            response.addProperty("content", content);
            return new McpToolResponse(response, resultVar, null);
        } catch (FileServiceException e) {
            throw e;
        } catch (Exception e) {
            if (isNdvAuthError(e)) {
                LoginManager.getInstance().invalidatePassword(host, user);
                throw new FileServiceException(FileServiceErrorCode.AUTH_FAILED,
                        "NDV-Authentifizierung fehlgeschlagen: " + safeMessage(e), e);
            }
            throw new FileServiceException(FileServiceErrorCode.IO_ERROR,
                    "NDV-Verbindung fehlgeschlagen: " + safeMessage(e), e);
        } finally {
            try {
                ndvService.close();
            } catch (IOException closeError) {
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.TOOL)
                        .fine("[read_resource] NDV close failed: " + safeMessage(closeError));
            }
        }
    }

    private static boolean isNdvAuthError(Throwable error) {
        return containsAnyPattern(safeMessage(error), NDV_AUTH_ERROR_PATTERNS);
    }

    private static boolean isNdvNotFoundError(Throwable error) {
        return containsAnyPattern(safeMessage(error), NDV_NOT_FOUND_ERROR_PATTERNS);
    }

    private static boolean containsAnyPattern(String value, List<String> patterns) {
        String lowerValue = value == null ? "" : value.toLowerCase();
        for (String pattern : patterns) {
            if (lowerValue.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullOrBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Trims a string and returns null when the input is null or only whitespace.
     */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return (message == null || message.trim().isEmpty()) ? error.getClass().getSimpleName() : message;
    }

    /**
     * Read a mail resource. mailPath format: "mailboxPath#folderPath#descriptorNodeId"
     */
    private McpToolResponse readMailResource(String mailPath, Integer maxLines, String resultVar) {
        JsonObject response = new JsonObject();
        try {
            String[] parts = mailPath.split("#", 3);
            if (parts.length < 3) {
                response.addProperty("status", "error");
                response.addProperty("message", "Ungültiges Mail-Pfad-Format: " + mailPath);
                return new McpToolResponse(response, resultVar, null);
            }

            String mailboxPath = parts[0];
            String folderPath = parts[1];
            long nodeId = Long.parseLong(parts[2]);

            de.bund.zrb.mail.infrastructure.PstMailboxReader reader =
                    new de.bund.zrb.mail.infrastructure.PstMailboxReader();
            de.bund.zrb.mail.model.MailMessageContent content = reader.readMessage(mailboxPath, folderPath, nodeId);

            String text = content.toMarkdown();
            if (maxLines != null && maxLines > 0) {
                text = truncateToLines(text, maxLines);
            }

            response.addProperty("status", "success");
            response.addProperty("path", "mail://" + mailPath);
            response.addProperty("kind", "MAIL");
            response.addProperty("content", text);
            response.addProperty("lineCount", text.split("\n", -1).length);

            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }
}
