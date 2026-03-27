          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            int statusCode = response.code();
            FileLogger.d(NETWORK_TAG, "🌐 [HTTP_RESPONSE] HTTP Response Status: " + statusCode + " | 线程：" + Thread.currentThread().getName());
            
            if (!response.isSuccessful())
            {
              String errorBody = "";
              try {
                errorBody = response.body().string();
                FileLogger.e(NETWORK_TAG, "HTTP " + statusCode + " Error Body: " + errorBody);
                
                String errorPreview = errorBody.length() > 2000 
                    ? errorBody.substring(0, 2000) + "..." 
                    : errorBody;
                FileLogger.e(NETWORK_TAG, "Error Body Preview: " + errorPreview);
                
                // ✅ #4823 HTTP 400 → 上下文超长
                if (statusCode == 400 && ContextLengthUtils.isContextLengthError(errorBody)) {
                  FileLogger.w(NETWORK_TAG, "🔍 检测到上下文超长错误（HTTP 400），不切换接入点");
                  listener.onError(new ResponseException(response, errorBody));
                  return; // 只调用一次 onError()
                }
                
                // ✅ #4824 HTTP 429 → 限流错误
                if (statusCode == 429) {
                  FileLogger.w(NETWORK_TAG, "⚠️ 检测到 HTTP 429 限流错误，不切换接入点");
                  listener.onError(new RateLimitException(response, errorBody));
                  return; // 只调用一次 onError()
                }
                
                // ✅ 其他错误 (401/403/500/503) → 接入点不可用
                FileLogger.d(NETWORK_TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换");
                listener.onError(new AccessPointUnavailableException("Error: " + errorBody));
                return; // 只调用一次 onError()
              } catch (Exception e) {
                FileLogger.e(NETWORK_TAG, "Failed to read error body: " + e.getMessage());
                listener.onError(new AccessPointUnavailableException("Failed to read error body: " + e.getMessage()));
                return; // 只调用一次 onError()
              }
            }
            else
            {
              ResponseBody responseBody = response.body();
              if (responseBody != null)
              {
                FileLogger.d(NETWORK_TAG, "🌐 [HTTP_STREAM_START] 开始处理 SSE 流式响应 | 线程：" + Thread.currentThread().getName());
                processSSEStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete);
              }
            }
          }