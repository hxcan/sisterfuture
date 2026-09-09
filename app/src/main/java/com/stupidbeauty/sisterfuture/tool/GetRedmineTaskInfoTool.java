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
    return "getRedmineTaskInfo";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "getRedmineTaskInfo");
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
        .put("api_key", new JSONObject()
          .put("type", "string")
          .put("description", "Redmine API Key，与 username/password 二选一；建议通过工具备注保存"))
        .put("task_id", new JSONObject()
          .put("type", "long")
          .put("description", "要查询的任务编号（支持长整型 ID，如 JoyMan 生成的 12-14 位数字）"))
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
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public boolean isAsync()
  {
    return true;
  }

  @Override
  public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback)
  {
    executor.execute(() ->
    {
      try
      {
        // 1. 获取参数
        long taskId = arguments.getLong("task_id");
        RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
        String redmineUrl = auth.getRedmineUrl();

        // 4. 构建请求
        OkHttpClient client = new OkHttpClient();
        // 在 URL 构建处升级为多重包含：
        HttpUrl url = HttpUrl.parse(redmineUrl + "/issues/" + taskId + ".json")
          .newBuilder()
          .addQueryParameter("include", "journals,relations,attachments,children,watchers,time_entries") // 五重数据维度全解锁
          .build();

        Request request = auth.apply(new Request.Builder().url(url)).build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful())
        {
          throw new IOException("请求失败：" + response.code() + " " + response.message());
        }

        ResponseBody body = response.body();
        if (body == null)
        {
          throw new IOException("返回体为空");
        }

        String resultStr = body.string();
        JSONObject result = new JSONObject();
        result.put("task_info", new JSONObject(resultStr)); // 包装为标准响应
        result.put("status", "success");
        result.put("fetched_at", System.currentTimeMillis());

        callback.onResult(result);
      }
      catch (Exception e)
      {
        // 🔍 [TOOL_ERROR_DEBUG] 记录工具执行失败的详细信息
        Log.e(TAG, "🔧 [TOOL_EXEC_ERROR] 工具执行出错 | name=getRedmineTaskInfo | errorType=" + e.getClass().getSimpleName() + " | errorMsg=" + e.getMessage(), e);
        
        // ✅ 修复：调用 onError 而不是 onResult，让 ToolManager 处理智能引导
        Log.e(TAG, "🔧 [TOOL_CALLBACK_ERROR] 准备调用 callback.onError(...)");
        callback.onError(e);
        Log.e(TAG, "🔧 [TOOL_CALLBACK_DONE] callback.onError 已调用");
      }
    });
  }

  // --- 工具备注支持 ---
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    return "必须在用户明确要求获取 Redmine 任务信息时才调用此工具。认证支持 api_key，或 username 与 password；调用参数缺失时会自动从工具备注读取。API Key 不得输出到回复或日志。";
  }
}
