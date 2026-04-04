  private void handleRateLimitError() {
    if (rateLimitRetryCount >= MAX_RATE_LIMIT_RETRIES) {
      FileLogger.e(TAG, "❌ [RATE_LIMIT] 限流重试次数过多（" + rateLimitRetryCount + " >= " + MAX_RATE_LIMIT_RETRIES + "），切换接入点");
      rateLimitRetryCount = 0;
      
      // 🔥 #4824 重试失败后切换接入点
      int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
      FileLogger.w(TAG, "🔥 [FAILURE_COUNT] 限流导致接入点标记为不可用，计数器：" + failures);
      
      FileLogger.i(TAG, "🔄 [ACCESS_POINT_SWITCH] 限流重试失败，切换到下一个接入点");
      sendChatRequestTongYi();
      return;
    }
    
    int delayMs = 1000 * (1 << rateLimitRetryCount);
    FileLogger.w(TAG, "⏳ [RATE_LIMIT] 限流重试 #" + rateLimitRetryCount + "，等待 " + delayMs + "ms");
    
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      rateLimitRetryCount++;
      sendChatRequestTongYi();
    }, delayMs);
  }