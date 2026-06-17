package com.stupidbeauty.sisterfuture.manager;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

import com.stupidbeauty.sisterfuture.bean.Delta;

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

    private static final int CONSECUTIVE_EMPTY_THRESHOLD = 2;
    private static final int CONTEXT_SIZE_THRESHOLD = 200;

    private final AtomicInteger consecutiveEmptyResponseCount = new AtomicInteger(0);
    private volatile boolean alreadyTriggered = false;

    /**
     * 检测并记录响应，在需要时触发上下文缩短
     *
     * @param delta 模型响应Delta
     * @param fullAnswer 完整回答文本
     * @param contextSize 当前上下文消息数量
     * @return true 表示触发了上下文缩短（调用方应 return）
     */
    public boolean checkAndRecordResponse(Delta delta, String fullAnswer, int contextSize) {
        boolean hasContent = !fullAnswer.isEmpty();
        boolean hasToolCalls = (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty());
        
        recordResponse(hasContent, hasToolCalls);
        
        if (shouldTriggerContextShorten(contextSize)) {
            return true;
        }
        return false;
    }

    private void recordResponse(boolean hasContent, boolean hasToolCalls) {
        if (hasToolCalls || hasContent) {
            if (consecutiveEmptyResponseCount.get() > 0) {
                consecutiveEmptyResponseCount.set(0);
            }
            return;
        }
        consecutiveEmptyResponseCount.incrementAndGet();
    }

    private boolean shouldTriggerContextShorten(int currentContextSize) {
        if (alreadyTriggered) return false;

        if (consecutiveEmptyResponseCount.get() >= CONSECUTIVE_EMPTY_THRESHOLD 
            && currentContextSize > CONTEXT_SIZE_THRESHOLD) {
            alreadyTriggered = true;
            return true;
        }
        return false;
    }

    public void acknowledgeTrigger() {
        alreadyTriggered = false;
    }

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

    private static volatile EmptyDeltaDetectionManager instance;
}
