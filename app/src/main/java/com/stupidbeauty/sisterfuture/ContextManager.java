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
            FileLogger.w(TAG, "[normalizeToolCallMessages] ❌ 丢弃不匹配的 tool 消息：tool_call_id=" + answeringtoolCAllId + " (找不到对应的 pending assistant)");
            // ❌ 不匹配的 tool 消息被清理，不添加到列表
          }
          continue;
        }
        else
        {
          FileLogger.w(TAG, "[normalizeToolCallMessages] ❌ 丢弃孤立的 tool 消息：tool_call_id=" + answeringtoolCAllId + " (没有 pending assistant)");
          // ❌ 没有 pending 的 tool 消息被清理，不添加到列表
          continue;
        }