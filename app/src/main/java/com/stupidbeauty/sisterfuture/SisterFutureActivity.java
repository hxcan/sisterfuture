      // 🌌 #759909257401 平行宇宙时间线理论：发送请求前清理悬而未决的工具调用
      FileLogger.d(TAG, "🌌 [TIMELINE_BRANCH] 准备创建新时间线，调用严厉模式 normalize");
      
      // 获取当前历史并应用严厉模式清理
      List<JSONObject> history = contextManager.getHistory();
      List<JSONObject> cleanedHistory = contextManager.normalizeToolCallMessages(history, true);
      
      // saveHistory 已经在 normalizeToolCallMessages 内部调用了，无需重复保存
      
      JSONArray historyArray = contextManager.getMessagesArray();