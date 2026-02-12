package com.stupidbeauty.sisterfuture.network;

// ModelAccessPoint.java
public class ModelAccessPoint {
    private String baseUrl;
    private String chatEndpoint;
    private String modelName;
    private String name; // 新增名称属性

    public ModelAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.chatEndpoint = chatEndpoint;
        this.modelName = modelName;
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
}
