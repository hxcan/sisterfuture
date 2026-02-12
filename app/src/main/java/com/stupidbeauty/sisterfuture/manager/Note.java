package com.stupidbeauty.sisterfuture.manager;

/**
 * 记事本实体类
 * 用于存储用户记下的事项
 */
public class Note {
    private String id; // 记事ID
    private String content; // 记事内容
    private long timestamp; // 创建时间戳

    public Note() {
        // 空构造函数，用于JSON反序列化
    }

    public Note(String id, String content) {
        this.id = id;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}