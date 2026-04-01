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
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          // #4895 更新通知状态：收到响应，正在解析
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在生成回复...");
          
          // ✅ #4997【阶段 3】记录成功响应并更新 lastSuccessRequestId
          lastSuccessRequestId = requestId;
          
          parseTongYiResponse(response);
        }

        @Override
        public void onError(Exception error)
        {
          // 🔍 #4997【阶段 3】检查是否为旧请求的错误回调
          FileLogger.d(TAG, "❌ [ERROR_CHECK] 请求 #" + requestId + " 错误 | lastSuccessRequestId=" + lastSuccessRequestId + " | 忽略=" + (requestId < lastSuccessRequestId));
          
          if (requestId < lastSuccessRequestId) {
            FileLogger.w(TAG, "⚠️ [IGNORED] 忽略旧请求 #" + requestId + " 的错误回调（lastSuccessRequestId=" + lastSuccessRequestId + "）");
            return; // 忽略旧请求的错误
          }
          
          FileLogger.e(TAG, "请求出错：" + error.getClass().getSimpleName() + " - " + error.getMessage());
          hideThinkingOverlay();
          
          // #4895 更新通知状态：请求出错
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "请求出错，请重试");

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            FileLogger.d(TAG, "接入点不可用异常，准备切换");
            isAccessPointUnavailable = true;
          }
          // ⚠️ #4824 处理 HTTP 429 限流错误
          else if (error instanceof TongYiClient.RateLimitException) {
            FileLogger.w(TAG, "⚠️ [RATE_LIMIT] 限流错误，等待后重试 #" + rateLimitRetryCount);
            handleRateLimitError();
            return;
          }
          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            if (response != null) {
              int statusCode = response.code();
              FileLogger.d(TAG, "HTTP 响应异常，状态码：" + statusCode);
              
              if (statusCode == 401 || statusCode == 403 || statusCode == 500 || statusCode == 503) {
                FileLogger.d(TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换");
                isAccessPointUnavailable = true;
              }
              // ✅ #4823 新增：HTTP 400 → 检查是否上下文超长
              else if (statusCode == 400) {
                String errorBody = responseException.getCustomMessage();
                if (ContextLengthUtils.isContextLengthError(errorBody)) {
                  // ✅ #4829 使用统一处理方法（缩短后重试）
                  handleContextLengthError(errorBody, true);
                  return; // 直接返回，不继续处理
                }
              }
            }
            
            // ✅ 修复：避免重复读取 ResponseBody（OkHttp 只能读取一次）
            String errorBody = responseException.getCustomMessage();
            FileLogger.e(TAG, "HTTP " + (response != null ? response.code() : 0) + ": " + errorBody);
            
            if (isHtmlResponse(errorBody))
            {
              FileLogger.e(TAG, "API 返回 HTML 页面，防止崩溃");
              runOnUiThread(() ->
              {
                messageAdapter.addMessage(new MessageItem("API 返回 HTML 页面", MessageType.AI));
                scrollToBottom();
              });
              return;
            }
          }
          else
          {
            FileLogger.e(TAG, "未知异常，不触发切换：" + error.getMessage());
          }

          if (isAccessPointUnavailable)
          {
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            FileLogger.w(TAG, "🔥 [FAILURE_COUNT] 接入点不可用，计数器递增：" + failures);
            
            // 🔍 #4997【阶段 3】记录重试
            FileLogger.d(TAG, "🔄 [RETRY] 准备重试，thread=" + Thread.currentThread().getName());
            
            sendChatRequestTongYi(); // 继续重试
          }
          else
          {
            // ✅ [FAILURE_RESET] 非接入点错误，重置失败计数器
            modelAccessPointManager.resetFailureCount();
          }
        }
      },
      () ->
        {
        }
      );
    }
    else
    {
      FileLogger.w(TAG, "语音识别结果为空");
    }
  }