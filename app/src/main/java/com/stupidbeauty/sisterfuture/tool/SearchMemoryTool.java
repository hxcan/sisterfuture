// SearchMemoryTool.java
package com.stupidbeauty.sisterfuture.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.stupidbeauty.sisterfuture.memory.SemanticMemorySearch;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class SearchMemoryTool implements Tool {
    private static final String TAG = "SearchMemoryTool";
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor();
    interface SearchBackend {
        SemanticMemorySearch.Result search(String query, int limit, double minSimilarity);
    }
    private final SearchBackend searchBackend;

    public SearchMemoryTool(MemoryManager memoryManager , Context context) {
        this(memoryManager::searchMemoryWithScores);
    }

    SearchMemoryTool(SearchBackend searchBackend) { this.searchBackend = searchBackend; }

    @Override
    public String getName() {
        return "searchMemory";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "searchMemory");
            functionDef.put("description", "搜索长期记忆。用于让未来姐姐在长期记忆中搜索可能与当前所聊的上下文有关的东西，帮助补全一些缺失的上下文，避免用户需要在不同次的会话中一次次地重复说明。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "自然语言查询或精确关键词，例如\"我喜欢的音乐风格\"或\"系统登录密码\"。本地向量检索结合关键词匹配。"));
            properties.put("limit", new JSONObject().put("type", "integer")
                .put("minimum", 1).put("maximum", 20).put("default", SemanticMemorySearch.DEFAULT_LIMIT)
                .put("description", "最多返回条数，默认 5；关键词命中优先，其余按相似度排序。"));
            properties.put("min_similarity", new JSONObject().put("type", "number")
                .put("minimum", -1).put("maximum", 1).put("default", SemanticMemorySearch.DEFAULT_MIN_SIMILARITY)
                .put("description", "语义候选的最低余弦相似度，默认 0.5。不是置信概率；精确关键词命中不受此阈值限制。"));

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray().put("query"));

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
    public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        SEARCH_EXECUTOR.execute(() -> {
            JSONObject result;
            try {
                result = execute(arguments);
            } catch (Exception error) {
                callback.onError(error);
                return;
            }
            callback.onResult(result);
        });
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        try {
            String query = arguments.getString("query");

            int limit = arguments.has("limit") ? arguments.getInt("limit") : SemanticMemorySearch.DEFAULT_LIMIT;
            double minSimilarity = arguments.has("min_similarity") ? arguments.getDouble("min_similarity")
                    : SemanticMemorySearch.DEFAULT_MIN_SIMILARITY;
            return searchBackend.search(query, limit, minSimilarity).toJson();

        } catch (Exception e) {
            Log.e(TAG, "执行出错", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "用于搜索长期记忆。使用本地语义向量并保留 key、内容和标签的关键词命中，默认最多返回 5 条。"
                + "观察 search_mode、ready_vectors 和 similarity 判断是否实际使用向量。"
                + "keyword_fallback 表示本次未能使用向量，不能据此断定不存在语义相关记忆。"
                + "返回的是候选记忆，相关不代表赞同或事实正确，请阅读原文后判断。";
    }
}
