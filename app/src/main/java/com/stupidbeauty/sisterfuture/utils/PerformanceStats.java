package com.stupidbeauty.sisterfuture.utils;

/** Numeric-only, bounded diagnostics. No text, parameters, or message identifiers. */
public final class PerformanceStats {
    private long count, total, max, slow, lastReport;
    public synchronized void record(long nanos) {
        long duration = Math.max(0, nanos);
        count++;
        total += duration;
        max = Math.max(max, duration);
        if (duration >= 16_000_000L) slow++;
    }
    public synchronized String drain() {
        if (count == 0) return null;
        String result = "count=" + count + " avgMs=" + (total / count / 1_000_000.0)
            + " maxMs=" + (max / 1_000_000.0) + " ge16ms=" + slow;
        count = total = max = slow = 0;
        return result;
    }
    public synchronized void report(String metric, boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastReport < 5_000_000_000L) return;
        lastReport = now;
        String summary = drain();
        if (summary != null) FileLogger.i("PerformanceStats", "[PERF] " + metric + " " + summary);
    }
}
