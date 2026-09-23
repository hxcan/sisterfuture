package com.stupidbeauty.sisterfuture.tool;

import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class UploadRedmineAttachmentToolTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final UploadRedmineAttachmentTool tool = new UploadRedmineAttachmentTool(null);

    private JSONObject args(MockWebServer server) throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), new byte[]{0, 1, (byte)255, 10});
        return new JSONObject().put("redmineUrl", server.url("/redmine").toString())
                .put("apiKey", "test-key").put("taskId", " 123456789012 ")
                .put("filePath", file.getAbsolutePath()).put("fileName", "图片 a.png");
    }

    @Test public void schemaIsCamelCaseAndAsync() throws Exception {
        JSONObject schema = tool.getDefinition().getJSONObject("function").getJSONObject("parameters");
        assertEquals("uploadRedmineAttachment", tool.getName());
        assertTrue(tool.isAsync());
        assertTrue(tool.shouldInclude());
        assertEquals(2, schema.getJSONArray("required").length());
        JSONObject properties = schema.getJSONObject("properties");
        for (Iterator<String> it = properties.keys(); it.hasNext();) assertFalse(it.next().contains("_"));
        assertEquals("integer", properties.getJSONObject("taskId").getString("type"));
    }

    @Test public void uploadsBytesThenAttachesWithoutOverwritingIssueFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"upload\":{\"token\":\"test-upload-token\"}}"));
            server.enqueue(new MockResponse().setResponseCode(204));
            JSONObject args = args(server).put("description", "附件说明");
            String original = args.toString();
            JSONObject result = tool.upload(args, "ordinary note");
            assertEquals("success", result.getString("status"));
            assertFalse(result.toString().contains("test-upload-token"));
            assertEquals(original, args.toString());
            RecordedRequest upload = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("POST", upload.getMethod());
            assertEquals("/redmine/uploads.json", upload.getRequestUrl().encodedPath());
            assertEquals("图片 a.png", upload.getRequestUrl().queryParameter("filename"));
            assertEquals("application/octet-stream", upload.getHeader("Content-Type"));
            assertArrayEquals(new byte[]{0, 1, (byte)255, 10}, upload.getBody().readByteArray());
            assertEquals("test-key", upload.getHeader("X-Redmine-API-Key"));
            RecordedRequest attach = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("PUT", attach.getMethod());
            assertEquals("/redmine/issues/123456789012.json", attach.getPath());
            assertEquals("test-key", attach.getHeader("X-Redmine-API-Key"));
            JSONObject issue = new JSONObject(attach.getBody().readUtf8()).getJSONObject("issue");
            assertEquals(1, issue.length());
            JSONObject entry = issue.getJSONArray("uploads").getJSONObject(0);
            assertEquals("test-upload-token", entry.getString("token"));
            assertEquals("附件说明", entry.getString("description"));
        }
    }

    @Test public void aliasesAndNoteFallbackWorkWithCanonicalPrecedence() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"upload\":{\"token\":\"t\"}}"));
            server.enqueue(new MockResponse().setResponseCode(204));
            JSONObject args = args(server);
            args.put("file_path", args.remove("filePath"));
            args.put("task_id", 9).put("apiKey", new JSONObject()).remove("redmineUrl");
            String note = new JSONObject().put("redmine_url", server.url("/").toString())
                    .put("username", "test").put("password", "test").toString();
            assertEquals("success", tool.upload(args, note).getString("status"));
            assertTrue(server.takeRequest().getHeader("Authorization").startsWith("Basic "));
            assertEquals("/issues/123456789012.json", server.takeRequest().getPath());
        }
    }

    @Test public void uploadRejectionStopsBeforeIssueUpdate() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(413).setBody("secret response"));
            JSONObject result = tool.upload(args(server), "");
            assertEquals("error", result.getString("status"));
            assertEquals("upload", result.getString("stage"));
            assertFalse(result.getBoolean("uploaded"));
            assertTrue(result.getString("message").contains("413"));
            assertFalse(result.toString().contains("secret"));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test public void attachFailureReportsPartialStateAndDoesNotRetry() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"upload\":{\"token\":\"secret\"}}"));
            server.enqueue(new MockResponse().setResponseCode(422));
            JSONObject result = tool.upload(args(server), "");
            assertEquals("error", result.getString("status"));
            assertEquals("attach", result.getString("stage"));
            assertTrue(result.getBoolean("uploaded"));
            assertFalse(result.toString().contains("secret"));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test public void invalidInputNeverSendsRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            JSONObject args = args(server);
            for (Object id : new Object[]{0, -1, 1.5, JSONObject.NULL, new JSONObject(), "9223372036854775808"}) {
                try { tool.upload(args.put("taskId", id), ""); fail(); }
                catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("taskId")); }
            }
            args.put("taskId", 1).put("filePath", temporary.getRoot().getAbsolutePath());
            try { tool.upload(args, ""); fail(); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("filePath")); }
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test public void malformedUploadResponseDoesNotAttachOrExposeBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(201).setBody("secret malformed JSON"));
            JSONObject result = tool.upload(args(server), "");
            assertEquals("error", result.getString("status"));
            assertTrue(result.getBoolean("uploaded"));
            assertFalse(result.toString().contains("secret"));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test public void redirectIsNotFollowed() throws Exception {
        try (MockWebServer server = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            server.start(); other.start();
            server.enqueue(new MockResponse().setResponseCode(307)
                    .setHeader("Location", other.url("/uploads.json")));
            JSONObject result = tool.upload(args(server), "");
            assertEquals("error", result.getString("status"));
            assertEquals(1, server.getRequestCount());
            assertEquals(0, other.getRequestCount());
        }
    }
}
