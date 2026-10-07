package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.ProcessingResult;
import com.aresstack.corenth.astu.acropolis.ResourceProcessingStep;
import com.aresstack.corenth.astu.acropolis.ResourceProcessingStepType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance of the local core path through the productive composition (#10 Slice 5, #42):
 * local text, Markdown, HTML, PDF, DOCX and XLSX files reach lexical search; unchanged content
 * is neither extracted nor indexed again; changed sources are re-acquired when the policy permits
 * refresh; deleted and excluded resources are withdrawn from the index.
 *
 * <p>Everything below is the {@link CorenthComposition} output against real local files; it does
 * not cover authenticated or remote sources.
 */
public class LocalCorePathAcceptanceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path root;
    private Path indexDirectory;
    private final List<CorenthApplication> opened = new ArrayList<CorenthApplication>();

    @Before
    public void setUp() throws IOException {
        root = tempFolder.newFolder("documents").toPath();
        indexDirectory = tempFolder.newFolder("index").toPath();
    }

    @After
    public void tearDown() throws IOException {
        for (CorenthApplication application : opened) {
            application.close();
        }
    }

    @Test
    public void everySupportedLocalFormat_reachesLexicalSearch() throws IOException {
        Path txt = write("notes.txt", text("Plaintextterm stands in a plain text note."));
        Path md = write("guide.md", text("# Guide\n\nMarkdownterm stands in a Markdown guide."));
        Path html = write("page.html", text("<!doctype html><html><head><title>Page</title></head>"
                + "<body><p>Htmlterm stands in an HTML page.</p><script>scriptonlyterm()</script></body></html>"));
        Path pdf = write("report.pdf", pdf("Pdfterm stands in a PDF report."));
        Path docx = write("letter.docx", docx("Docxterm stands in a Word letter."));
        Path xlsx = write("sheet.xlsx", xlsx("Xlsxterm"));
        CorenthApplication application = compose(settings().build());

        for (Path file : new Path[] {txt, md, html, pdf, docx, xlsx}) {
            ProcessingResult result = application.resourceLifecycle().process(ref(file));
            assertEquals(file + ": " + result.message(), ProcessingResult.Status.INDEXED, result.status());
        }

        assertFound(application, "plaintextterm", txt);
        assertFound(application, "markdownterm", md);
        assertFound(application, "htmlterm", html);
        assertFound(application, "pdfterm", pdf);
        assertFound(application, "docxterm", docx);
        assertFound(application, "xlsxterm", xlsx);
        assertTrue("script content is not indexed", application.search().search("scriptonlyterm", 10).isEmpty());
    }

    @Test
    public void unchangedContent_isNeitherExtractedNorIndexedAgain() throws IOException {
        Path pdf = write("stable.pdf", pdf("Stableterm stays the same."));
        CorenthApplication application = compose(settings().build());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(pdf)).status());

        ProcessingResult second = application.resourceLifecycle().process(ref(pdf));

        assertEquals(ProcessingResult.Status.UNCHANGED, second.status());
        assertFalse(executed(second, ResourceProcessingStepType.CONTENT_INSPECTION));
        assertFalse(executed(second, ResourceProcessingStepType.LEXICAL_INDEXING));
        assertFalse(executed(second, ResourceProcessingStepType.DERIVED_STATE_CLEANUP));
        assertFound(application, "stableterm", pdf);
    }

    @Test
    public void changedSource_isReacquiredAndReindexed_withDefaultRefresh() throws IOException {
        Path note = write("mutable.txt", text("Firstversionterm is the original."));
        CorenthApplication application = compose(settings().build());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(note)).status());

        write("mutable.txt", text("Secondversionterm replaces it."));
        ProcessingResult second = application.resourceLifecycle().process(ref(note));

        assertEquals(ProcessingResult.Status.INDEXED, second.status());
        assertFound(application, "secondversionterm", note);
        assertTrue(application.search().search("firstversionterm", 10).isEmpty());
    }

    @Test
    public void changedSource_staysCached_whenTheHostDisablesRefresh() throws IOException {
        Path note = write("cached.txt", text("Cachedfirstterm is the original."));
        CorenthApplication application = compose(settings().sourceRefresh(false).build());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(note)).status());

        write("cached.txt", text("Cachedsecondterm replaces it."));

        assertEquals(ProcessingResult.Status.UNCHANGED, application.resourceLifecycle().process(ref(note)).status());
        assertTrue(application.search().search("cachedsecondterm", 10).isEmpty());
    }

    @Test
    public void deletedSource_isWithdrawnFromTheIndex_andReindexedWhenItReturns() throws IOException {
        Path note = write("vanishing.txt", text("Vanishingterm will be deleted."));
        CorenthApplication application = compose(settings().build());
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(note)).status());

        Files.delete(note);
        ProcessingResult removed = application.resourceLifecycle().process(ref(note));

        assertEquals(ProcessingResult.Status.REMOVED, removed.status());
        assertTrue(application.search().search("vanishingterm", 10).isEmpty());

        write("vanishing.txt", text("Vanishingterm will be deleted."));
        assertEquals(ProcessingResult.Status.INDEXED, application.resourceLifecycle().process(ref(note)).status());
        assertFound(application, "vanishingterm", note);
    }

    @Test
    public void excludedResource_isWithdrawnFromThePersistentIndex() throws IOException {
        Path draft = write("drafts/plan.txt", text("Draftterm was indexed before the exclusion."));
        CorenthApplication before = new CorenthComposition().composeLocal(settings().build());
        assertEquals(ProcessingResult.Status.INDEXED, before.resourceLifecycle().process(ref(draft)).status());
        before.close();

        CorenthApplication after = compose(settings().excludedPattern("**/drafts/**").build());
        assertEquals(1, after.search().search("draftterm", 10).size());
        ProcessingResult excluded = after.resourceLifecycle().process(ref(draft));

        assertEquals(ProcessingResult.Status.DENIED, excluded.status());
        assertTrue(after.search().search("draftterm", 10).isEmpty());
    }

    @Test
    public void oversizedFile_isDeniedBeforeItsPayloadIsAcquired() throws IOException {
        Path big = write("big.txt", text("Oversizedterm is longer than the configured limit."));
        CorenthApplication application = compose(settings().maxIndexedBytes(16).build());

        ProcessingResult result = application.resourceLifecycle().process(ref(big));

        assertEquals(ProcessingResult.Status.DENIED, result.status());
        assertTrue(result.message(), result.message().startsWith("SIZE_OVER_LIMIT"));
        assertFalse("the size comes from source metadata",
                executed(result, ResourceProcessingStepType.MEDIATED_ACQUISITION));
        assertTrue(application.search().search("oversizedterm", 10).isEmpty());
    }

    private static void assertFound(CorenthApplication application, String term, Path file) throws IOException {
        assertEquals(term, 1, application.search().search(term, 10).size());
        assertEquals(term, ref(file).uri(), application.search().search(term, 10).get(0).resourceRef().uri());
    }

    private static boolean executed(ProcessingResult result, ResourceProcessingStepType type) {
        for (ResourceProcessingStep step : result.steps()) {
            if (step.type() == type) {
                return true;
            }
        }
        return false;
    }

    private ApplicationSettings.Builder settings() {
        return ApplicationSettings.builder().indexDirectory(indexDirectory).accessibleRoot(root);
    }

    private CorenthApplication compose(ApplicationSettings settings) throws IOException {
        CorenthApplication application = new CorenthComposition().composeLocal(settings);
        opened.add(application);
        return application;
    }

    private Path write(String relative, byte[] content) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.write(path, content);
    }

    private static VirtualResourceRef ref(Path path) {
        return new VirtualResourceRef(BookmarkUri.parse(path.toUri().toString()), VirtualResourceKind.FILE);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pdf(String line) throws IOException {
        PDDocument document = new PDDocument();
        try {
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            try {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 700);
                content.showText(line);
                content.endText();
            } finally {
                content.close();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }

    private static byte[] docx(String paragraph) throws IOException {
        XWPFDocument document = new XWPFDocument();
        try {
            document.createParagraph().createRun().setText(paragraph);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }

    private static byte[] xlsx(String cellText) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            XSSFSheet sheet = workbook.createSheet("Data");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Count");
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue(cellText);
            row.createCell(1).setCellValue(3);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } finally {
            workbook.close();
        }
    }
}
