package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreateRedmineTaskAliasesTest {
    private MockWebServer server;
    @Before public void setUp() throws Exception { server=new MockWebServer();server.start(); }
    @After public void tearDown() throws Exception { server.shutdown(); }
    private CreateRedmineTaskTool tool(String note) {
        return new CreateRedmineTaskTool(null) {
            @Override public String getNote(Context ignored) { return note; }
        };
    }
    private RecordedRequest call(JSONObject args,String note) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"issue\":{\"id\":123}}"));
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Exception> error=new AtomicReference<>();
        AtomicReference<JSONObject> result=new AtomicReference<>();
        tool(note).executeAsync(args,new Tool.OnResultCallback() {
            public void onResult(JSONObject value) { result.set(value);done.countDown(); }
            public void onError(Exception value) { error.set(value);done.countDown(); }
        });
        assertTrue("async result timed out",done.await(5,TimeUnit.SECONDS));
        assertNull(error.get());assertEquals("success",result.get().getString("status"));
        RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
        assertEquals("POST",request.getMethod());assertEquals("/issues.json",request.getPath());
        return request;
    }
    @Test public void schemaOnlyAdvertisesCamelCaseIncludingRequiredAndPrompt() throws Exception {
        JSONObject schema=tool("").getDefinition().getJSONObject("function").getJSONObject("parameters");
        JSONObject props=schema.getJSONObject("properties");
        for(Iterator<String> keys=props.keys();keys.hasNext();) assertFalse(keys.next().contains("_"));
        for(String name:new String[]{"redmineUrl","apiKey","projectId","parentIssueId","trackerId","assignedToId"})
            assertTrue(props.has(name));
        assertEquals("projectId",schema.getJSONArray("required").getString(0));
        assertTrue(tool("").getDefaultSystemPromptEnhancement().contains("parentIssueId"));
    }
    @Test public void newAndOldNamesProduceSameRedmineRequest() throws Exception {
        JSONObject expected=null;
        for(boolean legacy:new boolean[]{false,true}) {
            JSONObject args=new JSONObject().put(legacy?"redmine_url":"redmineUrl",server.url("/").toString())
                    .put(legacy?"api_key":"apiKey","test-key")
                    .put(legacy?"project_id":"projectId",750160066086L)
                    .put(legacy?"parent_issue_id":"parentIssueId",889546934309L)
                    .put(legacy?"tracker_id":"trackerId",2)
                    .put(legacy?"assigned_to_id":"assignedToId",17).put("subject","child");
            RecordedRequest request=call(args,"");
            assertEquals("test-key",request.getHeader("X-Redmine-API-Key"));
            JSONObject issue=new JSONObject(request.getBody().readUtf8()).getJSONObject("issue");
            assertEquals(889546934309L,issue.getLong("parent_issue_id"));
            assertEquals(750160066086L,issue.getLong("project_id"));
            assertEquals(17,issue.getInt("assigned_to_id"));assertEquals(2,issue.getInt("tracker_id"));
            assertFalse(issue.has("parentIssueId"));
            if(expected==null) expected=issue;
            else {
                assertEquals(expected.length(),issue.length());
                for(Iterator<String> keys=expected.keys();keys.hasNext();) {
                    String key=keys.next();assertEquals(expected.get(key),issue.get(key));
                }
            }
            assertEquals(legacy,args.has("parent_issue_id"));
        }
    }
    @Test public void mixedArgumentsPreferCanonicalAndKeepOldCredentialNotes() throws Exception {
        String note=new JSONObject().put("redmine_url",server.url("/").toString()).put("api_key","note-key").toString();
        JSONObject args=new JSONObject().put("project_id","750160066086").put("subject","child")
                .put("parent_issue_id",10).put("parentIssueId",889546934309L)
                .put("assignedToId",17).put("assigned_to_id",99)
                .put("api_key","old-key").put("apiKey","new-key")
                .put("redmine_url","https://unused.invalid").put("redmineUrl",server.url("/").toString());
        RecordedRequest request=call(args,note);
        assertEquals("new-key",request.getHeader("X-Redmine-API-Key"));
        JSONObject issue=new JSONObject(request.getBody().readUtf8()).getJSONObject("issue");
        assertEquals(889546934309L,issue.getLong("parent_issue_id"));assertEquals(17,issue.getInt("assigned_to_id"));
        RecordedRequest saved=call(new JSONObject().put("projectId",1).put("subject","standalone"),note);
        assertEquals("note-key",saved.getHeader("X-Redmine-API-Key"));
        assertFalse(new JSONObject(saved.getBody().readUtf8()).getJSONObject("issue").has("parent_issue_id"));
    }
    @Test public void camelCaseCredentialNotesAndBasicAuthStillWork() throws Exception {
        String note=new JSONObject().put("redmineUrl",server.url("/").toString()).put("apiKey","saved-key").toString();
        JSONObject args=new JSONObject().put("projectId",1).put("subject","basic");
        assertEquals("saved-key",call(args,note).getHeader("X-Redmine-API-Key"));
        args.put("username","user").put("password","password");
        RecordedRequest basic=call(args,note);
        assertEquals(Credentials.basic("user","password"),basic.getHeader("Authorization"));
        assertNull(basic.getHeader("X-Redmine-API-Key"));
    }
}
