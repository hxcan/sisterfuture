// 增强调试日志 - 追踪并发请求来源
// 在 sendChatRequest() 方法中添加

private void sendChatRequest(String userMessage) {
    // [REQUEST_ENTRY] 记录进入点
    String threadName = Thread.currentThread().getName();
    long threadId = Thread.currentThread().getId();
    FileLogger.d(TAG, "🔍 [REQUEST_ENTRY] 进入 sendChatRequest, thread=" + threadName + "(" + threadId + "), isRequestInProgress=" + isRequestInProgress);
    
    if (isRequestInProgress) {
        FileLogger.w(TAG, "⚠️ [REQUEST_LOCK] 请求锁已占用，拒绝并发请求: thread=" + threadName);
        return;
    }
    
    // [REQUEST_LOCK_BEFORE_SET] 设置锁前快照
    FileLogger.d(TAG, "🔒 [REQUEST_LOCK_BEFORE_SET] thread=" + threadName + ", isRequestInProgress=" + isRequestInProgress);
    isRequestInProgress = true;
    FileLogger.d(TAG, "🔒 [REQUEST_LOCK] 请求锁已设置：true, thread=" + threadName);
    
    try {
        // ... 原有请求逻辑 ...
        
    } catch (Exception e) {
        // ... 错误处理 ...
        
    } finally {
        // [REQUEST_LOCK_BEFORE_RELEASE] 释放锁前状态
        FileLogger.d(TAG, "🔓 [REQUEST_LOCK_BEFORE_RELEASE] thread=" + threadName + ", isRequestInProgress=" + isRequestInProgress);
        isRequestInProgress = false;
        FileLogger.d(TAG, "🔓 [REQUEST_LOCK] 请求锁已释放：false, thread=" + threadName);
    }
}

// 在 onError() 回调中重试时添加堆栈跟踪
@Override
public void onError(Throwable t) {
    String threadName = Thread.currentThread().getName();
    FileLogger.e(TAG, "❌ [ON_ERROR] 请求失败, thread=" + threadName);
    FileLogger.e(TAG, "📋 [RETRY_CALL] 即将发起重试，当前堆栈:\n" + Log.getStackTraceString(new Exception()));
    
    // 延迟重试以避免快速递归
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
        FileLogger.d(TAG, "🔄 [RETRY_ATTEMPT] 开始重试, thread=" + Thread.currentThread().getName());
        sendChatRequest(lastUserMessage);
    }, 1000); // 1 秒延迟
}

// 在工具调用完成后的重试逻辑中添加标记
private void postProcessToolResults() {
    FileLogger.d(TAG, "🔧 [TOOL_RETRY] 工具处理完成，准备重试对话, thread=" + Thread.currentThread().getName());
    FileLogger.d(TAG, "📋 [TOOL_RETRY_STACK] 调用堆栈:\n" + Log.getStackTraceString(new Exception()));
    
    // ... 原有重试逻辑 ...
}
