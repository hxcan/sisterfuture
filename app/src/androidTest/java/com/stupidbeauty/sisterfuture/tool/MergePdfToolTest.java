package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MergePdfToolTest {
    private File directory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PDFBoxResourceLoader.init(context);
        return Files.createTempDirectory(context.getCacheDir().toPath(), "merge-pdf-test-").toFile();
    }
    private File createPdf(File directory, String name, String text, float width) throws Exception {
        File file = new File(directory, name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(width, 400));
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(20, 100);
                content.showText(text);
                content.endText();
            }
            doc.save(file);
        }
        return file;
    }
    @Test public void preservesOrderTextDimensionsAndSources() throws Exception {
        File dir = directory();
        File first = createPdf(dir, "one.pdf", "FIRST", 200);
        File second = createPdf(dir, "two.pdf", "SECOND", 300);
        byte[] original = Files.readAllBytes(first.toPath());
        File output = new File(dir, "merged.pdf");
        assertEquals(2, MergePdfTool.mergeFiles(Arrays.asList(second, first), output, dir));
        try (PDDocument merged = PDDocument.load(output)) {
            assertEquals(300f, merged.getPage(0).getMediaBox().getWidth(), 0.01f);
            assertEquals(200f, merged.getPage(1).getMediaBox().getWidth(), 0.01f);
            String text = new PDFTextStripper().getText(merged);
            assertTrue(text.contains("FIRST") && text.indexOf("SECOND") < text.indexOf("FIRST"));
        }
        assertArrayEquals(original, Files.readAllBytes(first.toPath()));
        assertTrue(second.isFile());
    }
    @Test public void malformedSourceLeavesNoOutputOrTemporaryFile() throws Exception {
        File dir = directory();
        File first = createPdf(dir, "one.pdf", "FIRST", 200);
        File broken = new File(dir, "broken.pdf");
        assertTrue(broken.createNewFile());
        File output = new File(dir, "merged.pdf");
        try { MergePdfTool.mergeFiles(Arrays.asList(first, broken), output, dir); fail(); }
        catch (java.io.IOException expected) { }
        assertFalse(output.exists());
        assertEquals(2, dir.list().length);
    }
    @Test public void refusesToOverwriteExistingFileIncludingSource() throws Exception {
        File dir = directory();
        File first = createPdf(dir, "one.pdf", "FIRST", 200);
        File second = createPdf(dir, "two.pdf", "SECOND", 300);
        byte[] original = Files.readAllBytes(first.toPath());
        try { MergePdfTool.mergeFiles(Arrays.asList(first, second), first, dir); fail(); }
        catch (java.io.IOException expected) { }
        assertArrayEquals(original, Files.readAllBytes(first.toPath()));
    }
}
