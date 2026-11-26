package com.stupidbeauty.sisterfuture;

import java.util.*;
import org.json.*;

/** Sequential rolling summary. Greedy mode keeps every successfully summarised segment instead of discarding all work when one segment fails. */
public final class ChunkedSummary {
    /**
     * 单片最大字符数。
     *
     * 2026-11-26 依据实测重设（Redmine #956583437306）：
     * 在 192.168.1.144 上用真实接入点 MiniMax-M3-stupidbeauty 二分探测，
     * 纯文本上下文上限约为 2,750,000 ~ 2,781,250 字符（body 约 8.8 MB），
     * 服务端超限时的响应为：
     *   invalid params, context window exceeds limit (2013)
     * 旧值 188000 只用到真实上限的 6.8%，导致 9.4MB 的历史被切成 66 片，
     * 60 秒预算只跑得完 2 片。现取 900000（约上限的 1/3），留足余量给
     * system prompt、累计摘要与实际内容密度的波动。
     */
    public static final int CHUNK_CHARACTERS = 900000;
    private ChunkedSummary() { }
    public interface Summarizer { String summarize(String previous, String fragment, int index, int total) throws Exception; }

    /**
     * 服务端判定上下文超出模型窗口时抛出，用于触发上层「减小分片容量后重试」。
     *
     * 2026-11-26：实测 MiniMax 返回
     *   invalid params, context window exceeds limit (2013)
     * 与「语法错误」「余额不足」等其它 400 必须区分开，只有这一类才需要减半重试。
     */
    public static final class ContextTooLargeException extends Exception {
        public ContextTooLargeException(String message) { super(message); }
    }

    /** One planned fragment together with the number of archive messages it fully covers. */
    public static final class Chunk {
        public final String text;
        /** Exclusive message index; -1 when the fragment does not end on a message boundary. */
        public final int endMessageIndex;
        Chunk(String text, int endMessageIndex) { this.text = text; this.endMessageIndex = endMessageIndex; }
    }

    /** Outcome of a greedy run: the summary built from the segments that did succeed. */
    public static final class Result {
        public final String summary;
        public final List<Integer> succeededSegments;
        public final List<Integer> failedSegments;
        /**
         * Segments that were never attempted because the time budget ran out.
         * 2026-11-24 主人要求：到点就提交已有成果，不等所有片段跑完。
         * 这些片段保持原样（不丢数据），下次压缩可继续累积。
         */
        public final List<Integer> skippedSegments;
        /** True when the run stopped because the time budget was exhausted (not because of a failure). */
        public final boolean budgetExhausted;
        /** For every segment (1-based) the exclusive archive message index it fully covers, -1 if unknown. */
        public final int[] endMessageIndexBySegment;
        Result(String summary, List<Integer> succeededSegments, List<Integer> failedSegments, int[] endMessageIndexBySegment) {
            this(summary, succeededSegments, failedSegments, Collections.<Integer>emptyList(), false, endMessageIndexBySegment);
        }
        Result(String summary, List<Integer> succeededSegments, List<Integer> failedSegments,
               List<Integer> skippedSegments, boolean budgetExhausted, int[] endMessageIndexBySegment) {
            this.summary = summary;
            this.succeededSegments = Collections.unmodifiableList(succeededSegments);
            this.failedSegments = Collections.unmodifiableList(failedSegments);
            this.skippedSegments = Collections.unmodifiableList(skippedSegments);
            this.budgetExhausted = budgetExhausted;
            this.endMessageIndexBySegment = endMessageIndexBySegment;
        }
        public boolean isComplete() { return failedSegments.isEmpty() && skippedSegments.isEmpty(); }
    }

    public static String run(List<String> chunks, Summarizer summarizer) throws Exception {
        return run(chunks, summarizer, event -> { });
    }

    public static String run(List<String> chunks, Summarizer summarizer,
                             java.util.function.Consumer<String> diagnostic) throws Exception {
        return runGreedy(chunks, summarizer, diagnostic, null).summary;
    }

    /**
     * Greedy variant: a failing segment no longer aborts the run. The summary collected so far is kept,
     * the failed segment is recorded and the next segment continues from the last successful summary.
     * The caller decides what to persist.
     */
    public static Result runGreedy(List<String> chunks, Summarizer summarizer,
                                   java.util.function.Consumer<String> diagnostic,
                                   int[] endMessageIndexBySegment) throws Exception {
        return runGreedy(chunks, summarizer, diagnostic, endMessageIndexBySegment, 0L);
    }

