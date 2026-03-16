          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            if (response != null) {
              int statusCode = response.code();
              Log.d(TAG, "HTTP 响应异常，状态码：" + statusCode);
              
              // 401/403/500/503 等表示接入点不可用，应该切换
              if (statusCode == 401 || statusCode == 403 || statusCode == 500 || statusCode == 503) {
                Log.d(TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换");
                isAccessPointUnavailable = true;
              }
            }
            
            // 直接从 ResponseException 获取错误信息（避免重复读取 response.body()）
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