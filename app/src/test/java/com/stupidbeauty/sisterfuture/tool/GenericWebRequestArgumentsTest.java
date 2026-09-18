package com.stupidbeauty.sisterfuture.tool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GenericWebRequestArgumentsTest {
    private RecordedRequest call(JSONObject args) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("ok"));
            args.put("url", server.url("/test").toString());
            String before = args.toString();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<JSONObject> result = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            new GenericWebRequestTool(null).executeAsync(args, new Tool.OnResultCallback() {
                public void onResult(JSONObject value) { result.set(value); done.countDown(); }
                public void onError(Exception value) { error.set(value); done.countDown(); }
            });
            assertTrue("request callback timed out", done.await(5, TimeUnit.SECONDS));
            assertNull(error.get());
            assertTrue(result.get().getBoolean("success"));
            assertEquals(before, args.toString());
            return server.takeRequest(1, TimeUnit.SECONDS);
        }
    }

    @Test public void missingAndNullBodiesBecomeEmptyForBodyMethods() throws Exception {
        for (String method : new String[]{"POST", "PUT", "PATCH"}) {
            for (boolean explicitNull : new boolean[]{false, true}) {
                JSONObject args = new JSONObject().put("method", method);
                if (explicitNull) args.put("body", JSONObject.NULL);
                RecordedRequest request = call(args);
                assertEquals(method, request.getMethod());
                assertEquals(0L, request.getBodySize());
            }
        }
    }

    @Test public void objectAndArrayBodiesAreSerializedWithoutChangingPayload() throws Exception {
        for (Object body : new Object[]{new JSONObject().put("Mixed_Key", 7), new JSONArray().put("value")}) {
            RecordedRequest request = call(new JSONObject().put("METHOD", " post ").put("BODY", body)
                .put("HEADERS", "{\"content-type\":\"application/json\",\"X-Count\":2}"));
            assertEquals(body.toString(), request.getBody().readUtf8());
            assertEquals("application/json", request.getHeader("Content-Type"));
            assertEquals("2", request.getHeader("X-Count"));
        }
    }

    @Test public void emptyHeadersAndJsonNullOptionalFieldsAreSafe() throws Exception {
        for (String method : new String[]{"GET", "DELETE", "POST"}) {
            assertEquals(method, call(new JSONObject().put("method", method)
                .put("headers", new JSONObject()).put("auth_type", JSONObject.NULL)
                .put("session_id", JSONObject.NULL)).getMethod());
        }
    }

    @Test public void textJsonAndInferredFormsStillWork() throws Exception {
        for (String body : new String[]{"hello", "{\"key\":1}"}) {
            assertEquals(body, call(new JSONObject().put("method", "POST").put("body", body)).getBody().readUtf8());
        }
        assertEquals("a=one%20two&b=three", call(new JSONObject().put("method", "POST")
            .put("body", "a=one%20two&b=three")).getBody().readUtf8());
    }

    @Test public void ambiguousHeaderStructuresFailClearlyWithoutExposingValues() throws Exception {
        for (Object headers : new Object[]{"not-json-secret", new JSONArray(),
                new JSONObject().put("Authorization", JSONObject.NULL),
                new JSONObject().put("Authorization", new JSONObject().put("secret", "private"))}) {
            try {
                GenericWebRequestTool.normalizeArguments(new JSONObject().put("headers", headers));
                fail("Expected header error");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("headers"));
                assertFalse(expected.getMessage().contains("secret"));
                assertFalse(expected.getMessage().contains("private"));
            }
        }
    }
}
