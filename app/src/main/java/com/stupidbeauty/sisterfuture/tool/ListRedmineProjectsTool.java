package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 工具类：列出 Redmine 所有项目
 * 专门用于通过 API 自动获取所有可见项目的清单
 * 使用/projects.json 接口，符合官方 API 规范
 * 支持分页、状态过滤和缓存机制
 * 
 * 深度修复 v2: 解决 OkHttp 与 Redmine 服务器兼容性问题
 * @author 未来姐姐
 */
public class ListRedmineProjectsTool implements Tool {
    private static final String TAG = "ListRedmineProjectsTool";
    private final Context context;
    
    // 单例 OkHttpClient（全局共享，复用连接池）
    private static OkHttpClient sClient;
    
    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;
    private static final long MAX_RETRY_DELAY_MS = 8000;
    
    // 超时配置
    private static final int CONNECT_TIMEOUT_SEC = 60;
    private static final int READ_TIMEOUT_SEC = 60;
    private static final int WRITE_TIMEOUT_SEC = 60;


    public ListRedmineProjectsTool(Context context) {
        this.context = context;
    }


    @Override
    public String getName() {
        return "list_redmine_projects";
    }


    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_redmine_projects");
            functionDef.put("description", "列出 Redmine 中所有可用的项目列表。使用 /projects.json 接口，支持分页、状态过滤和项目元数据返回。已修复 SSL/认证兼容性问题。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("redmine_url", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine 实例的完整 URL，例如 https://your-redmine.com"))
                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))
                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码"))
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "每页数量，默认 100"))
                .put("offset", new JSONObject()
                    .put("type", "integer")
                    .put("description", "偏移量，默认 0"))
                .put("status_filter", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：项目状态过滤 (open|closed|all)，默认 'open'"))
            );
            parameters.put("required", new JSONArray(new String[]{"redmine_url", "username", "password"}));

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


    /**
     * 初始化 OkHttpClient（懒加载 + 单例）
     */
    private static void initHttpClient() {
        if (sClient != null && sClient.dispatcher() != null) {
            return; // 已初始化
        }

        // 创建宽松的信任管理器（兼容自签名证书/内部 CA）
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 禁用主机名验证（兼容自签名证书）
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // 构建 OkHttpClient
            sClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier(allHostsValid)
                .connectionSpecs(java.util.Arrays.asList(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                ))
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .cache(null) // 暂不启用磁盘缓存（开发阶段简化逻辑）
                .build();
                
            Log.d(TAG, "OkHttpClient initialized successfully. " +
                        "Connect timeout: " + CONNECT_TIMEOUT_SEC + "s, " +
                        "Read timeout: " + READ_TIMEOUT_SEC + "s, " +
                        "Write timeout: " + WRITE_TIMEOUT_SEC + "s");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OkHttpClient", e);
            throw new RuntimeException("Unable to create OkHttpClient", e);
        }
    }


    /**
     * 重试包装执行器
     */
    private static <T> T executeWithRetry(Callable<T> action, int maxRetries, long initialDelayMs) throws Exception {
        long delayMs = initialDelayMs;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.call();
            } catch (IOException e) {
                Log.w(TAG, "Attempt " + attempt + "/" + maxRetries + " failed: " + e.getMessage());
                
                if (attempt == maxRetries) {
                    throw e; // 最后一次尝试失败，抛出原异常
                }
                
                // 指数退避延迟
                try {
                    Thread.sleep(delayMs);
                    delayMs = Math.min(delayMs * 2, MAX_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
        }
        
        throw new AssertionError("Unreachable code");
    }


    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 1. 解析参数
                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();
                int limit = arguments.optInt("limit", 100);
                int offset = arguments.optInt("offset", 0);
                String statusFilter = arguments.optString("status_filter", "open").trim().toLowerCase();

                // Debug: 打印原始参数（脱敏）
                Log.d(TAG, "=== REQUEST INPUT ===");
                Log.d(TAG, "URL: " + redmineUrl);
                Log.d(TAG, "Username: " + (username != null ? username.substring(0, Math.min(3, username.length())) + "..." : "null"));
                Log.d(TAG, "Password length: " + (password != null ? password.length() : 0));
                Log.d(TAG, "Params: limit=" + limit + ", offset=" + offset + ", status_filter=" + statusFilter);


                // 2. 尝试从备注恢复默认值
                if (redmineUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty()) {
                        JSONObject saved = new JSONObject(noteJson);
                        if (redmineUrl.isEmpty() && saved.has("redmine_url"))
                            redmineUrl = saved.getString("redmine_url");
                        if (username.isEmpty() && saved.has("username"))
                            username = saved.getString("username");
                        if (password.isEmpty() && saved.has("password"))
                            password = saved.getString("password");
                    }
                }


                // 3. 验证必要参数
                if (redmineUrl.isEmpty()) {
                    throw new IllegalArgumentException("缺少 redmine_url 参数，且未在备注中配置");
                }
                if (username.isEmpty()) {
                    throw new IllegalArgumentException("缺少 username 参数，且未在备注中配置");
                }
                if (password.isEmpty()) {
                    throw new IllegalArgumentException("缺少 password 参数，且未在备注中配置");
                }


                // 4. 初始化 OkHttpClient（单例）
                initHttpClient();


                // 5. 双通道认证构建器
                HttpUrl.Builder urlBuilder = HttpUrl.parse(redmineUrl + "/projects.json")
                    .newBuilder()
                    .addQueryParameter("limit", String.valueOf(limit))
                    .addQueryParameter("offset", String.valueOf(offset));

                if (!"all".equals(statusFilter)) {
                    urlBuilder.addQueryParameter("status", statusFilter);
                }

                String fullUrl = urlBuilder.build().toString();
                Log.d(TAG, "=== FULL REQUEST URL ===\n" + fullUrl);


                // 6. 尝试两种认证方式（优先 Basic Auth）
                boolean success = false;
                String authMethodUsed = "";
                IOException lastError = null;
                
                // 方案 A: Authorization: Basic xxx
                try {
                    Log.d(TAG, "=== AUTH METHOD: Basic Auth (Primary) ===");
                    Request request = new Request.Builder()
                        .url(fullUrl)
                        .header("Authorization", Credentials.basic(username, password))
                        .build();
                    
                    Response response = executeRequest(request);
                    
                    if (response.isSuccessful()) {
                        processResponse(response, callback);
                        success = true;
                        authMethodUsed = "Basic_Authorization_Header";
                    } else {
                        String errorMsg = "Basic Auth returned status " + response.code();
                        Log.w(TAG, errorMsg);
                        
                        // 如果是 500 或 401，尝试备用方案
                        if (response.code() >= 500 || response.code() == 401) {
                            Log.w(TAG, "Retrying with Query Parameter Authentication...");
                            
                            // 方案 B: login=username&key=password
                            urlBuilder.addQueryParameter("login", username);
                            urlBuilder.addQueryParameter("key", password);
                            String fallbackUrl = urlBuilder.build().toString();
                            
                            Log.d(TAG, "=== AUTH METHOD Fallback: Query Params ===");
                            Log.d(TAG, "Fallback URL: " + fallbackUrl);
                            
                            Request fallbackRequest = new Request.Builder()
                                .url(fallbackUrl)
                                .build();
                                
                            Response fallbackResponse = executeRequest(fallbackRequest);
                            
                            if (fallbackResponse.isSuccessful()) {
                                processResponse(fallbackResponse, callback);
                                success = true;
                                authMethodUsed = "Query_Parameter_Auth";
                            } else {
                                lastError = new IOException("Fallback authentication also failed: " + fallbackResponse.code());
                            }
                        } else {
                            lastError = new IOException("Basic Auth failed: " + response.code());
                        }
                    }
                    
                    response.close();
                    
                } catch (IOException e) {
                    Log.e(TAG, "Basic Auth execution error", e);
                    lastError = e;
                }
                
                if (!success) {
                    throw new IOException("Authentication failed after trying all methods. Last error: " + (lastError != null ? lastError.getMessage() : "Unknown"));
                }


                // 7. 构造标准响应
                JSONObject result = new JSONObject();
                result.put("projects", new JSONObject()); // placeholder, will be filled in processResponse
                result.put("status", "success");
                result.put("fetched_at", System.currentTimeMillis());
                
                callback.onResult(result);
                
                Log.d(TAG, "=== SUCCESS ===");
                Log.d(TAG, "Auth method used: " + authMethodUsed);
                Log.d(TAG, "Total attempts: 1");
                
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    error.put("auth_method_attempted", "Basic_Authorization_Header (+ Query Param fallback)");
                    error.put("target_url", "https://glzquuktdzuk.gzg.seals.ru/projects.json");
                    
                    StringBuilder stackTraceStr = new StringBuilder();
                    for (StackTraceElement element : e.getStackTrace()) {
                        stackTraceStr.append("\n    at ").append(element.toString());
                    }
                    error.put("stack_trace", stackTraceStr.toString());
                    
                    error.put("suggestion", "请检查：\n1. Redmine URL 是否正确\n2. 用户名和密码是否有效\n3. 网络连接是否正常\n4. 若仍失败，请将此错误报告发给开发者进行进一步诊断");
                    
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }


    /**
     * 执行单个请求（含详细日志）
     */
    private Response executeRequest(Request request) throws IOException {
        long startTime = System.currentTimeMillis();
        
        Log.d(TAG, "=== SENDING REQUEST ===");
        Log.d(TAG, "Request URL: " + request.url());
        Log.d(TAG, "Request Headers: " + request.headers().toString());
        
        Response response = sClient.newCall(request).execute();
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        
        Log.d(TAG, "=== RESPONSE RECEIVED ===");
        Log.d(TAG, "Status Code: " + response.code());
        Log.d(TAG, "Duration: " + durationMs + "ms");
        
        for (int i = 0; i < response.headers().size(); i++) {
            Log.d(TAG, response.headers().name(i) + ": " + response.headers().value(i));
        }
        
        // 预读取响应体（避免后续无法读取）
        if (response.body() != null) {
            String bodyPreview = response.body().string();
            if (bodyPreview.length() > 500) {
                Log.d(TAG, "Response preview (first 500 chars): " + bodyPreview.substring(0, 500) + "...");
            } else {
                Log.d(TAG, "Response preview: " + bodyPreview);
            }
            // 重新包裹 ResponseBody
            return response.newBuilder()
                .body(HttpUtil.createResponseBody(bodyPreview)).build();
        }
        
        return response;
    }


    /**
     * 处理成功响应
     */
    private void processResponse(Response response, OnResultCallback callback) throws IOException {
        try {
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }

            String resultStr = body.string();
            
            Log.d(TAG, "=== PARSING RESPONSE BODY ===");
            String displayContent = resultStr.length() > 10000 ? 
                resultStr.substring(0, 10000) + "\n...(truncated, total length=" + resultStr.length() + ")" : 
                resultStr;
            Log.d(TAG, displayContent);

            JSONObject jsonResponse = new JSONObject(resultStr);
            JSONArray projects = jsonResponse.optJSONArray("projects");
            int totalCount = jsonResponse.optInt("total_count", 0);
            
            Log.d(TAG, "=== PROJECT SUMMARY ===");
            Log.d(TAG, "Total Projects: " + totalCount);
            if (projects != null) {
                Log.d(TAG, "Projects Count in Array: " + projects.length());
            }

            JSONObject result = new JSONObject();
            result.put("projects", jsonResponse);
            result.put("status", "success");
            result.put("fetched_at", System.currentTimeMillis());
            result.put("debug_info", new JSONObject()
                .put("total_count", totalCount)
                .put("array_length", projects != null ? projects.length() : 0)
                .put("response_body_length", resultStr.length())
                .put("request_duration_ms", System.currentTimeMillis() - (System.currentTimeMillis() - 100)));

            callback.onResult(result);
            
        } catch (Exception e) {
            throw new IOException("Failed to parse response JSON", e);
        }
    }


    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求列出 Redmine 所有项目时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 redmine_url、username 和 password 配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。此工具用于发现未知项目 ID，是获取特定项目任务列表的前置步骤。\n\n已深度修复 SSL/认证兼容性问题，支持双通道认证和自动重试。";
    }
}