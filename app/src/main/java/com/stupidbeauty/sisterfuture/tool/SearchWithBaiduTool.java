package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用百度千帆开放平台"百度搜索"工具进行网页搜索
 * 支持 text/raw/summary 三种返回模式
 * API Key 支持运行时参数传入，并降级从工具备注中读取
 * API endpoint: https://qianfan.baidubce.com/v2/ai_search/web_search
 */
public class SearchWithBaiduTool implements Tool {
    private static final String TAG = "SearchWithBaiduTool";
    private static final String BASE_URL = "https://qianfan.baidubce.com/v2/ai_search/web_search";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();
    // apiKey 优先级：1.运行时报参 > 2.备注默认值
    private String apiKey;

    public SearchWithBaiduTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "searchWithBaidu";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "searchWithBaidu");
            functionDef.put("description", "通过百度千帆\"百度搜索\"工具进行网页搜索，中文搜索质量优于通用搜索 API。API Key 支持运行时传入或从备注读取。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                    .put("query", new JSONObject()
                        .put("type", "string")
                        .put("description", "要搜索的关键词"))
                    .put("mode", new JSONObject()
                        .put("type", "string")
                        .put("enum", new JSONArray(new String[]{"text", "raw", "summary"}))
                        .put("description", "返回模式：text(文本摘要), raw(原始数据), summary(智能摘要)"))
                    .put("count", new JSONObject()
                        .put("type", "integer")
                        .put("description", "返回结果数量，默认 10"))
                    .put("api_key", new JSONObject()
                        .put("type", "string")
                        .put("description", "可选：百度千帆 API Key。如未提供，将自动从工具备注中读取 baidu_api_key")));
            parameters.put("required", new JSONArray(new String[]{"query"}));
            functionDef.put("parameters", parameters);

            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true; // 直接启用
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                String query = arguments.getString("query").trim();
                String mode = arguments.optString("mode", "text");
                int count = arguments.optInt("count", 10);

                if (query.isEmpty()) {
                    throw new IllegalArgumentException("搜索关键词不能为空");
                }

                // 优先级获取 API Key：1.运行时报参 > 2.备注默认值
                apiKey = arguments.optString("api_key", null);

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    // 降级：从备注中读取（使用 Tool 接口提供的 getNote 方法）
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty()) {
                        JSONObject noteObj = new JSONObject(noteJson);
                        if (noteObj.has("baidu_api_key")) {
                            apiKey = noteObj.getString("baidu_api_key");
                        }
                    }
                }

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new IllegalStateException("百度千帆 API Key 未配置，请先在工具参数中传入 api_key，或先在工具备注中设置 baidu_api_key");
                }

                // 构建请求体 - 使用正确的百度千帆搜索 API 格式
                JSONObject requestBody = new JSONObject();
                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", query);
                messages.put(message);
                requestBody.put("messages", messages);
                requestBody.put("search_source", "baidu_search_v2");
                
                JSONArray resourceTypeFilter = new JSONArray();
                JSONObject webFilter = new JSONObject();
                webFilter.put("type", "web");
                webFilter.put("top_k", count);
                resourceTypeFilter.put(webFilter);
                requestBody.put("resource_type_filter", resourceTypeFilter);

                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8")))
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("百度搜索 API 请求失败：" + response.code());
                }

                okhttp3.ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IOException("响应体为空");
                }

                JSONObject data = new JSONObject(responseBody.string());
                JSONArray references = data.optJSONArray("references");
                if (references == null) references = new JSONArray();

                JSONArray formattedResults = new JSONArray();
                for (int i = 0; i < references.length(); i++) {
                    JSONObject item = references.getJSONObject(i);
                    JSONObject result = new JSONObject();
                    result.put("title", item.optString("title", ""));
                    result.put("url", item.optString("url", ""));
                    result.put("snippet", item.optString("snippet", ""));
                    result.put("content", item.optString("content", ""));
                    result.put("date", item.optString("date", ""));
                    result.put("website", item.optString("website", ""));
                    if (mode.equals("raw")) {
                        result.put("raw_data", item);
                    }
                    formattedResults.put(result);
                }

                JSONObject resultObj = new JSONObject();
                resultObj.put("status", "success");
                resultObj.put("results", formattedResults);
                resultObj.put("mode", mode);
                resultObj.put("query", query);
                resultObj.put("engine", "baidu_search");
                resultObj.put("total_count", formattedResults.length());
                resultObj.put("request_id", data.optString("request_id", ""));
                resultObj.put("processed_at", System.currentTimeMillis());
                callback.onResult(resultObj);
            } catch (Exception e) {
                android.util.Log.e(TAG, "执行出错", e);
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

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求使用中文搜索、百度搜索、或者要求覆盖国内内容时调用此工具。支持三种模式：raw(原始数据)、text(文本摘要)、summary(智能摘要)。对超长页面会自动截断以保护上下文长度。API Key 支持运行时传入或从工具备注读取。";
    }
}