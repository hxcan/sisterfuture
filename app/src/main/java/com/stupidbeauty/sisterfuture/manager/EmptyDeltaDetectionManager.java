package com.stupidbeauty.sisterfuture.manager;

import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连续空响应检测管理器
 * 阈值：连续空响应 >= 2 次 且 上下文 > 200 条
 */
public class EmptyDeltaDetectionManager {
    private static final String TAG = "EmptyDeltaDetectionManager";
    private static final int CONSECUTIVE_EMPTY_THRESHOLD = 2;
    private static final int CONTEXT_SIZE_THRESHOLD = 200;

    private final AtomicInteger consecutiveEmptyResponseCount = new AtomicInteger(0);
    private volatile boolean alreadyTriggered = false;

    /** 检测并记录响应，返回是否触发缩短 */
    public boolean checkAndRecordResponse(String fullAnswer, boolean hasToolCalls, int contextSize) {
        boolean hasContent = !fullAnswer.isEmpty();
        if (hasToolCalls || hasContent) {
            consecutiveEmptyResponseCount.set(0);
            return false;
        }
        consecutiveEmptyResponseCount.incrementAndGet();
        if (alreadyTriggered) return false;
        if (consecutiveEmptyResponseCount.get() >= CONSECUTIVE_EMPTY_THRESHOLD && contextSize > CONTEXT_SIZE_THRESHOLD) {
            alreadyTriggered = true;
            return true;
        }
        return false;
    }

    public void acknowledgeTrigger() { alreadyTriggered = false; }

    public static EmptyDeltaDetectionManager getInstance() {
        if (instance == null) {
            synchronized (EmptyDeltaDetectionManager.class) {
                if (instance == null) instance = new EmptyDeltaDetectionManager();
            }
        }
        return instance;
    }

    private static volatile EmptyDeltaDetectionManager instance;
}
