package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GetRedmineTaskInfoAliasesTest {
  private GetRedmineTaskInfoTool tool(String note) {
    return new GetRedmineTaskInfoTool(null) {
      @Override public String getNote(Context context) { return note; }
    };
  }

  @Test public void schemaUsesCamelCaseAndInteger() throws Exception {
    JSONObject schema = tool("").getDefinition().getJSONObject("function").getJSONObject("parameters");
    JSONObject props = schema.getJSONObject("properties");
    for (Iterator<String> keys = props.keys(); keys.hasNext();) assertFalse(keys.next().contains("_"));
    assertEquals("taskId", schema.getJSONArray("required").getString(0));
    assertEquals("integer", props.getJSONObject("taskId").getString("type"));
    assertTrue(props.has("redmineUrl"));
    assertTrue(props.has("apiKey"));
  }

  @Test public void aliasesPreferCanonicalWithoutMutatingInput() throws Exception {
    JSONObject input = new JSONObject().put("task_id", 1).put("taskId", " 889546934309 ")
        .put("api_key", "old").put("apiKey", JSONObject.NULL);
    String original = input.toString();
    JSONObject parsed = GetRedmineTaskInfoTool.normalizeArguments(input);
    assertEquals(889546934309L, GetRedmineTaskInfoTool.parseTaskId(parsed));
    assertFalse(parsed.has("api_key"));
    assertFalse(parsed.has("apiKey"));
    assertEquals(original, input.toString());
  }

  @Test public void rejectsInvalidIdsWithoutLeakingValues() throws Exception {
    for (Object value : new Object[]{JSONObject.NULL, "", "secret", -1, 0, 1.5,
        "9223372036854775808", new JSONObject(), new org.json.JSONArray(), true}) {
      try {
        GetRedmineTaskInfoTool.parseTaskId(new JSONObject().put("taskId", value));
        fail("invalid ID accepted");
      } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage().contains("taskId"));
        assertFalse(expected.getMessage().contains("secret"));
      }
    }
  }

  @Test public void newAndLegacyNamesReachSameEndpointWithInvalidNote() throws Exception {
    for (boolean legacy : new boolean[]{false,true}) {
      try (MockWebServer server = new MockWebServer()) {
        server.start();
        server.enqueue(new MockResponse().setBody("{\"issue\":{\"id\":889546934309}}"));
        JSONObject args = new JSONObject().put(legacy ? "task_id" : "taskId", " 889546934309 ")
            .put(legacy ? "redmine_url" : "redmineUrl", server.url("/").toString())
            .put(legacy ? "api_key" : "apiKey", "test-key");
        run(args, "Redmine 普通说明");
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", request.getMethod());
        assertEquals("/issues/889546934309.json", request.getRequestUrl().encodedPath());
        assertEquals("journals,relations,attachments,children,watchers,time_entries",
            request.getRequestUrl().queryParameter("include"));
        assertEquals("test-key", request.getHeader("X-Redmine-API-Key"));
      }
    }
  }

  @Test public void malformedOptionalArgsAllowNoteFallback() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setBody("{\"issue\":{\"id\":1}}"));
      String note = new JSONObject().put("redmine_url", server.url("/").toString()).put("api_key", "saved-key").toString();
      run(new JSONObject().put("taskId", 1).put("apiKey", new JSONObject())
          .put("redmineUrl", JSONObject.NULL), note);
      assertEquals("saved-key", server.takeRequest(1, TimeUnit.SECONDS).getHeader("X-Redmine-API-Key"));
    }
  }

  private void run(JSONObject args, String note) throws Exception {
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
    assertTrue(result.get().has("task_info"));
  }
}
