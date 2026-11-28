package com.stupidbeauty.sisterfuture.utils;

/**
 * Markdown 渲染闪烁的观测工具（纯观测，不改变任何渲染行为）。
 *
 * <p>用途：量化「流式输出期间 Markdown 表格渲染闪烁」的三个关键维度 ——
 * 重建频次、形态切换、尺寸跳动。属于任务 #958696185511 的子任务
 * #958698459506（1/4 测量）。
 *
 * <p>与既有 {@link PerformanceStats} 的分工：
 * PerformanceStats 是跨请求累计的耗时统计器（每 5 秒节流输出），
 * 无法按「单次流式」归集；本类则按单次流式独立计数、流式结束时立即输出，
 * 两者互补，不替代。
 *
 * <p>纪律：
 * - 只记录计数与耗时，绝不记录正文内容
 * - 计数为纯数值累加，不在热路径上做字符串拼接或 IO
 * - 全部通过 FileLogger.d 输出，不用 Log.e 刷屏
 */
public final class MarkdownRenderDiagnostics {

    /** 表格分隔行的最小判定片段，例如 "|---|"、"| --- |"、"|:---:|"。 */
    private static final String TABLE_DELIMITER_MARK = "-";

    private MarkdownRenderDiagnostics() {
    }

    /** 单次流式的观测结果。 */
    public static final class Snapshot {
        /** 本次流式期间 setMarkdown 的调用次数。 */
        public final long bindCount;
        /** 本次流式期间「纯文本形态 ↔ 表格形态」的切换次数。 */
        public final long shapeSwitchCount;
        /** 本次流式期间表格所在视图高度发生变化的次数。 */
        public final long heightChangeCount;
        /** 本次流式持续时长（毫秒）。 */
        public final long durationMs;
        /** setMarkdown 单次耗时之和（毫秒）。 */
        public final long totalBindMs;
        /** setMarkdown 单次最大耗时（毫秒）。 */
        public final long maxBindMs;
        /** 本次流式期间是否出现过含完整表格的内容。 */
        public final boolean tableSeen;

        Snapshot(long bindCount, long shapeSwitchCount, long heightChangeCount,
                 long durationMs, long totalBindMs, long maxBindMs, boolean tableSeen) {
            this.bindCount = bindCount;
            this.shapeSwitchCount = shapeSwitchCount;
            this.heightChangeCount = heightChangeCount;
            this.durationMs = durationMs;
            this.totalBindMs = totalBindMs;
            this.maxBindMs = maxBindMs;
            this.tableSeen = tableSeen;
        }

        /** 是否有任何观测数据。 */
        public boolean hasData() {
            return bindCount > 0;
        }

        /** 输出为一行紧凑的统计摘要（不含正文）。 */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("bindCount=").append(bindCount);
            sb.append(" durationMs=").append(durationMs);
            sb.append(" bindTotalMs=").append(totalBindMs);
            sb.append(" bindMaxMs=").append(maxBindMs);
            sb.append(" shapeSwitch=").append(shapeSwitchCount);
            sb.append(" heightChange=").append(heightChangeCount);
            sb.append(" tableSeen=").append(tableSeen ? 1 : 0);
            return sb.toString();
        }
    }

    /** 单次流式的累加器。 */
    private static final class Session {
        long bindCount;
        long shapeSwitchCount;
        long heightChangeCount;
        long totalBindNanos;
        long maxBindNanos;
        boolean tableSeen;
        /** 上一次渲染时内容是否为表格形态，用于检测形态切换。 */
        Boolean lastShapeWasTable;
        /** 上一次观测到的文本视图高度，用于检测尺寸跳动。 */
        int lastHeight = -1;
        long startedAtNanos;
    }

    private static Session current;

    /** 标记一次新的流式开始，结束上一次未收尾的会话。 */
    public static synchronized void beginStream() {
        if (current != null) {
            // 上一次会话没有正常结束（例如异常中断），先收尾输出，避免数据丢失。
            finishStream();
        }
        current = new Session();
        current.startedAtNanos = System.nanoTime();
    }

    /** 结束当前流式并输出统计摘要。没有会话时什么都不做。 */
    public static synchronized void finishStream() {
        if (current == null) {
            return;
        }
        Session session = current;
        current = null;
        long durationMs = Math.max(0L, (System.nanoTime() - session.startedAtNanos) / 1_000_000L);
        Snapshot snapshot = new Snapshot(
                session.bindCount,
                session.shapeSwitchCount,
                session.heightChangeCount,
                durationMs,
                session.totalBindNanos / 1_000_000L,
                session.maxBindNanos / 1_000_000L,
                session.tableSeen);
        if (snapshot.hasData()) {
            FileLogger.d("MarkdownRenderDiagnostics",
                    "[PERF] markdown_render_flicker " + snapshot.describe());
        }
    }

    /**
     * 记录一次 Markdown 渲染。
     *
     * @param text             本次渲染的原始文本
     * @param bindNanos       setMarkdown 的同步耗时（纳秒）
     * @param observedHeight  渲染后文本视图的高度，无法取得时传 -1
     */
    public static synchronized void recordBind(String text, long bindNanos, int observedHeight) {
        Session session = current;
        if (session == null) {
            // 不在流式会话中（例如历史消息回看），不计入闪烁统计。
            return;
        }
        session.bindCount++;
        long duration = Math.max(0L, bindNanos);
        session.totalBindNanos += duration;
        if (duration > session.maxBindNanos) {
            session.maxBindNanos = duration;
        }

        // 形态检测：本次内容能否构成完整表格。
        boolean isTable = containsCompleteTable(text);
        if (isTable) {
            session.tableSeen = true;
        }
        Boolean last = session.lastShapeWasTable;
        if (last != null && last.booleanValue() != isTable) {
            session.shapeSwitchCount++;
        }
        session.lastShapeWasTable = Boolean.valueOf(isTable);

        // 尺寸检测：文本视图高度变化。
        if (observedHeight >= 0) {
            if (session.lastHeight >= 0 && session.lastHeight != observedHeight) {
                session.heightChangeCount++;
            }
            session.lastHeight = observedHeight;
        }
    }

    /**
     * 判断文本中是否存在「完整的 Markdown 表格」。
     *
     * <p>判定方式：逐行扫描，寻找同时满足以下条件的分隔行 ——
     * 该行含 {@code -} 且含 {@code |}，且其上一行也含 {@code |}。
     * 这样可以避免把普通含短横线的列表项误判为表格。
     *
     * <p>这是轻量启发式判断，不是完整 Markdown 解析；目的是低成本地
     * 感知「此刻内容是否已经成形为表格」，用于统计形态切换次数。
     */
    static boolean containsCompleteTable(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int len = text.length();
        int lineStart = 0;
        boolean previousLineHadPipe = false;
        while (lineStart <= len) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = len;
            }
            String line = text.substring(lineStart, lineEnd);
            boolean currentHasPipe = line.indexOf('|') >= 0;
            boolean currentHasDash = line.indexOf(TABLE_DELIMITER_MARK) >= 0;
            if (currentHasPipe && currentHasDash && previousLineHadPipe) {
                return true;
            }
            previousLineHadPipe = currentHasPipe;
            if (lineEnd >= len) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return false;
    }
}
