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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


/**
 * 工具类：列出 Redmine 所有项目
 * 专门用于通过 API 自动获取所有可见项目的清单
 * 使用/projects.json 接口，符合官方 API 规范
 * 
 * 📚 严格遵循 Redmine 官方文档:
 * https://www.redmine.org/projects/redmine/wiki/Rest_Projects
 * 
 * 核心规范:
 * - limit: 官方默认 30 (本工具采用此值)
 * - offset: 用于分页
 * - include: 可选关联数据 (trackers, issue_categories 等)
 * - ❌ status 参数不存在! (只有 issues 支持)
 * 
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
    
    // ✅ 明确定义 executor 字段
    private final ExecutorService executor = Executors.newSingleThreadExecutor();


    public ListRedmineProjectsTool(Context context) {
        this.context = context;
    }


    @Override
    public String getName() {
        return "listRedmineProjects";
    }


    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "listRedmineProjects");
            functionDef.put("description", "列出 Redmine 中所有可用的项目列表。使用 /projects.json 接口，严格遵循官方 API 规范 (https://www.redmine.org/projects/redmine/wiki/Rest_Projects)，支持分页和总数量统计。支持 localhost joyman api。如果 JoyMan API 连接超时，则重新启动应用程序使其前台运行。");

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
                    .put("description", "每页数量，官方默认 30，本工具安全范围 5-50"))
                .put("offset", new JSONObject()
                    .put("type", "integer")
                    .put("description", "偏移量，默认 0，用于分页"))
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
                .cache(null)
                .build();
                
            Log.d(TAG, "OkHttpClient initialized successfully." +
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
                    throw e;
                }
                
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
                int limit = arguments.optInt("limit", 30); // ✅ 改为官方默认 30
                int offset = arguments.optInt("offset", 0);

                // Debug: 打印原始参数（脱敏）
                Log.d(TAG, "=== REQUEST INPUT ===");
                Log.d(TAG, "URL: " + redmineUrl);
                Log.d(TAG, "Username: " + (username != null ? username.substring(0, Math.min(3, username.length())) + "..." : "null"));
                Log.d(TAG, "Password length: " + (password != null ? password.length() : 0));
                Log.d(TAG, "Params: limit=" + limit + ", offset=" + offset);
                Log.d(TAG, "Note: Official Redmine API does NOT support 'status' parameter for /projects.json!");


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


                // 5. 构建请求 URL (✅ 严格遵循官方规范，移除 status 参数)
                HttpUrl.Builder urlBuilder = HttpUrl.parse(redmineUrl + "/projects.json")
                    .newBuilder()
                    .addQueryParameter("limit", String.valueOf(limit))
                    .addQueryParameter("offset", String.valueOf(offset));

                String fullUrl = urlBuilder.build().toString();
                Log.d(TAG, "=== FULL REQUEST URL (Official Compliant) ===\n" + fullUrl);


                // 6. 仅使用 Basic Auth (移除无效的 Query Param Fallback)
                Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
                
                Response response = executeRequest(request);

                if (!response.isSuccessful()) {
                    String errorMsg = "Request returned status " + response.code();
                    Log.e(TAG, errorMsg);
                    
                    ResponseBody body = response.body();
                    String errorBody = "";
                    if (body != null) {
                        errorBody = body.string();
                        Log.e(TAG, "Error Body: " + errorBody);
                    }
                    
                    response.close();
                    throw new IOException(errorMsg + ". Details: " + errorBody);
                }


                // 7. 处理响应并实现自动分页
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
                JSONArray projectsArray = jsonResponse.optJSONArray("projects");
                int totalCount = jsonResponse.optInt("total_count", 0);
                
                Log.d(TAG, "=== PROJECT SUMMARY ===");
                Log.d(TAG, "Total Projects in Server: " + totalCount);
                Log.d(TAG, "Projects in This Page: " + (projectsArray != null ? projectsArray.length() : 0));

                // ✅ 实现自动分页：如果当前页面不够，继续拉取后续分页
                List<Map.Entry<Integer, JSONObject>> allProjectsMap = new ArrayList<>();
                if (projectsArray != null) {
                    for (int i = 0; i < projectsArray.length(); i++) {
                        JSONObject project = projectsArray.getJSONObject(i);
                        allProjectsMap.add(new java.util.AbstractMap.SimpleEntry<>(project.optInt("id"), project));
                    }
                }

                int fetchedCount = allProjectsMap.size();
                if (totalCount > fetchedCount) {
                    Log.d(TAG, "More projects needed. Fetching offset=" + fetchedCount + "...");
                    
                    // 递归/循环拉取剩余分页
                    int currentOffset = fetchedCount;
                    int batchSize = 30; // 每次最多拉 30 条
                    
                    while (currentOffset < totalCount) {
                        int nextLimit = Math.min(batchSize, totalCount - currentOffset);
                        
                        Log.d(TAG, "Fetching batch: offset=" + currentOffset + ", limit=" + nextLimit);
                        
                        HttpUrl.Builder nextPageBuilder = HttpUrl.parse(redmineUrl + "/projects.json")
                            .newBuilder()
                            .addQueryParameter("limit", String.valueOf(nextLimit))
                            .addQueryParameter("offset", String.valueOf(currentOffset));
                            
                        String pageUrl = nextPageBuilder.build().toString();
                        Log.d(TAG, "=== NEXT PAGE URL ===\n" + pageUrl);
                        
                        Request nextPageRequest = new Request.Builder()
                            .url(pageUrl)
                            .header("Authorization", Credentials.basic(username, password))
                            .build();
                            
                        Response nextPageResponse = executeRequest(nextPageRequest);
                        
                        if (!nextPageResponse.isSuccessful()) {
                            Log.e(TAG, "Failed to fetch next page: " + nextPageResponse.code());
                            nextPageResponse.close();
                            break;
                        }
                        
                        ResponseBody nextPageBody = nextPageResponse.body();
                        if (nextPageBody == null) {
                            nextPageResponse.close();
                            break;
                        }
                        
                        String pageResultStr = nextPageBody.string();
                        JSONObject pageJsonResponse = new JSONObject(pageResultStr);
                        JSONArray pageProjectsArray = pageJsonResponse.optJSONArray("projects");
                        
                        if (pageProjectsArray != null) {
                            for (int i = 0; i < pageProjectsArray.length(); i++) {
                                JSONObject project = pageProjectsArray.getJSONObject(i);
                                allProjectsMap.add(new java.util.AbstractMap.SimpleEntry<>(project.optInt("id"), project));
                            }
                        }
                        
                        nextPageResponse.close();
                        
                        currentOffset += nextLimit;
                        fetchedCount = allProjectsMap.size();
                        
                        if (fetchedCount >= totalCount) {
                            break;
                        }
                    }
                }
                
                Log.d(TAG, "=== ALL PROJECTS FETCHED ===");
                Log.d(TAG, "Total Retrieved: " + fetchedCount);


                // 8. 构造标准响应
                JSONObject result = new JSONObject();
                JSONArray allProjectsArray = new JSONArray();
                for (Map.Entry<Integer, JSONObject> entry : allProjectsMap) {
                    allProjectsArray.put(entry.getValue());
                }
                
                result.put("projects", allProjectsArray);
                result.put("total_count", totalCount);
                result.put("status", "success");
                result.put("fetched_at", System.currentTimeMillis());
                result.put("debug_info", new JSONObject()
                    .put("official_limit_default", 30)
                    .put("status_param_removed_official_compliance", true)
                    .put("pages_fetched", allProjectsMap.size() > 30 ? "auto-paginated" : "single_page")
                    .put("pagination_enabled", totalCount > 30));

                callback.onResult(result);
                
                Log.d(TAG, "=== SUCCESS ===");
                
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    
                    StringBuilder stackTraceStr = new StringBuilder();
                    for (StackTraceElement element : e.getStackTrace()) {
                        stackTraceStr.append("\n    at ").append(element.toString());
                    }
                    error.put("stack_trace", stackTraceStr.toString());
                    
                    // 【错误处理增强】添加智能引导提示
                    String smartSuggestion = "\n\n💡 建议操作：\n" +
                        "1. 请先调用 `get_tool_remark(\"listRedmineProjects\")` 检查是否已有保存的访问参数\n" +
                        "2. 如果备注中没有参数，请向用户索要正确的 Redmine URL、用户名和密码\n" +
                        "3. 获取参数后，使用 `set_tool_remark` 将其写入工具备注，或 `write_memory` 保存到长期记忆";
                    
                    error.put("suggestion", smartSuggestion + "\n\n原始建议：请检查：\n1. Redmine URL 是否正确\n2. 用户名和密码是否有效\n3. 网络连接是否正常\n4. 若仍失败，请将此错误报告发给开发者进行进一步诊断\n\nDebug Info:\n- Official Redmine API does not support 'status' parameter for /projects.json!\n- Using official default limit=30\n- Auto-pagination enabled if total_count > limit");
                    
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
            // ✅ 使用标准 OkHttp API 重新创建 ResponseBody
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            return response.newBuilder()
                .body(ResponseBody.create(mediaType, bodyPreview)).build();
        }
        
        return response;
    }


    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求列出 Redmine 所有项目时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 redmine_url、username 和 password 配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。\n\n已严格遵循 Redmine 官方 API 规范 (https://www.redmine.org/projects/redmine/wiki/Rest_Projects)，自动分页获取全部项目。\n注意：/projects.json 不支持 status 参数（仅 /issues.json 支持）!\n\n重要：如果 JoyMan API 连接超时，则重新启动应用程序使其前台运行。";
    }
}