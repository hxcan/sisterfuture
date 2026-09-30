package com.stupidbeauty.sisterfuture;

import java.util.*;
import org.json.*;

/** Sequential rolling summary; nothing is persisted until the entire operation succeeds. */
public final class ChunkedSummary {
    public static final int CHUNK_CHARACTERS = 48000;
    private ChunkedSummary() { }
    public interface Summarizer { String summarize(String previous, String fragment, int index, int total) throws Exception; }

    public static String run(List<String> chunks, Summarizer summarizer) throws Exception {
        String summary = "";
        for (int i = 0; i < chunks.size(); i++) {
            String next = summarizer.summarize(summary, chunks.get(i), i + 1, chunks.size());
            if (next == null || next.trim().isEmpty() || next.length() > RequestContext.MAX_SUMMARY_CHARS)
                throw new IllegalArgumentException("分段摘要为空或过长，未应用任何压缩结果");
            summary = next.trim();
        }
        return summary;
    }

    /** Keep user turns/tool batches together when they fit; oversized groups are losslessly split. */
    public static List<String> plan(List<JSONObject> messages, int limit) {
        if (limit < 2) throw new IllegalArgumentException("Chunk limit too small");
        List<String> groups = new ArrayList<>();
        StringBuilder group = new StringBuilder();
        Set<String> pending = new HashSet<>();
        for (JSONObject message : messages) {
            if ("user".equals(message.optString("role")) && pending.isEmpty() && group.length() > 0) {
                groups.add(group.toString()); group.setLength(0);
            }
            group.append(message.toString()).append('\n');
            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null) for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.optJSONObject(i);
                if (call != null) pending.add(call.optString("id"));
            }
            if ("tool".equals(message.optString("role"))) pending.remove(message.optString("tool_call_id"));
        }
        if (group.length() > 0) groups.add(group.toString());
        List<String> chunks = new ArrayList<>();
        StringBuilder batch = new StringBuilder();
        for (String value : groups) {
            if (batch.length() + value.length() > limit && batch.length() > 0) {
                chunks.add(batch.toString()); batch.setLength(0);
            }
            if (value.length() <= limit) { batch.append(value); continue; }
            for (int offset = 0; offset < value.length();) {
                int end = Math.min(value.length(), offset + limit);
                if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))
                    && Character.isLowSurrogate(value.charAt(end))) end--;
                chunks.add(value.substring(offset, end)); offset = end;
            }
        }
        if (batch.length() > 0) chunks.add(batch.toString());
        return chunks;
    }
}
