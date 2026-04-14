        // UI 显示 - 🖼️ 传递图片数据到 MessageItem
        messageAdapter.addMessage(new MessageItem(hasImage ? "📷 [图片消息]" : message, MessageType.USER, hasImage ? currentImageBase64 : null));