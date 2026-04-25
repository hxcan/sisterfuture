      // 🔗 生成预留消息 ID
      String currentReservedMessageId = contextManager.reserveMessageId();
      FileLogger.i(TAG, "🔗 [RESERVE_ID] 已生成预留消息 ID | requestId=" + requestId + " | messageId=" + currentReservedMessageId);
      
      // 📤 发送请求前记录
      FileLogger.i(TAG, "📤 [SENDING] 开始发送 " + messagesArray.length() + " 条消息给 AI 服务");

      // 🔗 调用带 messageId 的新方法
      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在生成回复...");
          
          lastSuccessRequestId = requestId;
          
          parseTongYiResponse(response);
        }

        @Override
        public void onError(Exception error)
        {
          FileLogger.d(TAG, "❌ [ERROR_CHECK] 请求 #" + requestId + " 错误 | lastSuccessRequestId=" + lastSuccessRequestId + " | 忽略=" + (requestId < lastSuccessRequestId));
          
          if (requestId < lastSuccessRequestId) {
            FileLogger.w(TAG, "⚠️ [IGNORED] 忽略旧请求 #" + requestId + " 的错误回调（lastSuccessRequestId=" + lastSuccessRequestId + "）");
            return;
          }
          
          // ❌ 记录 AI 错误
          String errorType = error.getClass().getSimpleName();
          String errorMsg = error.getMessage();
          FileLogger.e(TAG, "❌ [AI_ERROR] AI 响应错误 | 错误类型=" + errorType + " | 错误信息=" + errorMsg);
          
          FileLogger.e(TAG, "请求出错：" + errorType + " - " + errorMsg);
          hideThinkingOverlay();
          
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "请求出错，请重试");

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            FileLogger.d(TAG, "接入点不可用异常，准备切换");
            isAccessPointUnavailable = true;
          }
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
              else if (statusCode == 400) {
                String errorBody = responseException.getCustomMessage();
                if (ContextLengthUtils.isContextLengthError(errorBody)) {
                  handleContextLengthError(errorBody, true);
                  return;
                }
              }
            }
            
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
            
            FileLogger.d(TAG, "🔄 [RETRY] 准备重试，thread=" + Thread.currentThread().getName());
            
            sendChatRequestTongYi();
          }
          else
          {
            modelAccessPointManager.resetFailureCount();
          }
        }
      },
      () ->
        {
        },
        currentReservedMessageId // 🔗 传递预留的消息 ID
      );