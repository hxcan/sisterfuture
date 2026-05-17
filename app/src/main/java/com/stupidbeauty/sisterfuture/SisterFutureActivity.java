// ... (previous code) ...
                  @Override
                  public void onError(Exception e)
                  {
                    // 🔍 [TOOL_ASYNC_ERROR_DEBUG] 异步工具失败回调详细日志
                    FileLogger.e(TAG, "🔧 [TOOL_ASYNC_ERROR] 异步工具失败 | id=" + toolCallId + " | name=" + toolName + " | errorType=" + e.getClass().getSimpleName() + " | errorMsg=" + e.getMessage(), e);
                    
                    synchronized (pendingResults)
                    {
                      FileLogger.d(TAG, "🔧 [TOOL_ERROR_HANDLER] 错误处理器触发 | pendingResultsSize=" + pendingResults.size() + " | toolCallsCount=" + toolCallsArray.length());
                      
                      // 🔍 检查 pendingResults 是否为空，如果是，说明这是唯一的工具或所有工具都已失败
                      if (pendingResults.isEmpty()) {
                        FileLogger.w(TAG, "⚠️ [TOOL_ERROR_EMPTY_PENDING] pendingResults 为空，即将调用 postProcessToolResults");
                      }
                      
                      postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                    }
                  }
// ... (rest of the code) ...