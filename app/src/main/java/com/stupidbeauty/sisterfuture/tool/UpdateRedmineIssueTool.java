// com.stupidbeauty.sisterfuture.tool.UpdateRedmineIssueTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工具类：更新 Redmine 任务信息
 * 本工具基于 Redmine API 的 'Updating an issue' 接口，用于更新任务的任意属性。
 * 支持添加评论（notes）、修改标题（subject）、描述（description）、优先级（priority_id）和状态（status_id）等。
 * 新增支持修改上级任务编号（parent_issue_id）和任务阻挡关系。
 * 一个工具，满足多种任务更新需求，具有高度的通用性和可扩展性。
 */
public class UpdateRedmineIssueTool implements Tool
{
    private static final String TAG = "UpdateRedmineIssueTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public UpdateRedmineIssueTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "update_redmine_issue";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "update_redmine_issue");
            functionDef.put("description", "更新 Redmine 任务的任意属性。支持添加评论、修改标题、描述、优先级、状态和父子关系等。");

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
                    .put("description", "要更新的目标任务 ID"))
                .put("subject", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：任务的新标题"))
                .put("description", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：任务的新描述"))
                .put("priority", new JSONObject()
                    .put("type", "string")
                    .put("enum", new String[]{"Low", "Normal", "High", "Urgent"})
                    .put("description", "可选：任务的新优先级"))
                .put("status_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "可选：任务的新状态ID"))
                .put("notes", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：要添加的评论内容"))
                .put("parent_issue_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "可选：新的上级任务ID，用于调整任务父子关系"))
                .put("project_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "可选：新的项目ID，用于将任务移动到其他项目"))
                .put("blocked_by_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "integer"))
                    .put("description", "可选：此任务被哪些任务阻塞"))
                .put("blocking_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "integer"))
                    .put("description", "可选：此任务阻塞了哪些任务"))
            );
            parameters.put("required", new JSONArray(new String[]{"redmine_url", "username", "password", "task_id"}));

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
                int taskId = arguments.getInt("task_id");

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
                if (taskId <= 0)
                    throw new IllegalArgumentException("task_id 必须大于 0");

                // 4. 构建请求体
                JSONObject issueJson = new JSONObject();

                // 只有当参数存在时才添加，避免发送空值
                if (arguments.has("subject")) {
                    issueJson.put("subject", arguments.getString("subject"));
                }
                if (arguments.has("description")) {
                    issueJson.put("description", arguments.getString("description"));
                }
                if (arguments.has("priority")) {
                    issueJson.put("priority_id", getPriorityId(arguments.getString("priority")));
                }
                if (arguments.has("status_id")) {
                    issueJson.put("status_id", arguments.getInt("status_id"));
                }
                if (arguments.has("notes")) {
                    issueJson.put("notes", arguments.getString("notes"));
                }
                if (arguments.has("parent_issue_id")) {
                    if (!arguments.isNull("parent_issue_id")) {
                        issueJson.put("parent_issue_id", arguments.getInt("parent_issue_id"));
                    } else {
                        // 显式设置为 null 来移除父任务关系
                        issueJson.put("parent_issue_id", JSONObject.NULL);
                    }
                }
                if (arguments.has("project_id")) {
                    issueJson.put("project_id", arguments.getInt("project_id"));
                }
                
                // 处理任务阻挡关系
                if (arguments.has("blocked_by_ids")) {
                    JSONArray blockedByArray = arguments.getJSONArray("blocked_by_ids");
                    JSONObject relationsObj = new JSONObject();
                    relationsObj.put("blocked_by", blockedByArray);
                    issueJson.put("relations", relationsObj);
                }
                
                if (arguments.has("blocking_ids")) {
                    JSONArray blockingArray = arguments.getJSONArray("blocking_ids");
                    JSONObject relationsObj = issueJson.has("relations") ? issueJson.getJSONObject("relations") : new JSONObject();
                    relationsObj.put("blocks", blockingArray);
                    issueJson.put("relations", relationsObj);
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
                    .url(redmineUrl + "/issues/" + taskId + ".json")
                    .header("Authorization", Credentials.basic(username, password))
                    .put(body) // 使用 PUT 方法
                    .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful())
                {
                    throw new IOException("更新任务失败: " + response.code() + " " + response.message());
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null)
                    throw new IOException("返回体为空");

                String resultStr = responseBody.string();
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("updated_task", taskId);
                result.put("updated_at", System.currentTimeMillis());
                // 移除了sister_future_note字段

                callback.onResult(result);
            }
            catch (Exception e)
            {
                Log.e(TAG, "执行出错", e);
                try
                {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
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
        return "必须在用户明确要求更新 Redmine 任务信息时才调用此工具。若凭证缺失，应提示用户先通过 set_tool_remark 配置。支持更新任务的多个属性，包括添加评论（notes）、修改父子关系（parent_issue_id）和任务依赖关系。";
    }
}
