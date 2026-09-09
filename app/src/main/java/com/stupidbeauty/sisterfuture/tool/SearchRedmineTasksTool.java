package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchRedmineTasksTool implements Tool {
    private static final String TAG = "SearchRedmineTasks";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SearchRedmineTasksTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "searchRedmineTasks";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "searchRedmineTasks");
            functionDef.put("description", "根据关键词、状态、项目等条件搜索Redmine任务，支持分页和排序");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("redmine_url", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine实例URL"))
                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))
                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码"))
                .put("api_key", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine API Key，与 username/password 二选一；建议通过工具备注保存"))
                .put("query", new JSONObject()
                    .put("type", "string")
                    .put("description", "搜索关键词"))
                .put("status_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "状态ID"))
                .put("project_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "项目ID"))
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "每页数量，默认25"))
                .put("offset", new JSONObject()
                    .put("type", "integer")
                    .put("description", "偏移量，默认0"))
                .put("sort", new JSONObject()
                    .put("type", "string")
                    .put("description", "排序字段，如updated_on:desc"))
            );
            parameters.put("required", new JSONArray(new String[]{"query"}));

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
                // 1. 获取参数
                String query = arguments.getString("query");
                int limit = arguments.optInt("limit", 25);
                int offset = arguments.optInt("offset", 0);
                String sort = arguments.optString("sort", "");
                RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
                String redmineUrl = auth.getRedmineUrl();
                int statusId = arguments.optInt("status_id", -1);
                int projectId = arguments.optInt("project_id", -1);

                // 4. 构建请求
                OkHttpClient client = new OkHttpClient();
                HttpUrl.Builder urlBuilder = HttpUrl.parse(redmineUrl + "/search.json")
                    .newBuilder()
                    .addQueryParameter("q", query);

                // 添加可选过滤器
                urlBuilder.addQueryParameter("issues", "1"); // 只搜索任务
                if (limit > 0) {
                    urlBuilder.addQueryParameter("limit", String.valueOf(limit));
                }
                if (offset > 0) {
                    urlBuilder.addQueryParameter("offset", String.valueOf(offset));
                }
                if (!sort.isEmpty()) {
                    urlBuilder.addQueryParameter("sort", sort);
                }

                Request request = auth.apply(new Request.Builder()
                    .url(urlBuilder.build())).build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("请求失败: " + response.code() + " " + response.message());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("返回体为空");
                }

                String resultStr = body.string();
                JSONObject result = new JSONObject();
                result.put("tasks", new JSONObject(resultStr));
                result.put("status", "success");
                result.put("searched_at", System.currentTimeMillis());

                callback.onResult(result);

            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                // ✅ 修复：调用 onError 而不是 onResult，让 ToolManager 处理智能引导
                callback.onError(e);
            }
        });
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求搜索 Redmine 任务时才调用此工具。认证支持 api_key，或 username 与 password；调用参数缺失时会自动从工具备注读取。API Key 不得输出到回复或日志。";
    }
}
