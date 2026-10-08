package com.stupidbeauty.sisterfuture.tool;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.*;
import org.json.JSONObject;

public class ZipExtractorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File zip(String... names) throws Exception {
        File file = temp.newFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) out.write(new byte[]{1, 2, 3});
                out.closeEntry();
            }
        }
        return file;
    }
    private File target() { return new File(temp.getRoot(), "output"); }
    @Test public void extractsNestedUnicodeAndEmptyDirectory() throws Exception {
        File archive = zip("空目录/", "资料/测试.txt");
        byte[] original = Files.readAllBytes(archive.toPath());
        ZipExtractor.Result result = ZipExtractor.extract(archive, target());
        assertEquals(1, result.files); assertEquals(3, result.bytes);
        assertTrue(new File(target(), "空目录").isDirectory());
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(new File(target(), "资料/测试.txt").toPath()));
        assertArrayEquals(original, Files.readAllBytes(archive.toPath()));
    }
    @Test public void rejectsTraversalAndCleansPartialOutput() throws Exception {
        for (String name : new String[]{"../escape", "/absolute", "..\\escape", "C:/escape"}) {
            try { ZipExtractor.extract(zip("good", name), target()); fail(); }
            catch (IOException expected) { assertFalse(target().exists()); }
        }
        assertFalse(new File(temp.getRoot(), "escape").exists());
    }
    @Test public void existingDirectoryUntouched() throws Exception {
        assertTrue(target().mkdir());
        File old = new File(target(), "old"); assertTrue(old.createNewFile());
        try { ZipExtractor.extract(zip("new"), target()); fail(); }
        catch (IOException expected) { assertTrue(old.exists()); }
    }
    @Test public void limitsActualBytesAndEntryCount() throws Exception {
        File archive = zip("a", "b");
        try { ZipExtractor.extract(archive, target(), 5, 10); fail(); }
        catch (IOException expected) { assertFalse(target().exists()); }
        try { ZipExtractor.extract(archive, target(), 100, 1); fail(); }
        catch (IOException expected) { assertFalse(target().exists()); }
    }
    @Test public void invalidArchiveDoesNotCreateDirectory() throws Exception {
        try { ZipExtractor.extract(temp.newFile(), target()); fail(); }
        catch (IOException expected) { assertFalse(target().exists()); }
    }
    @Test public void conflictingEntriesAreRejected() throws Exception {
        try { ZipExtractor.extract(zip("a", "a/b"), target()); fail(); }
        catch (IOException expected) { assertFalse(target().exists()); }
    }
    @Test public void aliasesAndMalformedOptionalValue() throws Exception {
        JSONObject args = ExtractZipTool.parseArguments(new JSONObject().put("zip_path", "/a.zip")
            .put("output_directory", new JSONObject()));
        assertEquals("/a.zip", args.getString("zipPath"));
        assertFalse(args.has("outputDirectory"));
        args = ExtractZipTool.parseArguments(new JSONObject().put("zip_path", "/old.zip").put("zipPath", "/new.zip"));
        assertEquals("/new.zip", args.getString("zipPath"));
    }
    @Test public void invalidRequiredValueHasClearError() throws Exception {
        try { ExtractZipTool.parseArguments(new JSONObject().put("zipPath", new JSONObject())); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("缺少 zipPath", expected.getMessage()); }
    }
}
