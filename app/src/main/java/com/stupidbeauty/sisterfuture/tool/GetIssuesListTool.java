package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 工具类：获取 Redmine 任务列表
 * 专门用于列出特定项目或全部项目的任务列表
 * 使用/issues.json 接口，符合官方 API 规范
 * 支持分页、project_id 过滤和缓存机制
 * @author 未来姐姐
 */
public class GetIssuesListTool implements Tool {
    private static final String TAG = "GetIssuesListTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetIssuesListTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "getIssuesList";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "getIssuesList");
            functionDef.put("description", "获取 Redmine 中指定项目或全部项目的任务列表。使用 /issues.json 接口，支持分页和项目过滤。");

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
                .put("api_key", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine API Key，与 username/password 二选一；建议通过工具备注保存"))
                .put("project_id", new JSONObject()
                    .put("type", "long")
                    .put("description", "可选：项目 ID，用于查询特定项目的任务列表（支持 64 位长整数）"))
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "每页数量，默认 25"))
                .put("offset", new JSONObject()
                    .put("type", "integer")
                    .put("description", "偏移量，默认 0"))
            );
            parameters.put("required", new JSONArray());
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
                RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
                String redmineUrl = auth.getRedmineUrl();
                long projectId = arguments.optLong("project_id", -1);
                int limit = arguments.optInt("limit",25);
                int offset = arguments.optInt("offset", 0);

                // 4. 构建请求
                OkHttpClient client = new OkHttpClient();
                HttpUrl.Builder urlBuilder = HttpUrl.parse(redmineUrl + "/issues.json")
                    .newBuilder()
                    .addQueryParameter("status_id", "*") // 获取所有状态的任务
                    .addQueryParameter("limit", String.valueOf(limit))
                    .addQueryParameter("offset", String.valueOf(offset));

                // 添加项目过滤
                if (projectId > 0) {
                    urlBuilder.addQueryParameter("project_id", String.valueOf(projectId));
                }

                Request request = auth.apply(new Request.Builder()
                    .url(urlBuilder.build())).build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("请求失败：" + response.code() + " " + response.message());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("返回体为空");
                }

                String resultStr = body.string();
                JSONObject result = new JSONObject();
                result.put("tasks", new JSONObject(resultStr)); // 包装为标准响应
                result.put("status", "success");
                result.put("fetched_at", System.currentTimeMillis());

                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    // ✅ 修复：调用 onError(e) 而非 onResult(error)，让 ToolManager 注入历史值推荐
                    callback.onError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求获取 Redmine 任务列表时才调用此工具。认证支持 api_key，或 username 与 password；调用参数缺失时会自动从工具备注读取。API Key 不得输出到回复或日志。";
    }
}
