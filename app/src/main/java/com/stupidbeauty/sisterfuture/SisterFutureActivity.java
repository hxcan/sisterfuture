      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          parseTongYiResponse(response);
        }

        @Override
        public void onError(Exception error)
        {
          Log.e(TAG, "请求出错：" + error.getClass().getSimpleName() + " - " + error.getMessage());
          hideThinkingOverlay();

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            Log.d(TAG, "接入点不可用异常，准备切换");
            isAccessPointUnavailable = true;
          }
          // ⚠️ #4824 处理 HTTP 429 限流错误
          else if (error instanceof TongYiClient.RateLimitException) {
            Log.w(TAG, "⚠️ 限流错误，等待后重试 #" + rateLimitRetryCount);
            handleRateLimitError();
            return;
          }
          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            if (response != null) {
              int statusCode = response.code();
              Log.d(TAG, "HTTP 响应异常，状态码：" + statusCode);
              
              if (statusCode == 401 || statusCode == 403 || statusCode == 500 || statusCode == 503) {
                Log.d(TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换");
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
            Log.e(TAG, "HTTP " + (response != null ? response.code() : 0) + ": " + errorBody);
            
            if (isHtmlResponse(errorBody))
            {
              Log.e(TAG, "API 返回 HTML 页面，防止崩溃");
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
            Log.e(TAG, "未知异常，不触发切换：" + error.getMessage());
          }

          if (isAccessPointUnavailable)
          {
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            Log.d(TAG, "🔥 接入点不可用，切换并重试，当前失败次数：" + failures);
            sendChatRequestTongYi(); // 继续重试
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
      Log.w(TAG, "语音识别结果为空");
    }
  }

  // 🔍 #4839 调试：输出请求内容，检查 tool_calls 参数格式
  private void logRequestMessages(JSONArray messagesArray)
  {
    Log.d(TAG, "📋 请求消息总数：" + messagesArray.length());
    for (int i = 0; i < messagesArray.length(); i++) {
      try {
        JSONObject msg = messagesArray.getJSONObject(i);
        String role = msg.optString("role", "unknown");
        Log.d(TAG, "  消息[" + i + "] role=" + role);
        
        // 检查 assistant 消息中的 tool_calls
        if ("assistant".equals(role) && msg.has("tool_calls")) {
          JSONArray toolCalls = msg.getJSONArray("tool_calls");
          Log.d(TAG, "    🔧 tool_calls 数量：" + toolCalls.length());
          for (int j = 0; j < toolCalls.length(); j++) {
            JSONObject toolCall = toolCalls.getJSONObject(j);
            JSONObject func = toolCall.optJSONObject("function");
            if (func != null) {
              String funcName = func.optString("name", "unknown");
              Object args = func.opt("arguments");
              String argsType = (args == null) ? "null" : args.getClass().getSimpleName();
              String argsValue = (args == null) ? "null" : args.toString();
              Log.d(TAG, "      tool_call[" + j + "] name=" + funcName + ", arguments 类型=" + argsType);
              Log.d(TAG, "      arguments 值：" + argsValue);
              
              // ⚠️ 检测类型
              if (args instanceof String) {
                Log.w(TAG, "      ⚠️ 警告：arguments 是 String 类型，Code 模型可能拒绝！");
              } else if (args instanceof JSONObject) {
                Log.d(TAG, "      ✅ arguments 是 JSONObject 类型，格式正确");
              }
            }
          }
        }
      } catch (Exception e) {
        Log.e(TAG, "  解析消息[" + i + "] 失败", e);
      }
    }
  }