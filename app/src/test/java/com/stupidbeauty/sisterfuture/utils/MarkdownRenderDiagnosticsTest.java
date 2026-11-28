package com.stupidbeauty.sisterfuture.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link MarkdownRenderDiagnostics} 的判定逻辑单元测试。
 *
 * <p>属于任务 #958696185511 子任务 #958698459506（1/4 测量）：
 * 表格形态判断是「形态切换次数」这个指标的基础，必须先保证判定本身正确，
 * 否则测出来的数据不可信。
 */
public class MarkdownRenderDiagnosticsTest {

    @Test
    public void completeTableIsDetected() {
        String text = "| 字段 | 说明 |\n"
                + "|---|---|\n"
                + "| timeout | 超时 |";
        assertTrue(MarkdownRenderDiagnostics.containsCompleteTable(text));
    }

    @Test
    public void tableWithoutDelimiterRowIsNotDetected() {
        // 流式中间态：表头已经到达，但分隔行还没到。
        // 此时内容在屏幕上仍是纯文本形态，不应判定为表格。
        String text = "| 字段 | 说明 |\n"
                + "| timeout | 超时 |";
        assertFalse(MarkdownRenderDiagnostics.containsCompleteTable(text));
    }

    @Test
    public void delimiterRowAsFirstLineIsNotDetected() {
        // 分隔行在最前面，上一行没有表格行 → 不构成完整表格。
        String text = "|---|---|\n"
                + "| 字段 | 说明 |";
        assertFalse(MarkdownRenderDiagnostics.containsCompleteTable(text));
    }

    @Test
    public void plainListWithDashesIsNotDetected() {
        // 普通列表项含短横线，不能误判为表格。
        String text = "- 第一项\n"
                + "- 第二项\n"
                + "- 第三项";
        assertFalse(MarkdownRenderDiagnostics.containsCompleteTable(text));
    }

    @Test
    public void nullAndEmptyAreNotDetected() {
        assertFalse(MarkdownRenderDiagnostics.containsCompleteTable(null));
        assertFalse(MarkdownRenderDiagnostics.containsCompleteTable(""));
    }

    @Test
    public void tableWithAlignmentMarkersIsDetected() {
        String text = "| 左 | 中 | 右 |\n"
                + "|:---|:---:|---:|\n"
                + "| a | b | c |";
        assertTrue(MarkdownRenderDiagnostics.containsCompleteTable(text));
    }

    @Test
    public void snapshotDescribesAllCounters() {
        MarkdownRenderDiagnostics.Snapshot snapshot =
                new MarkdownRenderDiagnostics.Snapshot(12, 3, 5, 800, 120, 30, true);
        String text = snapshot.describe();
        assertTrue(text.contains("bindCount=12"));
        assertTrue(text.contains("shapeSwitch=3"));
        assertTrue(text.contains("heightChange=5"));
        assertTrue(text.contains("durationMs=800"));
        assertTrue(text.contains("tableSeen=1"));
    }
}
