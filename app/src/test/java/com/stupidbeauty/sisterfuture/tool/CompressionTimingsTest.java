package com.stupidbeauty.sisterfuture.tool;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompressionTimingsTest {
    @Test public void reportsStageAndTotalDurationsWithRequestAttempts() throws Exception {
        AtomicLong clock = new AtomicLong(0);
        CompressionTimings timings = new CompressionTimings(clock::get);
        clock.set(10_000_000); timings.phase("planning");
        clock.set(30_000_000); timings.phase("summarizing");
        timings.requests.put(new JSONObject().put("segment",1).put("attempt",1).put("elapsedMs",40).put("result","too_long"));
        clock.set(80_000_000);
        JSONObject report = timings.finish();
        assertEquals(80,report.getLong("totalMs"));
        assertEquals(10,report.getJSONObject("stages").getLong("mainQueueMs"));
        assertEquals(20,report.getJSONObject("stages").getLong("planningMs"));
        assertEquals(50,report.getJSONObject("stages").getLong("summarizingMs"));
        assertEquals("too_long",report.getJSONArray("requests").getJSONObject(0).getString("result"));
    }
    @Test public void failureRetainsCompletedWorkAndDoesNotInventCommitStage() throws Exception {
        AtomicLong clock = new AtomicLong();
        CompressionTimings timings = new CompressionTimings(clock::get);
        timings.phase("summarizing");
        clock.set(70_000_000); timings.phase("failureCallbackQueue");
        clock.set(75_000_000);
        JSONObject report = timings.finish();
        assertEquals(70,report.getJSONObject("stages").getLong("summarizingMs"));
        assertEquals(5,report.getJSONObject("stages").getLong("failureCallbackQueueMs"));
        assertFalse(report.getJSONObject("stages").has("commitMs"));
    }
}
