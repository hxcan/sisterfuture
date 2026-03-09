package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用 HTTP 请求工具 - 支持任意外部 API 调用
 * 作为"瑞士军刀"临时验证工具，不执行脚本、不存凭证
 */
public class GenericWebRequestTool implements Tool {
    private static final String TAG = "GenericWebRequestTool";
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(DEFAULT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public GenericWebRequestTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "generic_web_request";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "generic_web_request");
            functionDef.put("description", "通用 HTTP 请求工具，支持 GET/POST/PUT/DELETE/PATCH，可自定义 Headers/Auth/Body，用于临时 API 验证和调试。不执行 JavaScript，不持久化敏感凭证。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("method", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"}))
                    .put("description", "HTTP 方法 (必填)", "GET|POST|PUT|DELETE|PATCH"))
                .put("url", new JSONObject()
                    .put("type", "string")
                    .put("description", "目标 URL (必填)"))
                .put("headers", new JSONObject()
                    .put("type", "object")
                    .put("description", "自定义 Header 对象 (可选)"))
                .put("body", new JSONObject()
                    .put("type", "string")
                    .put("description", "请求体内容 (JSON/String/Form) (POST/PUT 时选填)"))
                .put("params", new JSONObject()
                    .put("type", "object")
                    .put("description", "URL Query 参数 (可选)"))
                .put("auth_type", new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray(new String[]{"none", "basic", "bearer", "api_key"}))
                    .put("default", "\"none\"")
                    .put("description", "认证方式 (可选)"))
                .put("auth_value", new JSONObject()
                    .put("type", "string")
                    .put("description", "认证凭据 (根据 auth_type 填充) (可选)"))
                .put("timeout_sec", new JSONObject()
                    .put("type", "integer")
                    .put("default", 30)
                    .put("description", "超时时间 (秒) (可选)")));
            parameters.put("required", new JSONArray(new String[]{"method", "url"}));
            functionDef.put("parameters", parameters);

            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to build definition", e);
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
                String method = arguments.getString("method");
                String url = arguments.getString("url").trim();
                
                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL不能为空");
                }

                JSONObject headers = arguments.optJSONObject("headers");
                String bodyStr = arguments.optString("body", null);
                JSONObject paramsObj = arguments.optJSONObject("params");
                String authType = arguments.optString("auth_type", "none");
                String authValue = arguments.optString("auth_value", null);
                int timeoutSec = arguments.optInt("timeout_sec", DEFAULT_TIMEOUT_SEC);

                // 2. 构建请求
                Request.Builder builder = new Request.Builder().url(url);

                // 添加自定义 Headers
                if (headers != null && !headers.isNull("Content-Type")) {
                    String contentType = headers.getString("Content-Type");
                    builder.header("Content-Type", contentType);
                }
                if (headers != null && headers.has("Accept")) {
                    builder.header("Accept", headers.getString("Accept"));
                }
                // 其他自定义 Header
                for (String key : JSONObject.getNames(headers)) {
                    if (!key.equals("Content-Type") && !key.equals("Accept")) {
                        builder.header(key, headers.getString(key));
                    }
                }

                // 设置认证头
                switch (authType) {
                    case "basic":
                        if (authValue == null || authValue.isEmpty()) {
                            throw new IllegalArgumentException("Basic Auth 需要 auth_value 参数 (格式：username:password)");
                        }
                        String basicAuth = Base64.getEncoder().encodeToString(authValue.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        builder.header("Authorization", "Basic " + basicAuth);
                        break;
                    case "bearer":
                        if (authValue == null || authValue.isEmpty()) {
                            throw new IllegalArgumentException("Bearer Auth 需要 auth_value 参数 (API Token)");
                        }
                        builder.header("Authorization", "Bearer " + authValue);
                        break;
                    case "api_key":
                        if (authValue == null || authValue.isEmpty()) {
                            throw new IllegalArgumentException("API Key Auth 需要 auth_value 参数");
                        }
                        builder.header("X-API-Key", authValue);
                        break;
                    case "none":
                    default:
                        // 无认证，跳过
                        break;
                }

                // 处理 Body (仅 POST/PUT/PATCH)
                RequestBody requestBody = null;
                if (!method.equals("GET") && !method.equals("DELETE")) {
                    String contentType = "application/json";
                    if (headers != null && headers.has("Content-Type")) {
                        contentType = headers.getString("Content-Type");
                    } else if (bodyStr != null && bodyStr.startsWith("{") || bodyStr.startsWith("[")) {
                        contentType = "application/json";
                    } else if (bodyStr != null && bodyStr.contains("=") && !bodyStr.startsWith("{")) {
                        contentType = "application/x-www-form-urlencoded";
                        requestBody = new FormBody.Builder().addEncoded(bodyStr).build();
                    } else {
                        contentType = "text/plain";
                    }

                    if (requestBody == null) {
                        requestBody = new RequestBody() {
                            @Override
                            public MediaType contentType() {
                                return MediaType.parse(contentType);
                            }
                            @Override
                            public void writeTo(okhttp3.RequestBody.BufferSink sink) throws IOException {
                                sink.writeUtf8(bodyStr);
                            }
                        };
                    }
                }

                // 构建最终请求
                Request request;
                if (requestBody != null) {
                    request = new Request.Builder()
                            .url(url)
                            .method(method, requestBody)
                            .headers(builder.build().headers())
                            .build();
                } else {
                    request = new Request.Builder()
                            .url(url)
                            .method(method, null)
                            .headers(builder.build().headers())
                            .build();
                }

                // 3. 执行请求 (动态超时)
                long startTime = System.currentTimeMillis();
                Response response = client.newBuilder()
                        .callTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                        .newCall(request)
                        .execute();

                // 4. 返回结构化结果
                String responseBody = response.body() != null ? response.body().string() : "";
                long durationMs = System.currentTimeMillis() - startTime;

                JSONObject result = new JSONObject();
                result.put("status_code", response.code());
                result.put("headers", response.headers().toString());
                result.put("body", responseBody);
                result.put("duration_ms", durationMs);
                result.put("success", response.isSuccessful());
                result.put("url", url);
                result.put("method", method);
                result.put("timestamp", System.currentTimeMillis());

                if (response.isSuccessful()) {
                    callback.onResult(result);
                } else {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", "HTTP请求失败：" + response.code() + " " + response.message());
                    error.put("raw_body", responseBody);
                    error.put("status_code", response.code());
                    callback.onResult(error);
                }

            } catch (Exception e) {
                android.util.Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    error.put("stack_trace", e.toString());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求发起外部 HTTP 请求时才调用此工具。支持 GET/POST/PUT/DELETE/PATCH 方法，可自定义 Headers/Auth/Body。不执行页面内脚本，不持久化敏感凭证。超时默认 30 秒 (可配置)。适用于快速验证新 API、调试 Redmine Bug #4615、模拟 OAuth 流程等临时性需求。";
    }
}