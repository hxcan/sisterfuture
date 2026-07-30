// MessageItem.java
package com.stupidbeauty.sisterfuture.bean;

import java.util.List;
import java.util.UUID;

public class MessageItem {
    public String text;
    private MessageType type;
    public String imageUrl; // 🖼️ 新增：存储图片的 Base64 数据（如果有）
    private String messageId; // 🔗 新增：消息唯一 ID，用于 UI 与上下文关联
    private List<Attachment> attachments; // 🔥 新增：工具生成的多媒体附件

    public MessageItem(String text, MessageType type) {
        this.text = text;
        this.type = type;
        this.messageId = generateMessageId(); // 自动生成 ID
    }

    // 🖼️ 新增构造函数，支持图片
    public MessageItem(String text, MessageType type, String imageUrl) {
        this.text = text;
        this.type = type;
        this.imageUrl = imageUrl;
        this.messageId = generateMessageId(); // 自动生成 ID
    }

    // 🔗 新增构造函数，支持图片和消息 ID（4 个参数，避免歧义）
    public MessageItem(String text, MessageType type, String imageUrl, String messageId) {
        this.text = text;
        this.type = type;
        this.imageUrl = imageUrl;
        this.messageId = messageId;
    }

    // 🔗 便捷方法：创建带指定 messageId 的消息（无图片）
    public static MessageItem withMessageId(String text, MessageType type, String messageId) {
        return new MessageItem(text, type, null, messageId);
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

    // 🔗 新增：获取消息 ID
    public String getMessageId() {
        return messageId;
    }

    // 🔗 新增：设置消息 ID
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    // 🔥 新增：获取附件列表
    public List<Attachment> getAttachments() {
        return attachments;
    }

    // 🔥 新增：设置附件列表
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    // 🔗 生成唯一消息 ID（时间戳 + 随机数）
    private static String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}