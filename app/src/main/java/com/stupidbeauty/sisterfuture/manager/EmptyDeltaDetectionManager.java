package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.utils.FileLogger;
import android.util.Log;

/**
 * 空响应检测管理器
 * 
 * 用途：检测 AI 连续返回空响应（content 为空且无 tool_calls）的情况。
 * 当连续多次空响应时，判定为上下文超长，触发自动缩短上下文重试。
 * 
 * 解决问题：M3 模型上下文超长时，AI 可能返回 finish_reason="stop" 但 content 为空的响应，
 * 用户看到的就是"回复空白"。此管理器通过连续检测空响应来识别这种情况。
 */
public class EmptyDeltaDetectionManager
{
    private static final String TAG = "EmptyDeltaDetectionManager";
    
    private static volatile EmptyDeltaDetectionManager instance;
    
    /**
     * 连续空响应阈值：连续 N 次空响应则判定为上下文超长
     */
    private static final int EMPTY_RESPONSE_THRESHOLD = 3;
    
    /**
     * 连续空响应计数器
     */
    private int consecutiveEmptyCount = 0;
    
    /**
     * 标记是否已触发过上下文缩短（避免重复触发）
     */
    private boolean triggerAcknowledged = false;
    
    /**
     * 上次有内容响应时的上下文大小
     */
    private int lastValidContextSize = 0;
    
    public static EmptyDeltaDetectionManager getInstance()
    {
        if (instance == null)
        {
            synchronized (EmptyDeltaDetectionManager.class)
            {
                if (instance == null)
                {
                    instance = new EmptyDeltaDetectionManager();
                }
            }
        }
        return instance;
    }
    
    private EmptyDeltaDetectionManager()
    {
    }
    
    /**
     * 一行流式 API：记录响应并判断是否应触发上下文缩短
     * 
     * @param fullAnswer 完整的回复内容
     * @param hasToolCalls 是否包含工具调用
     * @param currentContextSize 当前上下文大小
     * @return true 表示应触发上下文缩短，false 表示正常
     */
    public boolean checkAndRecordResponse(String fullAnswer, boolean hasToolCalls, int currentContextSize)
    {
        boolean hasContent = (fullAnswer != null && !fullAnswer.isEmpty());
        
        // 有内容：重置计数器
        if (hasContent)
        {
            consecutiveEmptyCount = 0;
            lastValidContextSize = currentContextSize;
            triggerAcknowledged = false;
            return false;
        }
        
        // 有工具调用：不算空响应
        if (hasToolCalls)
        {
            consecutiveEmptyCount = 0;
            triggerAcknowledged = false;
            return false;
        }
        
        // 真正的空响应：递增计数器
        consecutiveEmptyCount++;
        FileLogger.w(TAG, "⚠️ [EMPTY_DELTA] 连续空响应 #" + consecutiveEmptyCount + " | contextSize=" + currentContextSize);
        
        // 达到阈值且未确认触发：判定为上下文超长
        if (consecutiveEmptyCount >= EMPTY_RESPONSE_THRESHOLD && !triggerAcknowledged)
        {
            FileLogger.e(TAG, "🚨 [CONTEXT_TOO_LONG_DETECTED] 连续 " + consecutiveEmptyCount + " 次空响应，判定为上下文超长");
            return true;
        }
        
        return false;
    }
    
    /**
     * 确认已触发上下文缩短，避免重复触发
     */
    public void acknowledgeTrigger()
    {
        triggerAcknowledged = true;
        consecutiveEmptyCount = 0;
        FileLogger.i(TAG, "✅ [TRIGGER_ACK] 上下文缩短触发已确认，重置计数器");
    }
    
    /**
     * 重置状态（手动调用，例如切换接入点后）
     */
    public void reset()
    {
        consecutiveEmptyCount = 0;
        triggerAcknowledged = false;
        lastValidContextSize = 0;
    }
    
    /**
     * 获取连续空响应计数（用于调试）
     */
    public int getConsecutiveEmptyCount()
    {
        return consecutiveEmptyCount;
    }
}
