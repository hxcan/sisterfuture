package com.stupidbeauty.sisterfuture.tool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApprovePullRequestToolTest {
    private MockWebServer server;
    @Before public void setUp() throws Exception { server = new MockWebServer(); server.start(); }
    @After public void tearDown() throws Exception { server.shutdown(); }
    private ApprovePullRequestTool tool(String note) {
        return new ApprovePullRequestTool(new OkHttpClient(), server.url("/"), Runnable::run, () -> note, null);
    }
    private JSONObject args() throws Exception {
        return new JSONObject().put("owner", "hxcan").put("repo", "sisterfuture")
                .put("pull_number", 570).put("token", "test-secret");
    }
    private MockResponse approved() {
        return new MockResponse().setBody("{\"id\":123,\"state\":\"APPROVED\",\"html_url\":\"https://github.com/review/123\",\"commit_id\":\"abc\"}");
    }
    @Test public void definitionAndDispatchFlags() throws Exception {
        ApprovePullRequestTool tool = tool("");
        assertEquals("approvePullRequest", tool.getName());
        assertTrue(tool.isAsync()); assertTrue(tool.shouldInclude());
        assertFalse(tool.shouldRecordParameterHistory());
        JSONObject definition = tool.getDefinition().getJSONObject("function");
        assertEquals(tool.getName(), definition.getString("name"));
        assertEquals(3, definition.getJSONObject("parameters").getJSONArray("required").length());
    }
    @Test public void sendsApprovalWithOptionalBodyAndCommit() throws Exception {
        server.enqueue(approved());
        JSONObject result = tool("not json; explicit token takes priority")
                .execute(args().put("body", "Reviewed").put("commit_id", "abc").put("event", "REQUEST_CHANGES"));
        assertTrue(result.getBoolean("success"));
        assertEquals(123, result.getLong("review_id"));
        assertEquals("APPROVED", result.getString("state"));
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/repos/hxcan/sisterfuture/pulls/570/reviews", request.getPath());
        assertEquals("Bearer test-secret", request.getHeader("Authorization"));
        JSONObject payload = new JSONObject(request.getBody().readUtf8());
        assertEquals("APPROVE", payload.getString("event"));
        assertEquals("abc", payload.getString("commit_id"));
        assertEquals("Reviewed", payload.getString("body"));
        assertFalse(result.toString().contains("test-secret"));
    }
    @Test public void noteTokenAndMinimalPayload() throws Exception {
        server.enqueue(approved());
        JSONObject arguments=args(); arguments.remove("token");
        assertTrue(tool("{\"github_token\":\" note-secret \"}").execute(arguments).getBoolean("success"));
        RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
        assertEquals("Bearer note-secret",request.getHeader("Authorization"));
        JSONObject payload=new JSONObject(request.getBody().readUtf8());
        assertEquals(1,payload.length()); assertEquals("APPROVE",payload.getString("event"));
    }
    @Test public void rejectsMissingTokenAndInvalidTargetsBeforeNetwork() throws Exception {
        JSONObject missing=args();missing.remove("token");
        assertThrows(IllegalArgumentException.class,()->tool("").execute(missing));
        for(Object number:new Object[]{0,-1,1.5,"570"}) {
            JSONObject invalid=args().put("pull_number",number);
            assertThrows(IllegalArgumentException.class,()->tool("").execute(invalid));
        }
        assertThrows(IllegalArgumentException.class,()->tool("").execute(args().put("repo","../other")));
        assertEquals(0,server.getRequestCount());
    }
    @Test public void permissionAndSelfApprovalFailuresAreNotSuccess() throws Exception {
        for(int status:new int[]{401,403,404,422,500}) {
            server.enqueue(new MockResponse().setResponseCode(status)
                    .setBody("{\"message\":\"Cannot approve: test-secret\"}"));
            JSONObject result=tool("").execute(args());
            assertFalse(result.getBoolean("success")); assertEquals(status,result.getInt("status_code"));
            assertFalse(result.toString().contains("test-secret"));
        }
    }
    @Test public void unexpectedOrMalformedSuccessDoesNotClaimApproval() throws Exception {
        for(String body:new String[]{"{\"state\":\"PENDING\"}","not json",""}) {
            server.enqueue(new MockResponse().setBody(body));
            assertFalse(tool("").execute(args()).getBoolean("success"));
        }
    }
    @Test public void redirectsAreNotFollowed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location",server.url("/other")));
        assertFalse(tool("").execute(args()).getBoolean("success"));
        assertEquals(1,server.getRequestCount());
    }
    @Test public void invalidNoteDoesNotExposeItsContents() throws Exception {
        JSONObject arguments=args();arguments.remove("token");
        Exception error=assertThrows(IllegalArgumentException.class,
                ()->tool("private-note-secret").execute(arguments));
        assertFalse(error.getMessage().contains("private-note-secret"));
        assertEquals(0,server.getRequestCount());
    }
    @Test public void disconnectedResponseIsUncertainAndNotReplayed() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        Exception error=assertThrows(java.io.IOException.class,()->tool("").execute(args()));
        assertTrue(error.getMessage().contains("可能已提交"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertEquals(1,server.getRequestCount());
    }
    @Test public void asyncSuccessRunsOffCallerThreadAndInvalidArgsUseErrorCallback() throws Exception {
        server.enqueue(approved());
        long caller=Thread.currentThread().getId();
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Exception> error=new AtomicReference<>();
        AtomicReference<JSONObject> result=new AtomicReference<>();
        AtomicReference<Long> callbackThread=new AtomicReference<>();
        ApprovePullRequestTool async=new ApprovePullRequestTool(new OkHttpClient(),server.url("/"),
                task -> new Thread(task).start(),()->"",null);
        async.executeAsync(args(),new Tool.OnResultCallback() {
            public void onResult(JSONObject value) { result.set(value);callbackThread.set(Thread.currentThread().getId());done.countDown(); }
            public void onError(Exception value) { error.set(value);done.countDown(); }
        });
        assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(error.get());
        assertTrue(result.get().getBoolean("success"));assertNotEquals(caller,callbackThread.get().longValue());
        tool("").executeAsync(new JSONObject(),new Tool.OnResultCallback() {
            public void onResult(JSONObject value) { fail("Expected error"); }
            public void onError(Exception value) { error.set(value); }
        });
        assertNotNull(error.get());
    }
}
