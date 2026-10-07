package com.aresstack.corenth.astu.propylaea;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper that renders structures as stable, readable lines and loads fixtures.
 */
public final class StructureText {

    private StructureText() {
    }

    public static VirtualResourceRef ref(String path) {
        return new VirtualResourceRef(BookmarkUri.parse("file:///fixtures/" + path), VirtualResourceKind.FILE);
    }

    public static ParsingRequest request(String unitName, SourceLanguage language, String text) {
        return new ParsingRequest(ref(unitName), unitName, language, text);
    }

    /** Renders each relation as {@code L<line> <from-kind> <from> <KIND> <binding> <target-kind> <target> via <statement>}. */
    public static List<String> relations(ProgramStructure structure) {
        List<String> rendered = new ArrayList<String>();
        for (CodeRelation relation : structure.relations()) {
            CodeComponentRef target = relation.target();
            rendered.add("L" + relation.location().startLine() + " " + relation.from().kind() + " " + relation.from().name()
                    + " " + relation.kind() + " " + target.binding() + " " + target.expectedKind() + " " + target.name()
                    + " via " + relation.statement());
        }
        return rendered;
    }

    /** Renders each component as {@code <KIND> <name> <location>}. */
    public static List<String> components(ProgramStructure structure) {
        List<String> rendered = new ArrayList<String>();
        for (CodeComponent component : structure.components()) {
            rendered.add(component.kind() + " " + component.name() + " " + component.location());
        }
        return rendered;
    }

    /** Renders each diagnostic as {@code <CODE> L<line>}. */
    public static List<String> diagnostics(ProgramStructure structure) {
        List<String> rendered = new ArrayList<String>();
        for (SourceDiagnostic diagnostic : structure.diagnostics()) {
            rendered.add(diagnostic.code() + " L" + diagnostic.location().startLine());
        }
        return rendered;
    }

    public static String fixture(Class<?> anchor, String name) {
        InputStream in = anchor.getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("missing fixture " + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // Ignore close failures of test resources.
            }
        }
    }
}
