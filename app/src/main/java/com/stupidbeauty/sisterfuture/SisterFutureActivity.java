  // 🔥 #4657 死循环救援模式标记
  private boolean isDeadlockRescueMode = false;
  
  // ⚠️ #4824 HTTP 429 限流重试计数器
  private int rateLimitRetryCount = 0;
  private static final int MAX_RATE_LIMIT_RETRIES = 3;

  // 🔍 #4997 请求 ID 追踪 - 过滤旧请求的错误回调
  private volatile long currentRequestId = 0;
  private volatile long lastSuccessRequestId = 0;

  @Override
  public void onInit(int arg0)
  {

  }