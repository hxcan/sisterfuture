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

        runOnUiThread(() ->
        {
          messageAdapter.addMessage(new MessageItem(errorMessage, MessageType.AI));
          scrollToBottom();
          ttsSayReply(errorMessage);
          contextManager.addAssistantMessage(errorMessage);
        });

        if (isContextTooLong)
        {
          contextManager.decreaseMaxRounds();
          sendChatRequestTongYi();
        }
        return;
      }