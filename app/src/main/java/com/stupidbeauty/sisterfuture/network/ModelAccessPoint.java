package com.stupidbeauty.sisterfuture.network;

// ModelAccessPoint.java
public class ModelAccessPoint {
    private String baseUrl;
    private String chatEndpoint;
    private String modelName;
    private String name; // 新增名称属性
    private String apiKey; // ✅ 新增字段：独立认证密钥

    public ModelAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName) {
        this(name, baseUrl, chatEndpoint, modelName, null); // 默认空密钥保持兼容
    }

    // 完整构造函数（包含 apiKey）
    public ModelAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName, String apiKey) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.chatEndpoint = chatEndpoint;
        this.modelName = modelName;
        this.apiKey = apiKey; // 可存储加密或明文密钥
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getChatEndpoint() {
        return chatEndpoint;
    }

    public String getModelName() {
        return modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}