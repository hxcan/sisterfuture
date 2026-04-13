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
            // ✅ 暂存匹配的 tool 消息
            matchedToolMessages.add(currentObject);
            
            if (matchedToolCallIds.size() == pendingToolCallsObject.getJSONArray("tool_calls").length())
            {
              // ✅ 所有 tool 都匹配完成，按顺序添加
              list.add(pendingToolCallsObject);  // 先添加 assistant
              list.addAll(matchedToolMessages);  // 再添加所有 tool 消息
              pendingToolCallsObject = null;
              matchedToolCallIds.clear();
              matchedToolMessages.clear();  // 清空暂存列表
              FileLogger.d(TAG, "[normalizeToolCallMessages] Added assistant+tool pair, pending cleared");
            }
          }
          else
          {
            FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " did NOT match any pending tool_call!");
            // ✅ 不匹配的 tool 也添加（保持完整性）
            list.add(currentObject);
          }
          continue;
        }
        else
        {
          FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " found but pendingToolCallsObject is null, skipping!");
          // ✅ 没有 pending 也添加（可能是之前的遗留）
          list.add(currentObject);
          continue;
        }
      }