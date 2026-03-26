// 修改点：在 buildRequest 方法中添加 API Key 脱敏日志，并删除冗余初始化日志
// 注意：此处为简化表示，实际提交会保留原文件其他内容

// 1. 删除冗余初始化日志（移除以下行）：
// FileLogger.d(TAG, "OkHttpNetworkRequester 初始化完成");
// FileLogger.d(TAG, "TongYiClient 初始化完成");

// 2. 修改 API Key 输出为脱敏格式：
String apiKeyMasked = (apiKey != null && apiKey.length() > 12) 
    ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
    : (apiKey != null ? "***" : "null");
FileLogger.d(TAG, "[API Key] 接入点=\"" + accessPointName + "\", Key=\"" + apiKeyMasked + "\" (长度：" + (apiKey != null ? apiKey.length() : 0) + ")");

// 3. 截断 Body preview 至 200 字符：
String bodyPreview = (bodyString != null && bodyString.length() > 200) 
    ? bodyString.substring(0, 200) + "..." 
    : bodyString;
FileLogger.d(TAG, "Body preview: " + bodyPreview);