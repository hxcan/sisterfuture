package com.stupidbeauty.sisterfuture.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceStatsTest {
    @Test public void aggregatesAndClears() {
        PerformanceStats stats = new PerformanceStats();
        assertNull(stats.drain());
        stats.record(10_000_000);
        stats.record(20_000_000);
        assertEquals("count=2 avgMs=15.0 maxMs=20.0 ge16ms=1", stats.drain());
        assertNull(stats.drain());
    }
    @Test public void concurrentRecordingKeepsAllSamples() throws Exception {
        PerformanceStats stats = new PerformanceStats();
        Thread worker = new Thread(() -> { for (int i = 0; i < 1000; i++) stats.record(1_000_000); });
        worker.start();
        for (int i = 0; i < 1000; i++) stats.record(1_000_000);
        worker.join();
        assertEquals("count=2000 avgMs=1.0 maxMs=1.0 ge16ms=0", stats.drain());
    }
}
