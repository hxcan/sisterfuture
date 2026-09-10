package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 工具类：向 Redmine 创建新任务
 * 支持创建为指定父任务的子任务，适用于任务依赖链构建。
 */
public class CreateRedmineTaskTool implements Tool
{
  private static final String TAG = "CreateRedmineTaskTool";
  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public CreateRedmineTaskTool(Context context)
  {
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "createRedmineTask";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "createRedmineTask");
      functionDef.put("description", "向 Redmine 创建一个新任务。支持指定指派人，或创建为指定父任务的子任务。");

      JSONObject priorityEnum = new JSONObject();
      priorityEnum.put("type", "string");
      priorityEnum.put("enum", new JSONArray(new String[]{"Low", "Normal", "High", "Urgent"}));
      priorityEnum.put("description", "任务优先级，默认为 Normal");

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
          .put("type", "integer")
          .put("description", "目标项目 ID（支持长整型，如 JoyMan 生成的 750160066086）"))
        .put("subject", new JSONObject()
          .put("type", "string")
          .put("description", "任务标题"))
        .put("parent_issue_id", new JSONObject()
          .put("type", "integer")
          .put("description", "可选：父任务 ID，用于创建子任务（支持长整型）"))
        .put("description", new JSONObject()
          .put("type", "string")
          .put("description", "任务描述，可选"))
        .put("priority", priorityEnum)
        .put("tracker_id", new JSONObject()
          .put("type", "integer")
          .put("description", "可选：任务类型 ID（1=Bug, 2=Feature, 3=Support），默认为项目默认值"))
        .put("assigned_to_id", new JSONObject()
          .put("type", "integer")
          .put("minimum", 1)
          .put("description", "可选：任务指派人的用户 ID，必须为正整数"))
      );
      parameters.put("required", new JSONArray(new String[]{"project_id", "subject"}));

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
              // 1. 解析参数
              RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
              String redmineUrl = auth.getRedmineUrl();
              
              // ✅ 修复：支持长整型 project_id（JoyMan 生成的 12-14 位数字）
              long projectId;
              Object projectIdObj = arguments.opt("project_id");
              if (projectIdObj == null) {
                  throw new IllegalArgumentException("缺少必需参数：project_id");
              }
              if (projectIdObj instanceof Number) {
                  projectId = ((Number) projectIdObj).longValue();
              } else {
                  try {
                          projectId = Long.parseLong(projectIdObj.toString().trim());
                  } catch (NumberFormatException e) {
                      throw new IllegalArgumentException("project_id 必须为有效的整数或长整型");
                  }
              }
              
              String subject = arguments.getString("subject");
              String description = arguments.optString("description", "");
              String priority = arguments.optString("priority", "Normal");
              
              // ✅ 同步修复：parent_issue_id 也改为 long 类型
              long parentIssueId = arguments.optLong("parent_issue_id", -1);
              long trackerId = arguments.optLong("tracker_id", -1);

              if (projectId <= 0)
                  throw new IllegalArgumentException("project_id 必须大于 0");

              // 4. 构建请求体
              JSONObject issueJson = new JSONObject();
              issueJson.put("project_id", projectId);
              issueJson.put("subject", subject);
              issueJson.put("description", description);
              issueJson.put("priority_id", getPriorityId(priority));

              if (parentIssueId > 0) {
                  issueJson.put("parent_issue_id", parentIssueId);
              }
              
              if (trackerId > 0) {
                  issueJson.put("tracker_id", trackerId);
              }

              if (arguments.has("assigned_to_id")) {
                  long assignedToId = arguments.getLong("assigned_to_id");
                  if (assignedToId <= 0) {
                      throw new IllegalArgumentException("assigned_to_id 必须为正整数");
                  }
                  issueJson.put("assigned_to_id", assignedToId);
              }

              JSONObject requestJson = new JSONObject();
              requestJson.put("issue", issueJson);

              // 5. 构建 HTTP 请求
              OkHttpClient client = new OkHttpClient();
              RequestBody body = RequestBody.create(
                  requestJson.toString(),
                  MediaType.get("application/json; charset=utf-8")
              );

              Request request = auth.apply(new Request.Builder()
                  .url(redmineUrl + "/issues.json")
                  .post(body)).build();

              Response response = client.newCall(request).execute();

              if (!response.isSuccessful())
              {
                  throw new IOException("创建任务失败：" + response.code() + " " + response.message());
              }

              ResponseBody responseBody = response.body();
              if (responseBody == null)
                  throw new IOException("返回体为空");

              String resultStr = responseBody.string();
              JSONObject result = new JSONObject();
              result.put("status", "success");
              result.put("created_task", new JSONObject(resultStr).getJSONObject("issue"));
              result.put("created_at", System.currentTimeMillis());
              callback.onResult(result);
          }
          catch (Exception e)
          {
              Log.e(TAG, "执行出错", e);
              // ✅ 修复：直接调用 onError，让 ToolManager 的 handleParameterError 统一处理
              callback.onError(e);
          }
      });
  }

  private int getPriorityId(String priority)
  {
      String lowerPriority = priority.toLowerCase();
      if ("low".equals(lowerPriority))
          return 3;
      else if ("high".equals(lowerPriority))
          return 4;
      else if ("urgent".equals(lowerPriority))
          return 5;
      else
          return 2;
  }

  @Override
  public String getDefaultSystemPromptEnhancement()
  {
      return "必须在用户明确要求创建 Redmine 任务时才调用此工具。认证支持 api_key，或 username 与 password；调用参数缺失时会自动从工具备注读取。不得输出 API Key。project_id 支持长整型。可通过 assigned_to_id 指定指派人的用户 ID，不支持按姓名指派。";
  }
  
  // 获取工具备注
  @Override
  public String getNote(Context context)
  {
      try
      {
          String currentNote = Tool.super.getNote(context);
          if (!currentNote.isEmpty()) return currentNote;
          android.content.SharedPreferences prefs = context.getSharedPreferences("tool_config", Context.MODE_PRIVATE);
          return prefs.getString("create_redmine_task", "");
      }
      catch (Exception e)
      {
          return "";
      }
  }
}
