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
    return "create_redmine_task";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "create_redmine_task");
      functionDef.put("description", "向 Redmine 创建一个新任务。支持创建为指定父任务的子任务。");

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
                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();
                
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

                // 2. 尝试从备注恢复凭证
                if (redmineUrl.isEmpty() || username.isEmpty() || password.isEmpty())
                {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty())
                    {
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
                if (redmineUrl.isEmpty())
                    throw new IllegalArgumentException("缺少 redmine_url 参数，且未在备注中配置");
                if (username.isEmpty())
                    throw new IllegalArgumentException("缺少 username 参数，且未在备注中配置");
                if (password.isEmpty())
                    throw new IllegalArgumentException("缺少 password 参数，且未在备注中配置");
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

                JSONObject requestJson = new JSONObject();
                requestJson.put("issue", issueJson);

                // 5. 构建 HTTP 请求
                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(
                    requestJson.toString(),
                    MediaType.get("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                    .url(redmineUrl + "/issues.json")
                    .header("Authorization", Credentials.basic(username, password))
                    .post(body)
                    .build();

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
                try
                {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    
                    String errorMessage = e.getMessage();
                    StringBuilder guidance = new StringBuilder();
                    
                    boolean isParamError = false;
                    if (errorMessage != null) {
                        String lowerMsg = errorMessage.toLowerCase();
                        if (lowerMsg.contains("缺少") || 
                            lowerMsg.contains("no value for") || 
                            lowerMsg.contains("required") || 
                            lowerMsg.contains("must be") ||
                            lowerMsg.contains("empty") ||
                            lowerMsg.contains("invalid type") ||
                            lowerMsg.contains("argument")) {
                            isParamError = true;
                        }
                    }
                    
                    if (isParamError) {
                        guidance.append("\n\n💡 参数完整性要求：\n");
                        guidance.append("在调用 create_redmine_task 工具时，必须仔细检查所有必需参数和可选参数的传递：\n");
                        guidance.append("- **必需参数**：project_id, subject\n");
                        guidance.append("- **认证参数**：redmine_url, username, password（建议从工具备注读取）\n");
                        guidance.append("- **可选参数**：description, priority, tracker_id, parent_issue_id\n\n");
                        guidance.append("💡 示例正确调用格式：\n");
                        guidance.append("```\n{\n  \"redmine_url\": \"https://your-redmine.com\",\n  \"username\": \"your_username\",\n  \"password\": \"your_password\",\n  \"project_id\": 750160066086,\n  \"subject\": \"任务标题\",\n  \"description\": \"任务描述\",\n  \"priority\": \"Normal\",\n  \"tracker_id\": 2,\n  \"parent_issue_id\": 456\n}\n```\n\n**重要提醒**：每次调用前务必完整传递所有可获取的参数！\n");
                        
                        errorMessage += "\n" + guidance.toString();
                    } else {
                        errorMessage = errorMessage != null ? errorMessage : "未知错误";
                    }
                    
                    error.put("message", errorMessage);
                    error.put("type", e.getClass().getSimpleName());
                    callback.onResult(error);
                }
                catch (Exception ignored) {}
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
        return "必须在用户明确要求创建 Redmine 任务时才调用此工具。project_id 参数现在支持长整型（如 JoyMan 生成的 750160066086）。\n\n💡 示例正确调用格式：\n```json\n{\n  \"project_id\": 750160066086,\n  \"subject\": \"任务标题\"\n}\n```";
    }
}