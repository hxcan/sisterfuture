// MessageItem.java
package com.stupidbeauty.sisterfuture.bean;

import java.util.List;
import java.util.UUID;

public class MessageItem {
    public String text;
    private MessageType type;
    public String imageUrl; // 🖼️ 存储图片内容：base64 数据 或 远程 https URL
    private boolean imageUrlIsRemote; // 🔥 新增：标记 imageUrl 是远程 URL（true）还是 base64（false），任务 #910050720382
    private String messageId; // 🔗 新增：消息唯一 ID，用于 UI 与上下文关联
    private List<Attachment> attachments; // 🔥 新增：工具生成的多媒体附件
    private ModelUsage modelUsage; // 本地展示用，不发送给模型

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

    // 🔥 新增：判断 imageUrl 是否为远程 https URL
    public boolean isImageUrlRemote() {
        return imageUrlIsRemote;
    }

    // 🔥 新增：设置 imageUrl 为远程 URL
    public void setImageUrlRemote(boolean remote) {
        this.imageUrlIsRemote = remote;
    }

    // 🔥 新增：便捷方法，同时设置 imageUrl 和 remote 标记
    public void setImageUrl(String url, boolean remote) {
        this.imageUrl = url;
        this.imageUrlIsRemote = remote;
    }

    // 🔥 新增：获取附件列表
    public List<Attachment> getAttachments() {
        return attachments;
    }

    // 🔥 新增：设置附件列表
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public ModelUsage getModelUsage() {
        return modelUsage;
    }

    public void setModelUsage(ModelUsage modelUsage) {
        this.modelUsage = modelUsage;
    }

    // 🔗 生成唯一消息 ID（时间戳 + 随机数）
    private static String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
