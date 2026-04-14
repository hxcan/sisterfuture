      // 🔍 新增：记录输入历史的消息类型
      FileLogger.i(TAG, "📝 [INPUT] 开始处理输入历史，共 " + oldHistory.size() + " 条消息");
      for (int i = 0; i < oldHistory.size(); i++)
      {
        JSONObject msg = oldHistory.get(i);
        String role = msg.optString("role", "unknown");
        Object contentObj = msg.opt("content");
        String contentType = (contentObj instanceof JSONArray) ? "JSONArray" : "String";
        
        // ✅ 修复 #4886：添加 null 检查和类型安全处理
        int contentLength = 0;
        if (contentObj == null)
        {
          contentType = "null";
          contentLength = 0;
        }
        else if (contentObj instanceof String)
        {
          contentLength = ((String) contentObj).length();
        }
        else if (contentObj instanceof JSONArray)
        {
          contentLength = ((JSONArray) contentObj).length();
        }
        else
        {
          FileLogger.w(TAG, "[normalizeToolCallMessages] Unexpected content type: " + contentObj.getClass().getName());
          contentLength = 0;
        }
        
        FileLogger.i(TAG, "  [" + i + "] role=" + role + ", contentType=" + contentType + ", contentLength=" + contentLength);
        
        // 🖼️ 特别标记多模态消息
        if ("user".equals(role) && (contentObj instanceof JSONArray))
        {
          JSONArray contentArray = (JSONArray) contentObj;
          FileLogger.i(TAG, "🖼️ [MULTIMODAL] 检测到多模态用户消息，包含 " + contentArray.length() + " 个元素");
          for (int j = 0; j < contentArray.length(); j++)
          {
            JSONObject item = contentArray.optJSONObject(j);
            if (item != null)
            {
              String type = item.optString("type", "unknown");
              FileLogger.i(TAG, "    [" + j + "] type=" + type);
            }
          }
        }
      }