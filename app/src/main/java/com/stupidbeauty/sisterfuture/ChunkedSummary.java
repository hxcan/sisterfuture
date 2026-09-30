package com.stupidbeauty.sisterfuture;

import java.util.*;
import org.json.*;

/** Sequential rolling summary; nothing is persisted until the entire operation succeeds. */
public final class ChunkedSummary {
    public static final int CHUNK_CHARACTERS = 48000;
    private ChunkedSummary() { }
    public interface Summarizer { String summarize(String previous, String fragment, int index, int total) throws Exception; }

    public static String run(List<String> chunks, Summarizer summarizer) throws Exception {
        return run(chunks, summarizer, event -> { });
    }

    public static String run(List<String> chunks, Summarizer summarizer,
                             java.util.function.Consumer<String> diagnostic) throws Exception {
        String summary = "";
        for (int i = 0; i < chunks.size(); i++) {
            String oversized = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                String label = "segment=" + (i + 1) + "/" + chunks.size() + " attempt=" + attempt;
                diagnostic.accept(label + " phase=start mode=" + (oversized == null ? "summarize" : "refine")
                    + " sourceChars=" + chunks.get(i).length() + " previousChars=" + summary.length());
                long start = System.nanoTime();
                String next;
                try {
                    next = oversized == null
                        ? summarizer.summarize(summary, chunks.get(i), i + 1, chunks.size())
                        : summarizer.summarize("", "请精简以下累计摘要，保留关键决定、约束、待办和不确定性，"
                            + "不要添加事实。仅输出精简后的完整累计摘要，目标1500字以内：\n" + oversized, i + 1, chunks.size());
                } catch (Exception e) {
                    diagnostic.accept(label + " phase=failed reason=request_error errorType=" + e.getClass().getSimpleName());
                    throw new java.io.IOException("摘要第 " + (i + 1) + "/" + chunks.size()
                        + " 段请求失败（" + e.getClass().getSimpleName() + "）；未应用任何压缩结果", e);
                }
                next = next == null ? "" : next.trim();
                String reason = next.isEmpty() ? "empty" : next.length() > RequestContext.MAX_SUMMARY_CHARS ? "too_long" : "ok";
                diagnostic.accept(label + " phase=result reason=" + reason + " summaryChars=" + next.length()
                    + " elapsedMs=" + ((System.nanoTime() - start) / 1000000));
                if ("ok".equals(reason)) { summary = next; break; }
                if (attempt == 3) throw new IllegalArgumentException("摘要第 " + (i + 1) + "/" + chunks.size()
                    + " 段在3次尝试后仍" + (next.isEmpty() ? "为空" : "过长（" + next.length() + "字符，上限"
                    + RequestContext.MAX_SUMMARY_CHARS + "）") + "；未应用任何压缩结果");
                if (!next.isEmpty()) oversized = next;
            }
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
