package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONObject;
import java.util.List;

/**
 * 工具回避检测管理器（Tool Avoidance Detection Manager）
 *
 * 功能：检测姐姐（SisterFuture）是否陷入"只会嘴上说但完全不调用工具"的污染状态，
 *       并执行区间级清理，恢复正常的工具调用能力。
 *
 * 核心问题（Persona Bleed 引起的 Tool Avoidance）：
 *   - 历史中某种回复风格（如"姐姐立刻..."）被反复使用
 *   - AI 学会模仿该风格，倾向于"嘴上说"而不是"调用工具"
 *   - 最终导致连续大量助手消息完全没有 tool_calls 字段
 *
 * 判定策略：全样本 4 段时序分析
 *   - 将历史平均分为 4 段（每段 25%）
 *   - 计算每段中"无 tool_calls 的助手消息"比例
 *   - 判定条件（满足任意 2 个即触发）：
 *     1. 4 段比例单调上升（r1 < r2 < r3 < r4，明显的恶化趋势）
 *     2. 末段（最新 25%）纯文本比例 >= 80%
 *     3. 连续 >= 20 条助手消息无 tool_calls
 *
 * 恢复策略：区间级清理
 *   - 找到最后一个有 tool_calls 的消息位置
 *   - 保留该位置 + 5 条上下文缓冲
 *   - 删除之后的所有"污染"消息
 *
 * @author sisterfuture
 * @since 2026-06-21
 * @task #820049914004
 */
public class ToolAvoidanceDetectionManager
{
    private static final String TAG = "ToolAvoidanceDetection";

    /** 最小历史长度阈值（低于此值不判定，避免新对话误判） */
    private static final int MIN_HISTORY_FOR_DETECTION = 200;

    /** 连续无工具调用的助手消息数阈值 */
    private static final int CONSECUTIVE_THRESHOLD = 20;

    /** 末段（最新 25%）纯文本比例阈值 */
    private static final double TAIL_RATIO_THRESHOLD = 0.8;

    /** 保留最后工具调用后的上下文条数（缓冲） */
    private static final int KEEP_AFTER_LAST_TOOL = 5;

    /** 4 段分段时使用的段数 */
    private static final int SEGMENT_COUNT = 4;

    /**
     * 判定当前历史是否存在 Tool Avoidance 趋势
     * @param history 完整的对话历史
     * @return true 表示检测到工具回避
     */
    public boolean detectToolAvoidance(List<JSONObject> history) {
        if (history == null || history.size() < MIN_HISTORY_FOR_DETECTION) {
            FileLogger.d(TAG, "历史长度不足 " + MIN_HISTORY_FOR_DETECTION + "，跳过检测");
            return false;
        }

        // 计算 4 段比例
        double r1 = calculateSegmentRatio(history, 0.0, 0.25);
        double r2 = calculateSegmentRatio(history, 0.25, 0.50);
        double r3 = calculateSegmentRatio(history, 0.50, 0.75);
        double r4 = calculateSegmentRatio(history, 0.75, 1.0);

        FileLogger.d(TAG, "分段比例 r1=" + r1 + " r2=" + r2 + " r3=" + r3 + " r4=" + r4);

        // 条件 1：4 段比例单调上升（明显的恶化趋势）
        boolean cond1 = r1 < r2 && r2 < r3 && r3 < r4;

        // 条件 2：末段纯文本比例 >= 80%
        boolean cond2 = r4 >= TAIL_RATIO_THRESHOLD;

        // 条件 3：连续 20+ 条无工具
        int consecutiveNoTool = countConsecutiveNoTool(history);
        boolean cond3 = consecutiveNoTool >= CONSECUTIVE_THRESHOLD;
        FileLogger.d(TAG, "连续无工具数: " + consecutiveNoTool);

        int matched = (cond1 ? 1 : 0) + (cond2 ? 1 : 0) + (cond3 ? 1 : 0);
        FileLogger.d(TAG, "条件满足数: " + matched + "/3");

        return matched >= 2;
    }

    /**
     * 计算指定段的纯文本（无 tool_calls）比例
     * @param startRatio 起始位置比例（0.0 - 1.0）
     * @param endRatio 结束位置比例（0.0 - 1.0）
     * @return 纯文本助手消息比例
     */
    private double calculateSegmentRatio(List<JSONObject> history, double startRatio, double endRatio) {
        int total = history.size();
        int startIndex = (int) Math.floor(total * startRatio);
        int endIndex = (int) Math.floor(total * endRatio);

        if (startIndex >= endIndex) return 0.0;

        int assistantCount = 0;
        int textOnlyCount = 0;

        for (int i = startIndex; i < endIndex; i++) {
            JSONObject msg = history.get(i);
            if (!"assistant".equals(msg.optString("role"))) continue;
            assistantCount++;
            if (!msg.has("tool_calls")) {
                textOnlyCount++;
            }
        }

        return assistantCount == 0 ? 0.0 : (double) textOnlyCount / assistantCount;
    }

    /**
     * 从末尾往前数连续无 tool_calls 的助手消息数
     */
    private int countConsecutiveNoTool(List<JSONObject> history) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            JSONObject msg = history.get(i);
            if (!"assistant".equals(msg.optString("role"))) continue;
            if (msg.has("tool_calls")) break;
            count++;
        }
        return count;
    }

    /**
     * 执行恢复：清理污染区间
     * @param history 完整历史（会被修改）
     * @return 被清理的消息数（0 表示无需清理）
     */
    public int recoverFromToolAvoidance(List<JSONObject> history) {
        if (!detectToolAvoidance(history)) {
            return 0;
        }

        // 找最后一个有 tool_calls 的消息位置
        int lastToolCallIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            JSONObject msg = history.get(i);
            if ("assistant".equals(msg.optString("role")) && msg.has("tool_calls")) {
                lastToolCallIndex = i;
                break;
            }
        }

        if (lastToolCallIndex < 0) {
            FileLogger.w(TAG, "未找到任何 tool_calls 消息，跳过清理");
            return 0;
        }

        // 保留到该位置 + 5 条（缓冲上下文）
        int keepUntil = Math.min(history.size(), lastToolCallIndex + KEEP_AFTER_LAST_TOOL);
        int removeCount = history.size() - keepUntil;

        if (removeCount > 0) {
            FileLogger.w(TAG, "🧹 [TOOL_AVOIDANCE_RECOVERY] 清理 " + removeCount + " 条污染消息 | 保留前 " + keepUntil + " 条");
            // 在调用方移除尾部
        }

        return removeCount;
    }

    /**
     * 在原 list 上执行清理（就地修改）
     * @param history 完整历史（会被修改）
     * @return 被清理的消息数
     */
    public int performCleanup(List<JSONObject> history) {
        int removeCount = recoverFromToolAvoidance(history);
        if (removeCount > 0 && history.size() > removeCount) {
            // 移除尾部
            int newSize = history.size() - removeCount;
            while (history.size() > newSize) {
                history.remove(history.size() - 1);
            }
        }
        return removeCount;
    }
}
