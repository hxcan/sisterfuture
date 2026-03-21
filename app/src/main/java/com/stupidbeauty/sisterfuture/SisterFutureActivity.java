  private void postProcessToolResults(java.util.Map<String, JSONObject> pendingResults,
                                    JSONObject assistantMessage,
                                    JSONArray toolCallsArray)
  {
    runOnUiThread(() ->
    {
      try
      {
        for (int i = 0; i < toolCallsArray.length(); i++)
        {
          JSONObject call = toolCallsArray.getJSONObject(i);
          String id = call.getString("id");
          JSONObject wrapper = pendingResults.get(id);

          if (wrapper != null)
          {
            String name = wrapper.getString("name");
            JSONObject result = wrapper.getJSONObject("result");

            // 🔥 #4790 在回复前检查：这个 toolCallId 是否已回复过
            if (!toolManager.tryMarkToolCallAsReplied(id))
            {
              Log.w(TAG, "⚠️ 忽略重复的工具回复消息，toolCallId=" + id + ", toolName=" + name);
              continue;  // 跳过重复回复
            }

            contextManager.addToolMessage(id, name, result.toString());
            Log.d(TAG, "工具消息已添加：ID=" + id + ", Name=" + name);
            messageAdapter.addMessage(
              new MessageItem(
                "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
                MessageType.TOOL_CALL_RESULT
              )
            );
          }
        }

        clearAccumulatedToolCalls();

        sendChatRequestTongYi();
      }
      catch (Exception e)
      {
        Log.e(TAG, "postProcessToolResults 出错", e);
      }
    });
  }