package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基本网页请求工具
 * 在设备本地实现，不依赖云端处理
 */
public class BasicWebRequestTool implements Tool {
    private static final String TAG = "BasicWebRequestTool";
    private static final int MAX_RAW_SIZE = 50 * 1024; // 50KB
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();

    public BasicWebRequestTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "basic_web_request";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "basic_web_request");
            functionDef.put("description", "发送基本网页请求，返回页面内容。强调不执行页面内脚本。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "要请求的网页URL"))
                .put("mode", new JSONObject()
                    .put("type", "string")
                    .put("enum", new String[]{"raw", "text", "summary"})
                    .put("description", "返回模式：raw(原始HTML), text(纯文本), summary(摘要)"))
            );
            parameters.put("required", new JSONArray(new String[]{"url"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 1. 解析参数
                String url = arguments.getString("url").trim();
                String mode = arguments.optString("mode", "raw");

                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL不能为空");
                }

                // 2. 构建HTTP请求
                Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "SisterFuture/1.0") // 设置UA
                    .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("请求失败: " + response.code() + " " + response.message());
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IOException("返回体为空");
                }

                // 3. 流式处理响应
                String content = processResponse(responseBody, mode);

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("content", content);
                result.put("mode", mode);
                result.put("url", url);
                result.put("processed_at", System.currentTimeMillis());
                result.put("sister_future_note", "流式处理完成！");

                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 处理响应体，根据模式返回不同格式的内容
     */
    private String processResponse(ResponseBody responseBody, String mode) throws IOException {
        String html = responseBody.string(); // 简化处理，实际应使用流式读取

        switch (mode) {
            case "text":
                return extractTextContent(html);
            case "summary":
                return generateSummary(html);
            default:
                return truncateRawHtml(html); // raw模式
        }
    }

    /**
     * 截断原始HTML，优先保留头部信息
     */
    private String truncateRawHtml(String html) {
        if (html.length() <= MAX_RAW_SIZE) {
            return html;
        }

        // 优先保留<head>和<body>前段
        int headEnd = html.indexOf("</head>");
        if (headEnd != -1 && headEnd < MAX_RAW_SIZE) {
            int bodyStart = html.indexOf("<body", headEnd);
            if (bodyStart != -1) {
                int keepLength = Math.min(MAX_RAW_SIZE, bodyStart + (MAX_RAW_SIZE - headEnd));
                return html.substring(0, keepLength) + "\n<!-- 内容被截断 -->";
            }
        }

        return html.substring(0, MAX_RAW_SIZE) + "\n<!-- 内容被截断 -->";
    }

    /**
     * 提取纯文本内容
     */
    private String extractTextContent(String html) {
        // 简单实现，实际应使用更 sophisticated 的HTML解析
        return html.replaceAll("<[^>]+>", "")  // 移除标签
                   .replaceAll("\\s+", " ")     // 多个空白符变一个空格
                   .trim();
    }

    /**
     * 生成摘要
     */
    private String generateSummary(String html) {
        StringBuilder summary = new StringBuilder();

        // 提取标题
        int titleStart = html.indexOf("<title>");
        int titleEnd = html.indexOf("</title>");
        if (titleStart != -1 && titleEnd != -1) {
            String title = html.substring(titleStart + 7, titleEnd);
            summary.append("标题: ").append(title).append("\n\n");
        }

        // 提取meta描述
        int metaStart = html.indexOf("name=\"description\"");
        if (metaStart != -1) {
            int contentStart = html.indexOf("content=\"", metaStart);
            if (contentStart != -1) {
                int contentEnd = html.indexOf("\"", contentStart + 9);
                if (contentEnd != -1) {
                    String desc = html.substring(contentStart + 9, contentEnd);
                    summary.append("描述: ").append(desc).append("\n\n");
                }
            }
        }

        // 提取前几段文字
        String textOnly = extractTextContent(html);
        String[] sentences = textOnly.split("[。！？]");
        for (int i = 0; i < Math.min(3, sentences.length); i++) {
            if (!sentences[i].trim().isEmpty()) {
                summary.append(sentences[i].trim()).append("。\n");
            }
        }

        return summary.toString();
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求获取网页内容时才调用此工具。支持三种模式：raw(原始HTML)、text(纯文本)、summary(摘要)。对超长页面会自动截断以保护上下文长度。";
    }
}
