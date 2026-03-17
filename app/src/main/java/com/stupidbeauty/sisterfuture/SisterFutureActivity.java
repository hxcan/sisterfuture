  \/\/ ✅ #4829 新增：统一的上下文超长错误处理方法
  private void handleContextLengthError(String errorMessage, final boolean isRetry)
  {
    Log.w(TAG, "🔍 检测到上下文超长错误，自动缩短上下文");
    
    \/\/ 1. 在界面显示错误消息（包含实际错误内容和处置提示）
    runOnUiThread(() ->
    {
      String displayMessage = errorMessage + "\n⚠️ 上下文超长，自动缩短后重试";
      messageAdapter.addMessage(new MessageItem(displayMessage, MessageType.AI));
      scrollToBottom();
      ttsSayReply("上下文超长，自动缩短后重试");
      
      \/\/ 2. 将错误消息添加到上下文（关键！这样 decreaseMaxRounds 才能删除它）
      contextManager.addAssistantMessage(errorMessage);
      
      \/\/ 3. 减少最大轮数并立即清理旧消息
      contextManager.decreaseMaxRounds();
      
      \/\/ 4. 重试
      if (isRetry)
      {
        sendChatRequestTongYi();
      }
    });
  }

  protected void parseTongYiResponse(String jsonString)
  {
    Log.d(TAG, "收到响应");
    try
    {
      TongYiResponse response = new Gson().fromJson(jsonString, TongYiResponse.class);

      if (response != null && response.getError() != null)
      {
        String errorMessage = response.getError().getMessage();
        boolean isContextTooLong = ContextLengthUtils.isContextLengthError(errorMessage);

        if (isContextTooLong)
        {
          \/\/ ✅ #4829 使用统一处理方法
          handleContextLengthError(errorMessage, true);
        }
        else
        {
          \/\/ 非上下文超长错误，正常显示
          runOnUiThread(() ->
          {
            messageAdapter.addMessage(new MessageItem(errorMessage, MessageType.AI));
            scrollToBottom();
            ttsSayReply(errorMessage);
            contextManager.addAssistantMessage(errorMessage);
          });
        }
        return;
      }