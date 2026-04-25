              // 🔗 使用静态方法创建带 messageId 的消息
              MessageItem messageItem = MessageItem.withMessageId(callText.toString(), MessageType.AI, currentReservedMessageId);
              messageAdapter.addMessage(messageItem);
              
              FileLogger.i(TAG, "🔗 [UI_ADD] 已添加工具调用 UI 条目 | messageId=" + currentReservedMessageId);