package com.stupidbeauty.sisterfuture.manager;

import android.util.Log;

/**
 * 连续空 Delta 检测管理器
 *
 * 用于检测 SSE 流中 delta.content 连续为空的情况，并判定是否需要触发上下文缩短。
 * 当 M3 等思考型模型在上下文超长时，可能因为思考过程消耗大量 token，
 * 导致 delta.content 连续为空（只有 reasoning_content）。这种情况下，
 * 应当判定为"上下文超长"并触发自动缩短机制。
 *
 * 触发条件（需要同时满足）：
 * 1. 连续 N 次（默认 2 次）delta.content 为空
 * 2. 当前上下文消息总数 > M（默认 200）
 *
 * @author SisterFuture Team
 * @since 2026-06-17
 */
public class EmptyDeltaDetectionManager {

    private static final String TAG = "EmptyDeltaDetectionManager";

    /**
     * 连续空 delta 的阈值
     * 当连续检测到该次数的空 delta 时，可能触发上下文缩短
     */
    private static final int CONSECUTIVE_EMPTY_THRESHOLD = 2;

    /**
     * 上下文消息数量的阈值
     * 只有当消息总数超过此阈值时，才认为可能存在"上下文超长"问题
     */
    private static final int CONTEXT_SIZE_THRESHOLD = 200;

    /**
     * 当前连续空 delta 的计数
     */
    private int consecutiveEmptyCount = 0;

    /**
     * 是否已经触发了缩短操作
     * 防止在同一次请求中重复触发
     */
    private boolean alreadyTriggered = false;

    /**
     * 记录一次空 delta
     */
    public void recordEmptyDelta() {
        consecutiveEmptyCount++;
        Log.d(TAG, "📊 [EMPTY_DELTA] 记录空 delta | 当前连续次数=" + consecutiveEmptyCount);
    }

    /**
     * 记录一次有内容的 delta
     * 一旦检测到有内容，重置连续空计数
     */
    public void recordContentDelta() {
        if (consecutiveEmptyCount > 0) {
            Log.d(TAG, "✅ [CONTENT_DELTA] 检测到内容 delta，重置连续空计数 | 原计数=" + consecutiveEmptyCount);
        }
        consecutiveEmptyCount = 0;
    }

    /**
     * 判定是否应该触发上下文缩短
     *
     * @param currentContextSize 当前上下文消息总数
     * @return 如果同时满足"连续空 delta >= 阈值"和"上下文 > 阈值"，返回 true
     */
    public boolean shouldTriggerContextShorten(int currentContextSize) {
        if (alreadyTriggered) {
            return false; // 已经触发过，不再重复触发
        }

        boolean emptyCountReached = consecutiveEmptyCount >= CONSECUTIVE_EMPTY_THRESHOLD;
        boolean contextSizeReached = currentContextSize > CONTEXT_SIZE_THRESHOLD;

        if (emptyCountReached && contextSizeReached) {
            Log.w(TAG, "🚨 [TRIGGER] 满足上下文缩短触发条件 | 连续空次数=" + consecutiveEmptyCount +
                    " (>= " + CONSECUTIVE_EMPTY_THRESHOLD + ") | 上下文大小=" + currentContextSize +
                    " (> " + CONTEXT_SIZE_THRESHOLD + ")");
            alreadyTriggered = true; // 标记已触发
            return true;
        }

        return false;
    }

    /**
     * 重置检测器状态
     * 在请求成功完成或开始新请求时调用
     */
    public void reset() {
        if (consecutiveEmptyCount > 0 || alreadyTriggered) {
            Log.d(TAG, "🔄 [RESET] 重置检测器状态 | 原连续空次数=" + consecutiveEmptyCount + " | 原已触发=" + alreadyTriggered);
        }
        consecutiveEmptyCount = 0;
        alreadyTriggered = false;
    }

    /**
     * 获取当前连续空 delta 次数（用于调试和日志）
     */
    public int getConsecutiveEmptyCount() {
        return consecutiveEmptyCount;
    }

    /**
     * 获取上下文大小阈值（用于调试和测试）
     */
    public static int getContextSizeThreshold() {
        return CONTEXT_SIZE_THRESHOLD;
    }

    /**
     * 获取连续空 delta 阈值（用于调试和测试）
     */
    public static int getConsecutiveEmptyThreshold() {
        return CONSECUTIVE_EMPTY_THRESHOLD;
    }

    /**
     * 私有构造函数，防止实例化
     */
    private EmptyDeltaDetectionManager() {
        // 工具类不应被实例化
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
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
