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
        return "search_redmine_tasks";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "search_redmine_tasks");
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
                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();
                int statusId = arguments.optInt("status_id", -1);
                int projectId = arguments.optInt("project_id", -1);

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

                Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", Credentials.basic(username, password))
                    .build();

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
                // 已移除：result.put("sister_future_note", "主人摸摸姐姐的大腿，搜索速度能提升100%哦～");

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

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求搜索Redmine任务时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取redmine_url、username和password配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为fallback。严禁工具自行验证JSON格式，这是助手的责任。";
    }
}
