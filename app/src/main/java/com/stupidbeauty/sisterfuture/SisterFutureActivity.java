  private void sendChatRequestTongYi()
  {
    // 🔍 #4997【阶段 3】生成请求 ID 用于追踪
    final long requestId = System.currentTimeMillis();
    currentRequestId = requestId;
    FileLogger.d(TAG, "🆔 [REQUEST_ID] 开始发送请求 #" + requestId + " | 当前接入点索引：" + modelAccessPointManager.getCurrentAccessPointIndex());
    
    // 🔍 #4997 救援调试：请求发起前记录状态
    FileLogger.d(TAG, "📊 [FAILURE_COUNT] 当前连续失败次数：" + modelAccessPointManager.getConsecutiveFailures());
    FileLogger.d(TAG, "开始发送请求，当前接入点：" + modelAccessPointManager.getCurrentAccessPoint().getName());
    
    // #4895 更新通知状态：正在发送请求
    SisterFutureService.updateNotificationStatus(this, "正在发送请求...");

    // 🔥 #4657 请求前检查是否超过阈值
    if (modelAccessPointManager.checkFailureThreshold()) {
      FileLogger.e(TAG, "🚨 [DEADLOCK_RESCUE] 检测到连续失败超过阈值！触发救援模式");
      FileLogger.d(TAG, "⚠️ [RESCUE_MODE] 进入救援模式：true");
      isDeadlockRescueMode = true; // ✅ 设置救援模式
      runOnUiThread(() -> {
        Toast.makeText(SisterFutureActivity.this, 
          "⚠️ 所有接入点连续失败，正在启动备用接入点配置向导...", 
          Toast.LENGTH_LONG).show();
        
        guideManager.showAddAccessPointGuideForDeadlock(new GuideManager.ChatCallback() {
          @Override
          public void onResponse(String message) {
            messageAdapter.addMessage(new MessageItem(message, MessageType.AI));
            scrollToBottom();
            ttsSayReply(message);
            // 如果响应包含成功标记，退出救援模式并重置计数器
            if (response.contains("✅")) {
              FileLogger.i(TAG, "✅ [BACKUP_AP_CREATED] 备用接入点配置成功，退出救援模式");
              isDeadlockRescueMode = false;
              FileLogger.d(TAG, "ℹ️ [RESCUE_MODE] 退出救援模式：false");
              // ✅ #4657 救援成功后重置计数器，防止立即再次触发
              modelAccessPointManager.resetFailureCount();
              FileLogger.i(TAG, "✅ [FAILURE_RESET] 救援成功，计数器已重置：" + modelAccessPointManager.getConsecutiveFailures());
            }
          }

          @Override
          public void onError(String error) {
            messageAdapter.addMessage(new MessageItem(error, MessageType.AI));
            scrollToBottom();
          }
        });
      });
      return; // 阻止继续请求
    }

    if (voiceRecognizeResultString != null && !voiceRecognizeResultString.isEmpty())
    {
      accumulatedAnswer.setLength(0);
      showThinkingOverlay();

      JSONArray historyArray = contextManager.getMessagesArray();
      JSONArray messagesArray = new JSONArray();

      try
      {
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this);
        systemMsg.put("content", enhancedSystemPrompt);
        messagesArray.put(systemMsg);

        for (int i = 0; i < historyArray.length(); i++)
        {
          messagesArray.put(historyArray.getJSONObject(i));
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();

        try
        {
          messagesArray = new JSONArray();
          String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this);

          messagesArray.put(new JSONObject().put("role", "system").put("content", enhancedSystemPrompt));
          messagesArray.put(new JSONObject().put("role", "user").put("content", voiceRecognizeResultString));
        }
        catch (Exception ignored)
        {
        }
      }

      // #4962 发送请求前输出完整上下文历史（仅统计行数）
      contextManager.logFullHistory("BeforeSendRequest");

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          // #4895 更新通知状态：收到响应，正在解析
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在生成回复...");
          
          // ✅ #4997【阶段 3】记录成功响应并更新 lastSuccessRequestId
          FileLogger.d(TAG, "✅ [SUCCESS] 请求 #" + requestId + " 成功响应 | 更新 lastSuccessRequestId=" + requestId);
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
            FileLogger.d(TAG, "🔥 [FAILURE_COUNT] 当前接入点索引：" + modelAccessPointManager.getCurrentAccessPointIndex() + 
                          " / 阈值：" + (modelAccessPointManager.getAccessPointCount() * 2));
            
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