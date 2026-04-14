        // UI 显示 - 🖼️ 传递图片数据到 MessageItem，保留原始文字
        messageAdapter.addMessage(new MessageItem(message != null ? message : "", MessageType.USER, hasImage ? currentImageBase64 : null));