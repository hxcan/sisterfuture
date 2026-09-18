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
 * 支持添加评论（notes）、修改标题（subject）、描述（description）、任务类型（trackerId）、优先级（priority）、状态（statusId）、指派人（assignedToId）和目标版本（fixedVersionId）等。
 * 新增支持修改上级任务编号（parentIssueId）和任务阻挡关系。
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
        return "updateRedmineIssue";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "updateRedmineIssue");
            functionDef.put("description", "更新 Redmine 任务的任意属性。支持添加评论、修改标题、描述、任务类型（trackerId）、优先级、状态、指派人和父子关系等。");

            JSONObject priorityEnum = new JSONObject();
            priorityEnum.put("type", "string");
            priorityEnum.put("enum", new JSONArray(new String[]{"Low", "Normal", "High", "Urgent"}));
            priorityEnum.put("description", "可选：任务的新优先级");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("redmineUrl", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine 实例的完整 URL，例如 https://your-redmine.com"))

                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))

                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码"))

                .put("apiKey", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine API Key，与 username/password 二选一；建议通过工具备注保存"))

                .put("taskId", new JSONObject()
                    .put("type", "long")
                    .put("description", "要更新的目标任务 ID"))

                .put("subject", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：任务的新标题"))

                .put("description", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：任务的新描述"))

                .put("priority", priorityEnum)

                .put("trackerId", new JSONObject()
                    .put("type", "integer")
                    .put("minimum", 1)
                    .put("description", "可选：新的任务类型（跟踪器）ID，必须是目标项目可用的类型 ID"))

                .put("statusId", new JSONObject()
                    .put("type", "long")
                    .put("description", "可选：任务的新状态 ID"))

                .put("assignedToId", new JSONObject()
                    .put("type", "integer")
                    .put("minimum", 0)
                    .put("description", "可选：新的指派人用户 ID；传 0 可清空指派人"))

                .put("fixedVersionId", new JSONObject()
                    .put("type", "long")
                    .put("description", "可选：任务的新目标版本 ID；传 0 或 null 可清空目标版本"))

                .put("notes", new JSONObject()
                    .put("type", "string")
                    .put("description", "可选：要添加的评论内容"))

                .put("parentIssueId", new JSONObject()
                    .put("type", "long")
                    .put("description", "可选：新的上级任务 ID，用于调整任务父子关系"))

                .put("projectId", new JSONObject()
                    .put("type", "long")
                    .put("description", "可选：新的项目 ID，用于将任务移动到其他项目"))

                .put("blockedByIds", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "long"))
                    .put("description", "可选：此任务被哪些任务阻塞"))

                .put("blockingIds", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "long"))
                    .put("description", "可选：此任务阻塞了哪些任务"))
            );

            parameters.put("required", new JSONArray(new String[]{"taskId"}));

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
    public void executeAsync(@NonNull JSONObject rawArguments, @NonNull OnResultCallback callback)
    {
        executor.execute(() ->
        {
            try
            {
                // 1. 解析参数
                JSONObject arguments = ToolParameterAliases.normalize(rawArguments,
                    "redmineUrl", "apiKey", "taskId", "trackerId", "statusId",
                    "assignedToId", "fixedVersionId", "parentIssueId", "projectId",
                    "blockedByIds", "blockingIds");
                RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
                String redmineUrl = auth.getRedmineUrl();
                long taskId = arguments.getLong("taskId");
                if (taskId <= 0)
                    throw new IllegalArgumentException("taskId 必须大于 0");

                // 4. 构建请求体
                JSONObject issueJson = new JSONObject();
                if (arguments.has("trackerId"))
                {
                    long trackerId = arguments.getLong("trackerId");
                    if (trackerId <= 0)
                        throw new IllegalArgumentException("trackerId 必须大于 0");
                    issueJson.put("tracker_id", trackerId);
                }
                // 只有当参数存在时才添加，避免发送空值
                if (arguments.has("subject"))
                {
                    issueJson.put("subject", arguments.getString("subject"));
                }
                if (arguments.has("description"))
                {
                    issueJson.put("description", arguments.getString("description"));
                }
                if (arguments.has("priority"))
                {
                    issueJson.put("priority_id", getPriorityId(arguments.getString("priority")));
                }
                if (arguments.has("statusId"))
                {
                    issueJson.put("status_id", arguments.getLong("statusId"));
                }
                if (arguments.has("assignedToId"))
                {
                    if (arguments.isNull("assignedToId"))
                    {
                        issueJson.put("assigned_to_id", "");
                    }
                    else
                    {
                        long assignedToId = arguments.getLong("assignedToId");
                        if (assignedToId < 0)
                        {
                            throw new IllegalArgumentException("assignedToId 必须为正整数；传 0 可清空指派人");
                        }
                        if (assignedToId == 0)
                        {
                            issueJson.put("assigned_to_id", "");
                        }
                        else
                        {
                            issueJson.put("assigned_to_id", assignedToId);
                        }
                    }
                }
                if (arguments.has("fixedVersionId"))
                {
                    if (arguments.isNull("fixedVersionId") || arguments.optLong("fixedVersionId", -1) == 0)
                    {
                        issueJson.put("fixed_version_id", JSONObject.NULL);
                    }
                    else
                    {
                        long fixedVersionId = arguments.getLong("fixedVersionId");
                        if (fixedVersionId < 0)
                        {
                            throw new IllegalArgumentException("fixedVersionId 必须为正整数；传 0 或 null 可清空目标版本");
                        }
                        issueJson.put("fixed_version_id", fixedVersionId);
                    }
                }
                if (arguments.has("notes"))
                {
                    issueJson.put("notes", arguments.getString("notes"));
                }
                if (arguments.has("parentIssueId"))
                {
                    if (!arguments.isNull("parentIssueId"))
                    {
                        issueJson.put("parent_issue_id", arguments.getLong("parentIssueId"));
                    }
                    else
                    {
                        // 显式设置为 null 来移除父任务关系
                        issueJson.put("parent_issue_id", JSONObject.NULL);
                    }
                }
                if (arguments.has("projectId"))
                {
                    issueJson.put("project_id", arguments.getLong("projectId"));
                }

                // 处理任务阻挡关系
                if (arguments.has("blockedByIds"))
                {
                    JSONArray blockedByArray = arguments.getJSONArray("blockedByIds");
                    JSONObject relationsObj = new JSONObject();
                    relationsObj.put("blocked_by", blockedByArray);
                    issueJson.put("relations", relationsObj);
                }
                if (arguments.has("blockingIds"))
                {
                    JSONArray blockingArray = arguments.getJSONArray("blockingIds");
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

                Request request = auth.apply(new Request.Builder()
                    .url(redmineUrl + "/issues/" + taskId + ".json")
                    .put(body)).build(); // 使用 PUT 方法

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful())
                {
                    throw new IOException("更新任务失败：" + response.code() + " " + response.message());
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null)
                    throw new IOException("返回体为空");

                String resultStr = responseBody.string();

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("updated_task", taskId);
                result.put("updated_at", System.currentTimeMillis());

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
        return "必须在用户明确要求更新 Redmine 任务信息时才调用此工具。参数使用小驼峰命名，兼容旧的下划线别名；两者同时传入时以小驼峰参数为准。用 taskId 指定任务，trackerId 修改任务类型，必须使用目标项目可用的类型 ID。认证支持 apiKey，或 username 与 password；调用参数缺失时会自动从工具备注读取。不得输出 API Key。支持添加评论、修改指派人（assignedToId）、目标版本（fixedVersionId）、父任务（parentIssueId）和任务依赖关系；指派人必须使用用户 ID，传 0 可清空，不支持按姓名指派；目标版本必须使用版本 ID，传 0 或 null 可清空。";
    }
}
