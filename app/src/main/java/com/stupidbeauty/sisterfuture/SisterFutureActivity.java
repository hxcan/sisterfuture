      // #4962 发送请求前输出完整上下文历史（仅统计行数）
      contextManager.logFullHistory("BeforeSendRequest");

      // 🔍 #5030【救援模式】遍历消息列表，检查所有 tool_call 的 arguments
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 开始检查消息列表中的 tool_call arguments | 消息总数：" + messagesArray.length());
      for (int i = 0; i < messagesArray.length(); i++) {
        try {
          JSONObject msg = messagesArray.getJSONObject(i);
          String role = msg.optString("role", "unknown");
          
          // 检查 tool 角色的消息
          if ("tool".equals(role)) {
            String toolCallId = msg.optString("tool_call_id", "unknown");
            String toolName = msg.optString("name", "unknown_tool");
            String content = msg.optString("content", "");
            
            FileLogger.w(TAG, "🔧 [TOOL_MESSAGE] 索引=" + i + 
                ", role=tool, tool_call_id=" + toolCallId + 
                ", name=" + toolName);
            FileLogger.d(TAG, "   📄 [TOOL_CONTENT] content 长度=" + content.length());
            
            // 尝试解析 content 是否为有效 JSON
            try {
              new JSONObject(content);
              FileLogger.d(TAG, "   ✅ [JSON_VALID] content 是有效的 JSON");
            } catch (JSONException e) {
              FileLogger.e(TAG, "   ❌ [JSON_INVALID] content 不是有效的 JSON! Error: " + e.getMessage());
              FileLogger.e(TAG, "   📋 [RAW_CONTENT] 原始内容：" + content);
            }
          }
          
          // 检查 assistant 消息中的 tool_calls
          if ("assistant".equals(role)) {
            JSONArray toolCalls = msg.optJSONArray("tool_calls");
            if (toolCalls != null && toolCalls.length() > 0) {
              FileLogger.w(TAG, "🤖 [ASSISTANT_MESSAGE] 索引=" + i + 
                  ", role=assistant, tool_calls 数量=" + toolCalls.length());
              
              for (int j = 0; j < toolCalls.length(); j++) {
                JSONObject toolCall = toolCalls.getJSONObject(j);
                String id = toolCall.optString("id", "unknown");
                JSONObject func = toolCall.optJSONObject("function");
                
                if (func != null) {
                  String funcName = func.optString("name", "unknown_function");
                  String args = func.optString("arguments", "");
                  
                  FileLogger.w(TAG, "   🔧 [TOOL_CALL] 索引=" + j + 
                      ", id=" + id + ", name=" + funcName);
                  FileLogger.d(TAG, "      📄 [ARGUMENTS] arguments 长度=" + args.length());
                  
                  // 尝试解析 arguments 是否为有效 JSON
                  try {
                    new JSONObject(args);
                    FileLogger.d(TAG, "      ✅ [JSON_VALID] arguments 是有效的 JSON");
                  } catch (JSONException e) {
                    FileLogger.e(TAG, "      ❌ [JSON_INVALID] arguments 不是有效的 JSON! Error: " + e.getMessage());
                    FileLogger.e(TAG, "      📋 [RAW_ARGS] 原始参数：" + args);
                  }
                }
              }
            }
          }
        } catch (JSONException e) {
          FileLogger.e(TAG, "❌ [PARSE_ERROR] 解析消息 #" + i + " 失败", e);
        }
      }
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 消息列表检查完成");

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()