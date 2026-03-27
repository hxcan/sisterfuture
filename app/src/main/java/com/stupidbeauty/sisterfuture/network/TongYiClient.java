          @Override
          public void onFailure(Call call, IOException e)
          {
            FileLogger.e(NETWORK_TAG, "🌐 [HTTP_FAILURE] 请求失败：" + e.getMessage() + " | 线程：" + Thread.currentThread().getName());
            // ❌ 删除：不再在这里调用 reportCurrentAccessPointUnavailable()
            // ✅ 改为：由 SisterFutureActivity.onError() 统一处理
            listener.onError(new AccessPointUnavailableException("Current access point is unavailable", e));
          }

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
                
                if (statusCode == 400 && ContextLengthUtils.isContextLengthError(errorBody)) {
                  FileLogger.w(NETWORK_TAG, "🔍 检测到上下文超长错误（HTTP 400），不切换接入点");
                  listener.onError(new ResponseException(response, errorBody));
                  return;
                }
                
                if (statusCode == 429) {
                  FileLogger.w(NETWORK_TAG, "⚠️ 检测到 HTTP 429 限流错误，不切换接入点");
                  listener.onError(new RateLimitException(response, errorBody));
                  return;
                }
              } catch (Exception e) {
                FileLogger.e(NETWORK_TAG, "Failed to read error body: " + e.getMessage());
              }
              
              // ❌ 删除：不再在这里调用 reportCurrentAccessPointUnavailable()
              // ✅ 改为：由 SisterFutureActivity.onError() 统一处理
              listener.onError(new AccessPointUnavailableException("Error: " + errorBody));
              listener.onError(new ResponseException(response, errorBody));
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