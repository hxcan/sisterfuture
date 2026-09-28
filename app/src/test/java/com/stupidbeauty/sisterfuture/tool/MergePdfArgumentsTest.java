package com.stupidbeauty.sisterfuture.tool;

import java.io.File;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MergePdfArgumentsTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void preservesOrderAndAcceptsAliasesAndEncodedArray() throws Exception {
        File first = files.newFile("first.pdf");
        File second = files.newFile("second.pdf");
        JSONArray paths = new JSONArray().put(second.getAbsolutePath()).put(first.getAbsolutePath());
        JSONObject args = ToolParameterAliases.normalize(new JSONObject()
            .put("file_paths", paths.toString()).put("output_filename", "combined"), "filePaths", "outputFilename");
        List<File> inputs = MergePdfTool.parseInputs(args);
        assertEquals(second.getCanonicalFile(), inputs.get(0));
        assertEquals(first.getCanonicalFile(), inputs.get(1));
        assertEquals("combined.pdf", MergePdfTool.outputName(args));
    }

    @Test public void defaultsAreUniqueAndCannotEscapeOutputDirectory() throws Exception {
        assertNotEquals(MergePdfTool.outputName(new JSONObject()), MergePdfTool.outputName(new JSONObject()));
        for (String invalid : new String[]{"../bad", "/tmp/bad", "dir\\bad", ".", ".."}) {
            try {
                MergePdfTool.outputName(new JSONObject().put("outputFilename", invalid));
                fail("Path should be rejected");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void rejectsMissingAndNonLocalInputs() throws Exception {
        for (JSONObject args : new JSONObject[]{
            new JSONObject(),
            new JSONObject().put("filePaths", new JSONArray().put("/one.pdf")),
            new JSONObject().put("filePaths", new JSONArray().put("https://example.com/a.pdf").put("relative.pdf"))
        }) {
            try { MergePdfTool.parseInputs(args); fail("Invalid input must fail"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
