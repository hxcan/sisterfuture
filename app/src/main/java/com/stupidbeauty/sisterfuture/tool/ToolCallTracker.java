package com.stupidbeauty.sisterfuture.tool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolCallTracker - 追踪已回复的 tool_call_id
 * 
 * 用于实现工具调用回复消息的幂等性，防止重复回复同一个 tool_call_id。
 * 使用 ConcurrentHashMap.newKeySet() 确保线程安全。
 */
public class ToolCallTracker {
    
    /**
     * 存储已回复的 callId 集合
     * 使用 ConcurrentHashMap.newKeySet() 确保线程安全
     */
    private final Set<String> repliedCallIds = ConcurrentHashMap.newKeySet();
    
    /**
     * 标记某个 callId 为已回复
     * 
     * @param callId 要标记的 callId
     * @return 如果标记成功返回 true，如果该 callId 已被标记过则返回 false
     */
    public boolean tryMarkAsReplied(String callId) {
        return repliedCallIds.add(callId);
    }
    
    /**
     * 清理单个已回复的 callId
     * 
     * @param callId 要清理的 callId
     */
    public void clearRepliedCallId(String callId) {
        repliedCallIds.remove(callId);
    }
    
    /**
     * 清空所有追踪的 callId
     */
    public void clearAll() {
        repliedCallIds.clear();
    }
    
    /**
     * 检查某个 callId 是否已被回复
     * 
     * @param callId 要检查的 callId
     * @return 如果已回复返回 true，否则返回 false
     */
    public boolean isReplied(String callId) {
        return repliedCallIds.contains(callId);
    }
    
    /**
     * 获取当前已追踪的 callId 数量
     * 
     * @return 已追踪的 callId 数量
     */
    public int getRepliedCount() {
        return repliedCallIds.size();
    }
}