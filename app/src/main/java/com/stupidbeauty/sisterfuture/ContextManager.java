    try
    {
      JSONObject pendingToolCallsObject = null;
      List<String> matchedToolCallIds = new ArrayList<>();
      // ✅ 新增：暂存匹配的 tool 消息
      List<JSONObject> matchedToolMessages = new ArrayList<>();

      for (int i = 0; i < history.size(); i++)
      {