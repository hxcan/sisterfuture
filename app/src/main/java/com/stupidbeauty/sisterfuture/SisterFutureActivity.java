      }
      catch (Exception e)
      {
        e.printStackTrace();

        try
        {
          messagesArray = new JSONArray();
          String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this);

          messagesArray.put(new JSONObject().put("role", "system").put("content", enhancedSystemPrompt));
          messagesArray.put(new JSONObject().put("role", "user").put("content", voiceRecognizeResultString));
        }
        catch (Exception ignored)
        {
        }
      }

      // 🔍 #4839 调试：输出请求内容，检查 tool_calls 参数格式
      logRequestMessages(messagesArray);

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()