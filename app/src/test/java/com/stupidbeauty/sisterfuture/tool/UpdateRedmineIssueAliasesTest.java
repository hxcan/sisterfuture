package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateRedmineIssueAliasesTest {
    private UpdateRedmineIssueTool tool(String note) {
        return new UpdateRedmineIssueTool(null) {
            @Override public String getNote(Context ignored) { return note; }
        };
    }

    @Test public void schemaAdvertisesCamelCaseAndTracker() throws Exception {
        JSONObject schema = tool("").getDefinition().getJSONObject("function").getJSONObject("parameters");
        JSONObject properties = schema.getJSONObject("properties");
        for (Iterator<String> keys = properties.keys(); keys.hasNext();) assertFalse(keys.next().contains("_"));
        assertTrue(properties.has("trackerId"));
        assertEquals("taskId", schema.getJSONArray("required").getString(0));
        assertTrue(tool("").getDefaultSystemPromptEnhancement().contains("trackerId"));
    }

    private JSONObject call(JSONObject args, boolean noteAuth) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setResponseCode(204));
            String note = new JSONObject().put("redmine_url", server.url("/").toString())
                .put("api_key", "note-key").toString();
            if (!noteAuth) args.put("redmineUrl", server.url("/").toString()).put("apiKey", "arg-key");
            String original = args.toString();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<JSONObject> result = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            tool(note).executeAsync(args, new Tool.OnResultCallback() {
                public void onResult(JSONObject value) { result.set(value); done.countDown(); }
                public void onError(Exception value) { error.set(value); done.countDown(); }
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(error.get());
            assertEquals("success", result.get().getString("status"));
            assertEquals(original, args.toString());
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("PUT", request.getMethod());
            assertEquals("/issues/889546934309.json", request.getPath());
            assertEquals(noteAuth ? "note-key" : "arg-key", request.getHeader("X-Redmine-API-Key"));
            return new JSONObject(request.getBody().readUtf8()).getJSONObject("issue");
        }
    }

    @Test public void canonicalAndLegacyParametersProduceSamePayload() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            JSONObject args = new JSONObject();
            String[] camel = {"taskId", "trackerId", "statusId", "assignedToId", "fixedVersionId", "parentIssueId", "projectId"};
            String[] old = {"task_id", "tracker_id", "status_id", "assigned_to_id", "fixed_version_id", "parent_issue_id", "project_id"};
            long[] values = {889546934309L, 2, 3, 17, 4, 750160066086L, 8};
            for (int i = 0; i < camel.length; i++) args.put(legacy ? old[i] : camel[i], values[i]);
            args.put(legacy ? "blocked_by_ids" : "blockedByIds", new org.json.JSONArray().put(11))
                .put(legacy ? "blocking_ids" : "blockingIds", new org.json.JSONArray().put(12))
                .put("subject", "title").put("description", "description").put("notes", "note").put("priority", "High");
            JSONObject issue = call(args, legacy);
            for (int i = 1; i < camel.length; i++) {
                assertEquals(values[i], issue.getLong(old[i]));
                assertFalse(issue.has(camel[i]));
            }
            assertEquals(11, issue.getJSONObject("relations").getJSONArray("blocked_by").getInt(0));
            assertEquals(12, issue.getJSONObject("relations").getJSONArray("blocks").getInt(0));
            assertEquals(4, issue.getInt("priority_id"));
            assertEquals("note", issue.getString("notes"));
        }
    }

    @Test public void canonicalWinsIncludingExplicitClears() throws Exception {
        JSONObject args = new JSONObject().put("taskId", 889546934309L).put("task_id", 1)
            .put("trackerId", "3").put("tracker_id", 2)
            .put("assignedToId", 0).put("assigned_to_id", 17)
            .put("fixedVersionId", JSONObject.NULL).put("fixed_version_id", 4)
            .put("parentIssueId", JSONObject.NULL).put("parent_issue_id", 5);
        JSONObject issue = call(args, true);
        assertEquals(3, issue.getInt("tracker_id"));
        assertEquals("", issue.getString("assigned_to_id"));
        assertTrue(issue.isNull("fixed_version_id"));
        assertTrue(issue.isNull("parent_issue_id"));
        assertFalse(issue.has("project_id"));
    }

    @Test public void omittedTrackerDoesNotChangeType() throws Exception {
        JSONObject issue = call(new JSONObject().put("task_id", 889546934309L).put("notes", "only comment"), true);
        assertFalse(issue.has("tracker_id"));
        assertEquals(1, issue.length());
    }
}
