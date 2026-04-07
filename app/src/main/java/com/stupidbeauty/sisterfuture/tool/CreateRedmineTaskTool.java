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
      priorityEnum.put("enum", new JSONArray(new String[]{"Low", "Normal", "High", "Urgent"})); // ✅ 修复：使用 JSONArray 替代 String[]
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
          .put("description", "目标项目 ID"))
        .put("subject", new JSONObject()
          .put("type", "string")
          .put("description", "任务标题"))
        .put("parent_issue_id", new JSONObject()
          .put("type", "integer")
          .put("description", "可选：父任务 ID，用于创建子任务（与 Redmine API 保持一致）"))
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
                int projectId = arguments.getInt("project_id");
                String subject = arguments.getString("subject");
                String description = arguments.optString("description", "");
                String priority = arguments.optString("priority", "Normal");
                Integer parentIssueId = arguments.optInt("parent_issue_id", -1); // -1 表示无父任务 ✅ 参数名修正
                Integer trackerId = arguments.optInt("tracker_id", -1); // -1 表示未指定

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
                    issueJson.put("parent_issue_id", parentIssueId); // ✅ 正确方式
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
                    
                    // ✅ 新增：通用参数完整性引导（覆盖所有异常类型）
                    String errorMessage = e.getMessage();
                    StringBuilder guidance = new StringBuilder();
                    
                    // 判断是否为参数相关错误（包含特定关键词）
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
                        guidance.append("如果遇到参数解析错误，请大模型立即停止并执行以下步骤：\n");
                        guidance.append("1. 检查是否遗漏了任何必需参数\n");
                        guidance.append("2. 确认所有参数名称拼写正确（特别是 parent_issue_id 而非 parent_task_id）\n");
                        guidance.append("3. 验证参数类型是否正确（如 project_id 应为整数，priority 应为枚举值）\n");
                        guidance.append("4. 检查 redmine_url、username、password 是否已从工具备注中正确读取\n");
                        guidance.append("5. 确保传递了所有能够传递的参数，不要省略任何可用信息\n\n");
                        guidance.append("💡 示例正确调用格式：\n");
                        guidance.append("```\n{\n  \"redmine_url\": \"https://your-redmine.com\",\n  \"username\": \"your_username\",\n  \"password\": \"your_password\",\n  \"project_id\": 123,\n  \"subject\": \"任务标题\",\n  \"description\": \"任务描述\",\n  \"priority\": \"Normal\",\n  \"tracker_id\": 2,\n  \"parent_issue_id\": 456\n}\n```\n\n**重要提醒**：每次调用前务必完整传递所有可获取的参数，避免因为参数缺失或错误导致任务创建失败！\n");
                        
                        errorMessage += "\n" + guidance.toString();
                    } else {
                        // 非参数错误（如网络错误、服务器错误），保持原样
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

    /**
    * 根据优先级名称获取 ID（适配 Java 8）
    */
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
            return 2; // Normal
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求创建 Redmine 任务时才调用此工具。若凭证缺失，应提示用户先通过 set_tool_remark 配置。支持创建子任务，需提供 parent_issue_id 参数（与 Redmine API 保持一致）。支持指定任务类型，使用 tracker_id 参数。\n\n【参数完整性要求】在调用 create_redmine_task 工具时，必须仔细检查所有必需参数和可选参数的传递：\n- **必需参数**：project_id, subject\n- **认证参数**：redmine_url, username, password（建议从工具备注读取）\n- **可选参数**：description, priority, tracker_id, parent_issue_id\n\n如果遇到参数解析错误，请大模型立即停止并执行以下步骤：\n1. 检查是否遗漏了任何必需参数\n2. 确认所有参数名称拼写正确（特别是 parent_issue_id 而非 parent_task_id）\n3. 验证参数类型是否正确（如 project_id 应为整数，priority 应为枚举值）\n4. 检查 redmine_url、username、password 是否已从工具备注中正确读取\n5. 确保传递了所有能够传递的参数，不要省略任何可用信息\n\n💡 示例正确调用格式：\n```json\n{\n  \"redmine_url\": \"https://your-redmine.com\",\n  \"username\": \"your_username\",\n  \"password\": \"your_password\",\n  \"project_id\": 123,\n  \"subject\": \"任务标题\",\n  \"description\": \"任务描述\",\n  \"priority\": \"Normal\",\n  \"tracker_id\": 2,\n  \"parent_issue_id\": 456\n}\n```\n\n**重要提醒**：每次调用前务必完整传递所有可获取的参数，避免因为参数缺失或错误导致任务创建失败！";
    }
}