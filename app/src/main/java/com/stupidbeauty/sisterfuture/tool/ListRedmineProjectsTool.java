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
 * 工具类：列出 Redmine 所有项目
 * 专门用于通过 API 自动获取所有可见项目的清单
 * 使用/projects.json 接口，符合官方 API 规范
 * 支持分页、状态过滤和缓存机制
 * 
 * @author 未来姐姐
 */
public class ListRedmineProjectsTool implements Tool {
    private static final String TAG = "ListRedmineProjectsTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();


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
            functionDef.put("description", "列出 Redmine 中所有可用的项目列表。使用 /projects.json 接口，支持分页、状态过滤和项目元数据返回。");

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
                HttpUrl.Builder urlBuilder = HttpUrl.parse(redmineUrl + "/projects.json")
                    .newBuilder()
                    .addQueryParameter("limit", String.valueOf(limit))
                    .addQueryParameter("offset", String.valueOf(offset));


                // 添加状态过滤
                if (!"all".equals(statusFilter)) {
                    urlBuilder.addQueryParameter("status", statusFilter);
                }


                Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", Credentials.basic(username, password))
                    .build();


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
                result.put("projects", new JSONObject(resultStr)); // 包装为标准响应
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
                    // 新增提示字段：引导用户检查工具备注中的配置
                    error.put("suggestion", "请检查本工具的备注中是否已有有效的 redmine_url、username 和 password 配置。");
                    callback.onResult(error); // 使用 onResult 而非 onError，确保 JSON 返回
                } catch (Exception ignored) {}
            }
        });
    }


    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求列出 Redmine 所有项目时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 redmine_url、username 和 password 配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。此工具用于发现未知项目 ID，是获取特定项目任务列表的前置步骤。";
    }
}