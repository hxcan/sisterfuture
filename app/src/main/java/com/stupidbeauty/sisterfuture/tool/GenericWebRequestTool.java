package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用 HTTP 请求工具 - 支持任意外部 API 调用
 * 作为"瑞士军刀"临时验证工具，不执行脚本、不存凭证
 *
 * 增强功能：支持 return_cookies 参数，让调用方获取结构化的 Cookie 列表
 * 用于需要登录认证的多步流程（如 Redmine 附件下载）
 *
 * 增强功能：支持 session_id 参数，自动管理同一会话的 cookie jar
 * 解决多步登录流程（如 Redmine 登录：GET 拿 CSRF + POST 登录 + GET 下载附件）
 * 中 session 不一致导致的 CSRF token 失效问题
 *
 * 修复：form-urlencoded body 多字段解析（之前只解析第一个 = 字段）
 * */
public class GenericWebRequestTool implements Tool {
    private static final String TAG = "GenericWebRequestTool";
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(DEFAULT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // 🆕 新增：session cookie jar 存储
    // Map<session_id, Map<cookie_name, Cookie>> 用于跨请求复用 cookie
    // 线程安全：使用 ConcurrentHashMap
    private static final Map<String, Map<String, Cookie>> sessionCookieJars = new ConcurrentHashMap<>();

    public GenericWebRequestTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "genericWebRequest";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "genericWebRequest");
            functionDef.put("description", "通用 HTTP 请求工具，支持 GET/POST/PUT/DELETE/PATCH，可自定义 Headers/Auth/Body，用于临时 API 验证和调试。不执行 JavaScript，不持久化敏感凭证。超时默认 30 秒 (可配置)。可选 return_cookies 启用结构化 Cookie 返回（用于需要登录认证的多步流程）。可选 session_id 启用会话内 cookie jar 自动管理：相同 session_id 的多次请求会自动复用 cookie（如 Redmine 两步登录场景）。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject methodParam = new JSONObject();
            methodParam.put("type", "string");
            JSONArray enumValues = new JSONArray();
            enumValues.put("GET").put("POST").put("PUT").put("DELETE").put("PATCH");
            methodParam.put("enum", enumValues);
            methodParam.put("description", "HTTP 方法 (必填): GET|POST|PUT|DELETE|PATCH");
            properties.put("method", methodParam);

            JSONObject urlParam = new JSONObject();
            urlParam.put("type", "string");
            urlParam.put("description", "目标 URL (必填)");
            properties.put("url", urlParam);

            JSONObject headersParam = new JSONObject();
            headersParam.put("type", "object");
            headersParam.put("description", "自定义 Header 对象 (可选)");
            properties.put("headers", headersParam);

            JSONObject bodyParam = new JSONObject();
            bodyParam.put("type", "string");
            bodyParam.put("description", "请求体内容 (JSON/String/Form) (POST/PUT 时选填)。form-urlencoded 格式支持多字段，例如：key1=value1&key2=value2");
            properties.put("body", bodyParam);

            JSONObject paramsObjParam = new JSONObject();
            paramsObjParam.put("type", "object");
            paramsObjParam.put("description", "URL Query 参数 (可选)");
            properties.put("params", paramsObjParam);

            JSONObject authTypeParam = new JSONObject();
            authTypeParam.put("type", "string");
            JSONArray authEnums = new JSONArray();
            authEnums.put("none").put("basic").put("bearer").put("api_key");
            authTypeParam.put("enum", authEnums);
            authTypeParam.put("default", "\"none\"");
            authTypeParam.put("description", "认证方式 (可选)");
            properties.put("auth_type", authTypeParam);

            JSONObject authValueParam = new JSONObject();
            authValueParam.put("type", "string");
            authValueParam.put("description", "认证凭据 (根据 auth_type 填充) (可选)");
            properties.put("auth_value", authValueParam);

            JSONObject timeoutParam = new JSONObject();
            timeoutParam.put("type", "integer");
            timeoutParam.put("default", 30);
            timeoutParam.put("description", "超时时间 (秒) (可选)");
            properties.put("timeout_sec", timeoutParam);

            // 🆕 新增：return_cookies 参数，用于返回结构化 Cookie 列表
            JSONObject returnCookiesParam = new JSONObject();
            returnCookiesParam.put("type", "boolean");
            returnCookiesParam.put("default", false);
            returnCookiesParam.put("description", "是否在响应中返回结构化的 Cookie 列表（用于多步登录认证流程）。默认 false 不返回，避免影响现有调用。");
            properties.put("return_cookies", returnCookiesParam);

            // 🆕 新增：session_id 参数，用于 cookie jar 自动管理
            JSONObject sessionIdParam = new JSONObject();
            sessionIdParam.put("type", "string");
            sessionIdParam.put("description", "会话标识 (可选)。传入相同 session_id 的多次请求会自动共享 cookie jar：自动注入该 session 已保存的 Cookie，并在响应后保存新 Set-Cookie。典型场景：Redmine 两步登录（GET /login 拿 CSRF → POST /login 带 CSRF → GET 下载附件）。不传则不启用会话管理，每次请求独立。");
            properties.put("session_id", sessionIdParam);

            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
            required.put("method").put("url");
            parameters.put("required", required);
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

    /**
     * 🆕 新增：从 session cookie jar 构造 Cookie 请求头
     * @param sessionId 会话标识
     * @return Cookie 头的值（如 "name1=value1; name2=value2"），无 cookie 时返回 null
     */
    private String buildCookieHeaderForSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        Map<String, Cookie> jar = sessionCookieJars.get(sessionId);
        if (jar == null || jar.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Cookie> entry : jar.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            sb.append(entry.getValue().name()).append("=").append(entry.getValue().value());
            first = false;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 🆕 新增：将响应中的 Set-Cookie 保存到 session cookie jar
     * @param sessionId 会话标识
     * @param url 请求的 URL（用于解析 cookie 的 domain/path）
     * @param responseHeaders 响应头
     */
    private void saveResponseCookiesToSession(String sessionId, String url, Headers responseHeaders) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        try {
            HttpUrl httpUrl = HttpUrl.get(url);
            List<Cookie> cookies = Cookie.parseAll(httpUrl, responseHeaders);
            if (cookies.isEmpty()) {
                return;
            }
            Map<String, Cookie> jar = sessionCookieJars.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
            for (Cookie cookie : cookies) {
                jar.put(cookie.name(), cookie);
            }
            android.util.Log.d(TAG, "Session [" + sessionId + "] saved " + cookies.size() + " cookie(s)");
        } catch (Exception e) {
            android.util.Log.w(TAG, "Failed to save cookies for session " + sessionId, e);
        }
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 1. 解析参数
                String method = arguments.getString("method");
                String url = arguments.getString("url").trim();

                if (url.isEmpty()) {
                    throw new IllegalArgumentException("URL 不能为空");
                }

                JSONObject headers = arguments.optJSONObject("headers");
                String bodyStr = arguments.optString("body", null);
                JSONObject paramsObj = arguments.optJSONObject("params");
                String authType = arguments.optString("auth_type", "none");
                String authValue = arguments.optString("auth_value", null);
                int timeoutSec = arguments.optInt("timeout_sec", DEFAULT_TIMEOUT_SEC);
                // 🆕 新增：是否返回 cookies
                boolean returnCookies = arguments.optBoolean("return_cookies", false);
                // 🆕 新增：session_id 参数
                String sessionId = arguments.optString("session_id", null);
                if (sessionId != null && sessionId.isEmpty()) {
                    sessionId = null; // 空字符串视为未传
                }

                // 2. 构建请求
                Request.Builder builder = new Request.Builder().url(url);

                // 🆕 新增：从 session cookie jar 注入 Cookie（如果指定了 session_id）
                if (sessionId != null) {
                    String cookieHeader = buildCookieHeaderForSession(sessionId);
                    if (cookieHeader != null) {
                        builder.header("Cookie", cookieHeader);
                        android.util.Log.d(TAG, "Session [" + sessionId + "] injecting Cookie: " + cookieHeader);
                    }
                }

                // 添加自定义 Headers
                if (headers != null && !headers.isNull("Content-Type")) {
                    String contentType = headers.getString("Content-Type");
                    builder.header("Content-Type", contentType);
                }
                if (headers != null && headers.has("Accept")) {
                    builder.header("Accept", headers.getString("Accept"));
                }
                // 其他自定义 Header
                if (headers != null) {
                    JSONArray keys = headers.names();
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        if (!key.equals("Content-Type") && !key.equals("Accept")) {
                            builder.header(key, headers.getString(key));
                        }
                    }
                }

                // 设置认证头
                switch (authType) {
                    case "basic":
                        if (authValue == null || authValue.isEmpty()) {
                            throw new IllegalArgumentException("Basic Auth 需要 auth_value 参数 (格式：username:password)");
                        }
                        byte[] authBytes = authValue.getBytes("UTF-8");
                        String basicAuth = android.util.Base64.encodeToString(authBytes, android.util.Base64.NO_WRAP);
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
                        break;
                }

                // 处理 Body (仅 POST/PUT/PATCH)
                RequestBody requestBody = null;
                if (!method.equals("GET") && !method.equals("DELETE")) {
                    String contentType = "application/json";
                    if (headers != null && headers.has("Content-Type")) {
                        contentType = headers.getString("Content-Type");
                    } else if (bodyStr != null && (bodyStr.startsWith("{") || bodyStr.startsWith("["))) {
                        contentType = "application/json";
                    } else if (bodyStr != null && bodyStr.contains("=") && !bodyStr.startsWith("{")) {
                        contentType = "application/x-www-form-urlencoded";
                        // 🔥 修复：完整解析 form-urlencoded 多字段，按 & 分割每个 key=value 对
                        FormBody.Builder formBuilder = new FormBody.Builder();
                        String[] pairs = bodyStr.split("&");
                        for (String pair : pairs) {
                            int eqIdx = pair.indexOf("=");
                            if (eqIdx > 0) {
                                String key = pair.substring(0, eqIdx);
                                String value = pair.substring(eqIdx + 1);
                                try {
                                    // URLDecoder 处理 %xx 编码
                                    key = URLDecoder.decode(key, StandardCharsets.UTF_8.name());
                                    value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                                } catch (Exception e) {
                                    android.util.Log.w(TAG, "URL decode failed for: " + pair, e);
                                }
                                formBuilder.add(key, value);
                            }
                        }
                        requestBody = formBuilder.build();
                    } else {
                        contentType = "text/plain";
                    }

                    if (requestBody == null) {
                        final MediaType mediaType = MediaType.parse(contentType);
                        final String content = bodyStr;
                        requestBody = new RequestBody() {
                            @Override
                            public MediaType contentType() {
                                return mediaType;
                            }
                            @Override
                            public void writeTo(okio.BufferedSink sink) throws IOException {
                                sink.writeUtf8(content);
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

                // 🆕 新增：响应后保存 Set-Cookie 到 session jar（如果指定了 session_id）
                if (sessionId != null) {
                    saveResponseCookiesToSession(sessionId, url, response.headers());
                }

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

                // 🆕 新增：如果启用，返回结构化 Cookie 列表
                // 修复：使用 HttpUrl.get(url) 将 String 转成 HttpUrl，避免编译错误
                if (returnCookies) {
                    JSONArray cookiesArray = new JSONArray();
                    List<Cookie> cookies = Cookie.parseAll(HttpUrl.get(url), response.headers());
                    for (Cookie cookie : cookies) {
                        JSONObject cookieObj = new JSONObject();
                        cookieObj.put("name", cookie.name());
                        cookieObj.put("value", cookie.value());
                        cookieObj.put("domain", cookie.domain());
                        cookieObj.put("path", cookie.path());
                        cookieObj.put("expiresAt", cookie.expiresAt());
                        cookieObj.put("secure", cookie.secure());
                        cookieObj.put("httpOnly", cookie.httpOnly());
                        cookiesArray.put(cookieObj);
                    }
                    result.put("cookies", cookiesArray);
                }

                // 🆕 新增：如果启用了 session，告知调用方当前 session 状态
                if (sessionId != null) {
                    Map<String, Cookie> jar = sessionCookieJars.get(sessionId);
                    if (jar != null) {
                        result.put("session_id", sessionId);
                        result.put("session_cookie_count", jar.size());
                    }
                }

                if (response.isSuccessful()) {
                    callback.onResult(result);
                } else {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", "HTTP 请求失败：" + response.code() + " " + response.message());
                    error.put("raw_body", responseBody);
                    error.put("status_code", response.code());
                    // 出错时也返回 cookies，方便调试登录失败场景
                    if (returnCookies) {
                        JSONArray cookiesArray = new JSONArray();
                        List<Cookie> cookies = Cookie.parseAll(HttpUrl.get(url), response.headers());
                        for (Cookie cookie : cookies) {
                            JSONObject cookieObj = new JSONObject();
                            cookieObj.put("name", cookie.name());
                            cookieObj.put("value", cookie.value());
                            cookieObj.put("domain", cookie.domain());
                            cookieObj.put("path", cookie.path());
                            cookieObj.put("expiresAt", cookie.expiresAt());
                            cookieObj.put("secure", cookie.secure());
                            cookieObj.put("httpOnly", cookie.httpOnly());
                            cookiesArray.put(cookieObj);
                        }
                        error.put("cookies", cookiesArray);
                    }
                    callback.onResult(error);
                }

            } catch (Exception e) {
                android.util.Log.e(TAG, "执行出错", e);
                // 🔥 修复：调用 onError 让 ToolManager 统一处理
                callback.onError(e);
            }
        });
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求发起外部 HTTP 请求时才调用此工具。支持 GET/POST/PUT/DELETE/PATCH 方法，可自定义 Headers/Auth/Body。不执行页面内脚本，不持久化敏感凭证。超时默认 30 秒 (可配置)。可选 return_cookies=true 返回结构化 Cookie 列表，用于多步登录认证流程。可选 session_id 启用会话内 cookie jar 自动管理：相同 session_id 的多次请求自动共享 cookie（如 Redmine 两步登录）。form-urlencoded body 支持多字段（如 key1=v1&key2=v2），会自动 URL 解码。适用于快速验证新 API、调试 Redmine Bug #4615、模拟 OAuth 流程等临时性需求。";
    }
}
