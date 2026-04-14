// MessageItem.java
package com.stupidbeauty.sisterfuture.bean;

public class MessageItem {
    public String text;
    private MessageType type;
    public String imageUrl; // 🖼️ 新增：存储图片的 Base64 数据（如果有）

    public MessageItem(String text, MessageType type) {
        this.text = text;
        this.type = type;
    }

    // 🖼️ 新增构造函数，支持图片
    public MessageItem(String text, MessageType type, String imageUrl) {
        this.text = text;
        this.type = type;
        this.imageUrl = imageUrl;
    }

    public String getText() {
        return text;
    }

    public MessageType getType() {
        return type;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}