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
 * 使用 Brave Search API 执行安全稳定的网页搜索
 * 支持 text/raw/summary 模式，当主搜索失败时自动降级调用
 */
class SearchWithBraveTool implements Tool {
    private static final String TAG = "SearchWithBraveTool";
    private static final String BASE_URL = "https://api.search.brave.com/res/v1/web/search";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();
    private final String apiKey; // 从长期记忆或配置中获取

    public SearchWithBraveTool(Context context, String apiKey) {
        this.context = context;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "search_with_brave";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "search_with_brave");
            functionDef.put("description", "通过 Brave Search API 进行安全稳定的网页搜索，支持多种返回模式");

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
                    .put("description", "返回结果数量，默认5")))
            );
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
        return true; // 直启用
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
                int count = arguments.optInt("count", 5);

                if (query.isEmpty()) {
                    throw new IllegalArgumentException("搜索关键词不能为空");
                }

                Request request = new Request.Builder()
                    .url(BASE_URL + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&count=" + count)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
                    .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("Brave API 请求失败: " + response.code());
                }

                okhttp3.ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IOException("响应体为空");
                }

                JSONObject data = new JSONObject(responseBody.string());
                JSONArray results = data.getJSONObject("web").getJSONArray("results");

                JSONArray formattedResults = new JSONArray();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    JSONObject result = new JSONObject();
                    result.put("title", item.optString("title", ""));
                    result.put("url", item.optString("url", ""));
                    result.put("snippet", item.optString("description", ""));
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
                resultObj.put("engine", "brave_search");
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
        return "必须在用户明确要求获取网页内容时才调用此工具。支持三种模式：raw(原始HTML)、text(纯文本)、summary(摘要)。对超长页面会自动截断以保护上下文长度。";
    }
}
