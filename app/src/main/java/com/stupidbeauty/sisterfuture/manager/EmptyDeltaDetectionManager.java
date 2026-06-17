package com.stupidbeauty.sisterfuture.manager;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连续空响应检测管理器
 *
 * 用于跨请求跟踪"连续空响应"的情况，并判定是否需要触发上下文缩短。
 * 当 M3 等思考型模型在上下文超长时，可能因为思考过程消耗大量 token，
 * 导致整个响应中 delta.content 都是空的（只有 reasoning_content）。
 * 多次连续出现这种"空响应"，就应当判定为"上下文超长"并触发自动缩短。
 *
 * 重要设计：
 * - 统计单位是"请求"，不是"delta"（一次请求中多次空 delta 只算一次）
 * - 跨请求累积统计（不会在每次请求前 reset）
 * - 排除 tool_call 干扰（带 tool_calls 的响应不算空）
 * - 阈值：连续 N 次（默认 2 次）空响应 + 上下文 > M（默认 200）
 *
 * @author SisterFuture Team
 * @since 2026-06-17
 */
public class EmptyDeltaDetectionManager {

    private static final String TAG = "EmptyDeltaDetectionManager";

    /**
     * 连续空响应的阈值
     * 当连续 N 次请求的响应为空时，触发上下文缩短
     */
    private static final int CONSECUTIVE_EMPTY_THRESHOLD = 2;

    /**
     * 上下文消息数量的阈值
     * 只有当消息总数超过此阈值时，才认为可能存在"上下文超长"问题
     */
    private static final int CONTEXT_SIZE_THRESHOLD = 200;

    /**
     * 当前连续空响应的计数（跨请求累积）
     */
    private final AtomicInteger consecutiveEmptyResponseCount = new AtomicInteger(0);

    /**
     * 是否已经触发了缩短操作
     * 防止重复触发，直到外部调用 acknowledgeTrigger() 或 reset() 才解除
     */
    private volatile boolean alreadyTriggered = false;

    /**
     * 记录一次请求的响应情况
     *
     * @param hasContent 响应中是否有 content（文本内容）
     * @param hasToolCalls 响应中是否有 tool_calls（工具调用）
     */
    public void recordResponse(boolean hasContent, boolean hasToolCalls) {
        // 如果响应有 tool_calls，不算空响应（这是正常的工具调用流程）
        if (hasToolCalls) {
            if (consecutiveEmptyResponseCount.get() > 0) {
                Log.d(TAG, "🔧 [TOOL_CALL_RESPONSE] 检测到 tool_call 响应，重置连续空响应计数 | 原计数=" + consecutiveEmptyResponseCount.get());
            }
            consecutiveEmptyResponseCount.set(0);
            return;
        }

        // 如果响应有 content（即使是思考后的简短回复），也不算空
        if (hasContent) {
            if (consecutiveEmptyResponseCount.get() > 0) {
                Log.d(TAG, "✅ [CONTENT_RESPONSE] 检测到 content 响应，重置连续空响应计数 | 原计数=" + consecutiveEmptyResponseCount.get());
            }
            consecutiveEmptyResponseCount.set(0);
            return;
        }

        // 既无 content 也无 tool_calls → 真正的空响应
        int newCount = consecutiveEmptyResponseCount.incrementAndGet();
        Log.w(TAG, "📊 [EMPTY_RESPONSE] 记录空响应 | 当前连续次数=" + newCount);
    }

    /**
     * 判定是否应该触发上下文缩短
     *
     * @param currentContextSize 当前上下文消息总数
     * @return 如果同时满足"连续空响应 >= 阈值"和"上下文 > 阈值"，返回 true
     */
    public boolean shouldTriggerContextShorten(int currentContextSize) {
        if (alreadyTriggered) {
            return false; // 已经触发过，等待外部确认
        }

        int currentEmptyCount = consecutiveEmptyResponseCount.get();
        boolean emptyCountReached = currentEmptyCount >= CONSECUTIVE_EMPTY_THRESHOLD;
        boolean contextSizeReached = currentContextSize > CONTEXT_SIZE_THRESHOLD;

        if (emptyCountReached && contextSizeReached) {
            Log.w(TAG, "🚨 [TRIGGER] 满足上下文缩短触发条件 | 连续空响应次数=" + currentEmptyCount +
                    " (>= " + CONSECUTIVE_EMPTY_THRESHOLD + ") | 上下文大小=" + currentContextSize +
                    " (> " + CONTEXT_SIZE_THRESHOLD + ")");
            alreadyTriggered = true; // 标记已触发，防止重复
            return true;
        }

        return false;
    }

    /**
     * 确认已处理触发（例如调用方已成功执行上下文缩短后调用）
     * 重置触发状态，允许下次重新判定
     */
    public void acknowledgeTrigger() {
        Log.d(TAG, "✅ [ACK] 确认触发已处理，重置触发状态 | 保留连续空响应计数=" + consecutiveEmptyResponseCount.get());
        alreadyTriggered = false;
    }

    /**
     * 强制重置所有状态（仅在必要时使用，如手动清理）
     */
    public void forceReset() {
        Log.d(TAG, "🔄 [FORCE_RESET] 强制重置检测器状态 | 原连续空响应次数=" + consecutiveEmptyResponseCount.get() + " | 原已触发=" + alreadyTriggered);
        consecutiveEmptyResponseCount.set(0);
        alreadyTriggered = false;
    }

    /**
     * 获取当前连续空响应次数（用于调试和日志）
     */
    public int getConsecutiveEmptyResponseCount() {
        return consecutiveEmptyResponseCount.get();
    }

    /**
     * 获取上下文大小阈值（用于调试和测试）
     */
    public static int getContextSizeThreshold() {
        return CONTEXT_SIZE_THRESHOLD;
    }

    /**
     * 获取连续空响应阈值（用于调试和测试）
     */
    public static int getConsecutiveEmptyThreshold() {
        return CONSECUTIVE_EMPTY_THRESHOLD;
    }

    // ========== 单例模式 ==========

    private static volatile EmptyDeltaDetectionManager instance;

    /**
     * 获取单例实例
     */
    public static EmptyDeltaDetectionManager getInstance() {
        if (instance == null) {
            synchronized (EmptyDeltaDetectionManager.class) {
                if (instance == null) {
                    instance = new EmptyDeltaDetectionManager();
                }
            }
        }
        return instance;
    }
}