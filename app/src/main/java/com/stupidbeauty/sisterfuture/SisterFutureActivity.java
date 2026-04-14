  private void displayExistingContext()
  {
    List<JSONObject> history = contextManager.getHistory();
    for (JSONObject msg : history)
    {
      String role = msg.optString("role");
      Object contentObj = msg.opt("content");
      String toolCallId = msg.optString("tool_call_id");
      JSONArray toolCalls = msg.optJSONArray("tool_calls");

      if ("tool".equals(role) && !toolCallId.isEmpty())
      {
        String toolName = msg.optString("name", "unknown_tool");
        String content = msg.optString("content");
        String displayText = "🛠️ 工具调用结果：" + toolName + "\n" + content;
        messageAdapter.addMessage(new MessageItem(displayText, MessageType.TOOL_CALL_RESULT));
      }
      else if ("user".equals(role))
      {
        // 🖼️ 检测是否为多模态消息（包含图片）
        if (contentObj instanceof JSONArray)
        {
          JSONArray contentArray = (JSONArray) contentObj;
          StringBuilder textBuilder = new StringBuilder();
          String imageUrl = null;
          
          for (int i = 0; i < contentArray.length(); i++)
          {
            try
            {
              JSONObject item = contentArray.optJSONObject(i);
              if (item == null) continue;
              
              String type = item.optString("type");
              if ("text".equals(type))
              {
                textBuilder.append(item.optString("text"));
              }
              else if ("image_url".equals(type))
              {
                JSONObject imageUrlObj = item.optJSONObject("image_url");
                if (imageUrlObj != null)
                {
                  String url = imageUrlObj.optString("url");
                  if (url != null && url.startsWith("data:image/jpeg;base64,"))
                  {
                    // 提取 Base64 部分（去掉前缀）
                    imageUrl = url.substring(21);
                  }
                }
              }
            }
            catch (Exception e)
            {
              Log.e(TAG, "解析多模态消息失败", e);
            }
          }
          
          // 使用三参数构造函数，传递文字和图片
          messageAdapter.addMessage(new MessageItem(textBuilder.toString(), MessageType.USER, imageUrl));
        }
        else
        {
          // 纯文本消息
          String content = msg.optString("content");
          if (!content.isEmpty())
          {
            messageAdapter.addMessage(new MessageItem(content, MessageType.USER));
          }
        }
      }
      else if ("assistant".equals(role))
      {
        if (toolCalls != null && toolCalls.length() > 0)
        {
          StringBuilder callText = new StringBuilder("🛠️ 正在调用工具：\n");
          for (int i = 0; i < toolCalls.length(); i++)
          {
            try
            {
              JSONObject toolCall = toolCalls.getJSONObject(i);
              JSONObject func = toolCall.optJSONObject("function");
              if (func != null)
              {
                String toolName = func.optString("name", "unknown");
                callText.append("- `").append(toolName).append("`").append("\n");
              }
            }
            catch (JSONException e)
            {
              Log.e(TAG, "解析工具调用失败", e);
            }
          }
          messageAdapter.addMessage(new MessageItem(callText.toString(), MessageType.AI));
        }
        else if (!msg.optString("content").isEmpty())
        {
          messageAdapter.addMessage(new MessageItem(msg.optString("content"), MessageType.AI));
        }
      }
    }
    
    checkAndResumeLastMessage();
  }