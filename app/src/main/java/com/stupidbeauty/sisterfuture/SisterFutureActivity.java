              else if (statusCode == 400) {
                String errorBody = responseException.getCustomMessage();
                if (ContextLengthUtils.isContextLengthError(errorBody)) {
                  Log.w(TAG, "🔍 检测到上下文超长错误（HTTP 400），自动缩短上下文");
                  
                  // ✅ #4829 统一处理逻辑：提取为独立方法
                  shortenContextAndRetry(errorBody);
                  return; // 直接返回，不继续处理
                }
              }