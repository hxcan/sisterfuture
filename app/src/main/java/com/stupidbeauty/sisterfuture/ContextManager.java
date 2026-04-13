      else if (roleString.equals("tool"))
      {
        String answeringtoolCAllId = currentObject.optString("tool_call_id", "none");
        
        if (pendingToolCallsObject!=null)
        {
          JSONArray toolCallsArray = pendingToolCallsObject.getJSONArray("tool_calls");
          boolean matched = false;
          for (int tc = 0; tc < toolCallsArray.length(); tc++)
          {
            JSONObject toolCall = toolCallsArray.getJSONObject(tc);
            String toolCallId = toolCall.optString("id", "");
            if (toolCallId.equals(answeringtoolCAllId) && !matchedToolCallIds.contains(toolCallId))
            {
              matched = true;
              matchedToolCallIds.add(toolCallId);
              FileLogger.d(TAG, "[normalizeToolCallMessages] Tool message matched tool_call_id=" + answeringtoolCAllId + " at index " + tc);
              break;
            }
          }
          if (matched)
          {
            if (matchedToolCallIds.size() == pendingToolCallsObject.getJSONArray("tool_calls").length())
            {
              list.add(pendingToolCallsObject);
              pendingToolCallsObject = null;
              matchedToolCallIds.clear();
              FileLogger.d(TAG, "[normalizeToolCallMessages] Added assistant+tool pair, pending cleared");
            }
            // ✅ 修复：添加匹配成功的 tool 消息到列表
            list.add(currentObject);
            FileLogger.d(TAG, "[normalizeToolCallMessages] Added matched tool message to list");
          }
          else
          {
            FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " did NOT match any pending tool_call!");
            // ✅ 修复：即使不匹配也添加（可能是孤立的 tool 消息）
            list.add(currentObject);
          }
          continue;
        }
        else
        {
          FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " found but pendingToolCallsObject is null, skipping!");
          // ✅ 修复：没有 pending 也添加（可能是之前的遗留）
          list.add(currentObject);
          continue;
        }
      }