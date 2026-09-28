package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class RenderPdfToolAsyncTest {
    @Test public void returnsBeforeRenderingCompletesAndRepliesFromWorker() throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Thread> renderThread = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        JSONObject expected = new JSONObject().put("status", "success");
        AtomicReference<JSONObject> result = new AtomicReference<>();
        RenderPdfTool tool = new RenderPdfTool(null) {
            @Override protected JSONObject render(JSONObject arguments) {
                renderThread.set(Thread.currentThread());
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return expected;
            }
        };
        assertTrue(tool.isAsync());
        try {
            tool.executeAsync(new JSONObject(), new Tool.OnResultCallback() {
                public void onResult(JSONObject value) {
                    callbackThread.set(Thread.currentThread());
                    result.set(value);
                    done.countDown();
                }
                public void onError(Exception error) { failure.set(error); done.countDown(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(1L, done.getCount());
            assertNotSame(caller, renderThread.get());
        } finally { release.countDown(); }
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertSame(expected, result.get());
        assertSame(renderThread.get(), callbackThread.get());
    }

    @Test public void invalidArgumentsKeepExistingFailedResult() throws Exception {
        RenderPdfTool tool = new RenderPdfTool(null);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<JSONObject> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        tool.executeAsync(null, new Tool.OnResultCallback() {
            public void onResult(JSONObject value) { result.set(value); done.countDown(); }
            public void onError(Exception error) { failure.set(error); done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals("failed", result.get().getString("status"));
    }

    @Test public void unexpectedRenderExceptionUsesErrorCallback() throws Exception {
        IllegalStateException expected = new IllegalStateException("render failed");
        RenderPdfTool tool = new RenderPdfTool(null) {
            @Override protected JSONObject render(JSONObject arguments) { throw expected; }
        };
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<JSONObject> result = new AtomicReference<>();
        tool.executeAsync(new JSONObject(), new Tool.OnResultCallback() {
            public void onResult(JSONObject value) { result.set(value); done.countDown(); }
            public void onError(Exception error) { failure.set(error); done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertSame(expected, failure.get());
        assertNull(result.get());
    }
}
