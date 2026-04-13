      // 🔍 #759909257401 严厉模式：移除所有未匹配的 assistant+tool_calls 消息
      if (strictMode && pendingToolCallsObject != null)
      {
        cleanedCount = removePendingAssistantMessages(list, pendingToolCallsObject);
        FileLogger.i(TAG, "🔄 [TIMELINE_BRANCH] 创建新时间线，清理悬而未决的工具调用消息");
        FileLogger.i(TAG, "🗑️ [CLEANED] 共清理 " + cleanedCount + " 条未完成的工具调用消息");
        FileLogger.i(TAG, "📝 [INFO] 当前历史长度：" + list.size());
        
        // ✅ 严厉模式下需要显式保存清理后的历史
        saveHistory(list);
      }
      else if (pendingToolCallsObject != null)
      {
        list.add(pendingToolCallsObject);
        FileLogger.w(TAG, "[normalizeToolCallMessages] Pending assistant with tool_calls added at end, but some tool messages may be missing");
      }