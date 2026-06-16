package com.aresstack.corenth.astu;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level architecture guards for the Corenth city boundaries.
 */
class ArchitectureBoundaryTest {

    @Test
    void innerCityDoesNotImportOuterRing() throws Exception {
        List<File> roots = Arrays.asList(
                module("adyton"),
                module("astu"));

        List<String> violations = importsMatching(roots,
                "com.aresstack.corenth.proasteion");

        assertTrue(violations.isEmpty(), formatViolations(
                "Inner modules must not import the outer adapter ring.", violations));
    }

    @Test
    void coreModulesDoNotImportSwingOrExedra() throws Exception {
        List<File> roots = Arrays.asList(
                module("adyton"),
                module("astu"));

        List<String> violations = new ArrayList<String>();
        violations.addAll(importsMatching(roots, "javax.swing"));
        violations.addAll(importsMatching(roots, "java.awt"));
        violations.addAll(importsMatching(roots, "com.aresstack.corenth.proasteion.exedra"));

        assertTrue(violations.isEmpty(), formatViolations(
                "Core modules must not depend on Swing or Exedra.", violations));
    }

    @Test
    void holkasDoesNotDependOnExedraOrSwing() throws Exception {
        List<File> roots = Arrays.asList(module("proasteion/emporion/holkas"));

        List<String> violations = new ArrayList<String>();
        violations.addAll(importsMatching(roots, "javax.swing"));
        violations.addAll(importsMatching(roots, "java.awt"));
        violations.addAll(importsMatching(roots, "com.aresstack.corenth.proasteion.exedra"));

        assertTrue(violations.isEmpty(), formatViolations(
                "Holkas must stay transport-focused and must not depend on UI code.", violations));
    }

    @Test
    void clientAdaptersDoNotImportHolkasDirectly() throws Exception {
        List<File> roots = Arrays.asList(
                module("proasteion/exedra"),
                module("proasteion/katagogion"));

        List<String> violations = importsMatching(roots,
                "com.aresstack.corenth.proasteion.emporion.holkas");

        assertTrue(violations.isEmpty(), formatViolations(
                "Client-facing adapters must not bypass mediated resource access by importing Holkas directly.", violations));
    }

    private File module(String relativePath) {
        return new File(projectRoot(), relativePath);
    }

    private File projectRoot() {
        File current = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (current != null) {
            if (new File(current, "settings.gradle").isFile()) {
                return current;
            }
            current = current.getParentFile();
        }
        throw new IllegalStateException("Could not find project root from user.dir");
    }

    private List<String> importsMatching(List<File> moduleRoots, String forbiddenPrefix) throws IOException {
        List<String> violations = new ArrayList<String>();
        for (File moduleRoot : moduleRoots) {
            File sourceRoot = new File(moduleRoot, "src/main/java");
            collectImportViolations(sourceRoot, forbiddenPrefix, violations);
        }
        return violations;
    }

    private void collectImportViolations(File file, String forbiddenPrefix, List<String> violations) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectImportViolations(child, forbiddenPrefix, violations);
            }
            return;
        }
        if (!file.getName().endsWith(".java")) {
            return;
        }
        inspectJavaImports(file, forbiddenPrefix, violations);
    }

    private void inspectJavaImports(File file, String forbiddenPrefix, List<String> violations) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.startsWith("import ") && trimmed.contains(forbiddenPrefix)) {
                    violations.add(relativePath(file) + ":" + lineNumber + " -> " + trimmed);
                }
            }
        } finally {
            reader.close();
        }
    }

    private String relativePath(File file) {
        String root = projectRoot().getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(root)) {
            path = path.substring(root.length());
            if (path.startsWith(File.separator)) {
                path = path.substring(1);
            }
        }
        return path.replace(File.separatorChar, '/');
    }

    private String formatViolations(String message, List<String> violations) {
        StringBuilder builder = new StringBuilder(message);
        for (String violation : violations) {
            builder.append('\n').append(" - ").append(violation);
        }
        return builder.toString();
    }
}