    /**
     * Greedy variant with a total time budget.
     *
     * 2026-11-24 主人指示：「从你开始压缩开始，比如说一分钟之内我要你结束压缩，
     * 并且把已有的成果给提交，我不要等所有的片段全都搞完。」
     *
     * The budget is checked between segments, so a segment that is already running is never interrupted.
     * When the budget is exhausted the remaining segments are recorded in {@link Result#skippedSegments}
     * and the summary collected so far is returned, so the caller can persist the partial progress.
     *
     * @param timeBudgetMs total budget in milliseconds; values &lt;= 0 mean "no limit"
     */
    public static Result runGreedy(List<String> chunks, Summarizer summarizer,
                                   java.util.function.Consumer<String> diagnostic,
                                   int[] endMessageIndexBySegment,
                                   long timeBudgetMs) throws Exception {
        String summary = "";
        List<Integer> succeeded = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        boolean budgetExhausted = false;
        final long startedAt = System.nanoTime();
        for (int i = 0; i < chunks.size(); i++) {
            // 🆕 #955184165419 时间预算检查：放在片段之间，不打断正在进行的单片
            if (timeBudgetMs > 0) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1000000L;
                if (elapsedMs >= timeBudgetMs) {
                    budgetExhausted = true;
                    for (int remaining = i; remaining < chunks.size(); remaining++) {
                        skipped.add(remaining + 1);
                    }
                    diagnostic.accept("phase=budget_exhausted action=stop elapsedMs=" + elapsedMs
                        + " budgetMs=" + timeBudgetMs + " completedSegments=" + succeeded.size()
                        + " failedSegments=" + failed.size() + " skippedSegments=" + skipped.size());
                    break;
                }
            }
            String oversized = null;
            boolean segmentOk = false;
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
                    diagnostic.accept(label + " phase=failed reason=request_error errorType=" + e.getClass().getSimpleName()
                        + " action=skip_segment");
                    failed.add(i + 1);
                    break;
                }
                next = next == null ? "" : next.trim();
                String reason = next.isEmpty() ? "empty" : next.length() > CHUNK_CHARACTERS ? "too_long" : "ok";
                diagnostic.accept(label + " phase=result reason=" + reason + " summaryChars=" + next.length()
                    + " elapsedMs=" + ((System.nanoTime() - start) / 1000000));
                if ("ok".equals(reason)) { summary = next; segmentOk = true; break; }
                if (attempt == 3) {
                    diagnostic.accept(label + " phase=abandoned reason=" + reason + " action=skip_segment");
                    failed.add(i + 1);
                    break;
                }
                if (!next.isEmpty()) oversized = next;
            }
            if (segmentOk) succeeded.add(i + 1);
        }
        return new Result(summary, succeeded, failed, skipped, budgetExhausted, endMessageIndexBySegment);
    }

    /** Keep user turns/tool batches together when they fit; oversized groups are losslessly split. */
    public static List<String> plan(List<JSONObject> messages, int limit) {
        List<String> chunks = new ArrayList<>();
        for (Chunk chunk : planWithBoundaries(messages, limit)) chunks.add(chunk.text);
        return chunks;
    }

    /**
     * Same planning as {@link #plan} but each fragment also reports the exclusive archive message index it
     * fully covers, so a greedy run can persist only the part that was summarised successfully.
     * -1 means the fragment ends inside a message and therefore cannot be used as a compression boundary.
     */
    public static List<Chunk> planWithBoundaries(List<JSONObject> messages, int limit) {
        if (limit < 2) throw new IllegalArgumentException("Chunk limit too small");
        List<String> groups = new ArrayList<>();
        List<Integer> groupEnds = new ArrayList<>();
        StringBuilder group = new StringBuilder();
        int groupEndMessage = -1;
        Set<String> pending = new HashSet<>();
        for (int index = 0; index < messages.size(); index++) {
            JSONObject message = messages.get(index);
            if ("user".equals(message.optString("role")) && pending.isEmpty() && group.length() > 0) {
                groups.add(group.toString()); groupEnds.add(groupEndMessage);
                group.setLength(0); groupEndMessage = -1;
            }
            group.append(message.toString()).append('\n');
            groupEndMessage = index + 1;
            JSONArray calls = message.optJSONArray("tool_calls");
            if (calls != null) for (int i = 0; i < calls.length(); i++) {
                JSONObject call = calls.optJSONObject(i);
                if (call != null) pending.add(call.optString("id"));
            }
            if ("tool".equals(message.optString("role"))) pending.remove(message.optString("tool_call_id"));
        }
        if (group.length() > 0) { groups.add(group.toString()); groupEnds.add(groupEndMessage); }
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder batch = new StringBuilder();
        int batchEnd = -1;
        for (int g = 0; g < groups.size(); g++) {
            String value = groups.get(g);
            int end = groupEnds.get(g);
            if (batch.length() + value.length() > limit && batch.length() > 0) {
                chunks.add(new Chunk(batch.toString(), batchEnd)); batch.setLength(0); batchEnd = -1;
            }
            if (value.length() <= limit) { batch.append(value); batchEnd = end; continue; }
            for (int offset = 0; offset < value.length();) {
                int stop = Math.min(value.length(), offset + limit);
                if (stop < value.length() && Character.isHighSurrogate(value.charAt(stop - 1))
                    && Character.isLowSurrogate(value.charAt(stop))) stop--;
                chunks.add(new Chunk(value.substring(offset, stop), -1)); offset = stop;
            }
        }
        if (batch.length() > 0) chunks.add(new Chunk(batch.toString(), batchEnd));
        return chunks;
    }
}