package com.stupidbeauty.sisterfuture.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.json.*;

/** Monotonic, numeric-only metrics for a single tool invocation, including failures. */
final class CompressionTimings {
    private final LongSupplier clock;
    private final long started;
    private long phaseStarted;
    private String phase = "mainQueue";
    private final Map<String, Long> durations = new LinkedHashMap<>();
    final JSONArray requests = new JSONArray();
    CompressionTimings() { this(System::nanoTime); }
    CompressionTimings(LongSupplier clock) { this.clock = clock; started = phaseStarted = clock.getAsLong(); }
    synchronized void phase(String next) {
        long now = clock.getAsLong();
        durations.put(phase, durations.getOrDefault(phase, 0L) + now - phaseStarted);
        phaseStarted = now; phase = next;
    }
    synchronized JSONObject finish() {
        phase("finished");
        try {
            JSONObject stages = new JSONObject();
            for (Map.Entry<String,Long> entry : durations.entrySet()) stages.put(entry.getKey() + "Ms", entry.getValue()/1000000);
            return new JSONObject().put("totalMs", (phaseStarted-started)/1000000)
                .put("stages", stages).put("requests", requests);
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
