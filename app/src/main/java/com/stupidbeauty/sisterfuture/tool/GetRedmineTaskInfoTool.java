package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GetRedmineTaskInfoTool implements Tool
{
  private static final String TAG = "GetRedmineTaskInfo";
  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public GetRedmineTaskInfoTool(Context context)
  {
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "get_redmine_task_info";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "get_redmine_task_info");
      functionDef.put("description", "获取 Redmine 中指定任务的详细信息。需要提供 Redmine 实例地址、登录凭证和任务编号。");

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
        .put("task_id", new JSONObject()
          .put("type", "integer")
          .put("description", "要查询的任务编号"))
      );
      parameters.put("required", new JSONArray(new String[]{"task_id"}));

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    }
    catch (Exception e)
    {
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
                int taskId = arguments.getInt("task_id");
                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();

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
                // 在URL构建处升级为多重包含：
                HttpUrl url = HttpUrl.parse(redmineUrl + "/issues/" + taskId + ".json")
                    .newBuilder()
                    .addQueryParameter("include", "journals,relations,attachments,children,watchers,time_entries") // 五重数据维度全解锁
                    .build();

                Request request = new Request.Builder()
                    .url(url)
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
                result.put("task_info", new JSONObject(resultStr)); // 包装为标准响应
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
                    callback.onResult(error); // 使用 onResult 而非 onError，确保 JSON 返回
                } catch (Exception ignored) {}
            }
        });
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求获取 Redmine 任务信息时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 redmine_url、username 和 password 配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。";
    }
}
