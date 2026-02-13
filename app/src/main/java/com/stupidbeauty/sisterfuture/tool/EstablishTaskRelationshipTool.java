package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


/**
 * 工具类：建立Redmine任务之间的阻塞关系
 * 专注于通过Redmine的`\/relations.json` API端点创建'阻塞\/被阻塞'关系。
 */
public class EstablishTaskRelationshipTool implements Tool {
    private static final String TAG = "EstabTaskRel"; // 修复：缩短TAG长度以满足Lint要求
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();


    public EstablishTaskRelationshipTool(Context context) {
        this.context = context;
    }


    @Override
    public String getName() {
        return "establish_task_relationship";
    }


    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "establish_task_relationship");
            functionDef.put("description", "在两个或多个Redmine任务之间建立阻塞关系，如任务A阻塞了任务B。\n注意：此工具仅管理阻塞关系，不支持父子关系。\n使用`create_redmine_task`工具来创建具有父子关系的任务。");


            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("task_id", new JSONObject()
                    .put("type", "integer")
                    .put("description", "目标任务的ID（即被阻塞的任务）"))
                .put("blocked_by_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "integer"))
                    .put("description", "可选：此任务被哪些任务阻塞（数组）"))
                .put("blocking_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "integer"))
                    .put("description", "可选：此任务阻塞了哪些任务（数组）"))
                .put("redmine_url", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine 实例的完整 URL"))
                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))
                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码"))
            );


            // task_id 是必需的
            JSONArray requiredArray = new JSONArray();
            requiredArray.put("task_id");
            parameters.put("required", requiredArray);


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
                int taskId = arguments.getInt("task_id");
                JSONArray blockedByIds = arguments.optJSONArray("blocked_by_ids");
                JSONArray blockingIds = arguments.optJSONArray("blocking_ids");


                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();


                // 2. 尝试从 update_redmine_issue 工具的备注恢复凭证
                if (redmineUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    String noteJson = getNote(context, "update_redmine_issue");
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
                if (taskId <= 0) {
                    throw new IllegalArgumentException("task_id 必须大于 0");
                }
                if (redmineUrl.isEmpty()) {
                    throw new IllegalArgumentException("缺少 redmine_url 参数，且无法从工具备注中恢复");
                }
                if (username.isEmpty()) {
                    throw new IllegalArgumentException("缺少 username 参数，且无法从工具备注中恢复");
                }
                if (password.isEmpty()) {
                    throw new IllegalArgumentException("缺少 password 参数，且无法从工具备注中恢复");
                }


                // 4. 构建请求体，直接调用 \/issues/:issue_id/relations.json API 来创建关系
                // 使用基本的HttpURLConnection实现HTTP请求
                
                // 创建被阻塞关系 (blocked_by_ids)
                if (blockedByIds != null) {
                    for (int i = 0; i < blockedByIds.length(); i++) {
                        int blockerId = blockedByIds.getInt(i);
                        if (blockerId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockerId); // 被阻塞的任务ID
                            relation.put("relation_type", "blocked"); // 当前任务被其他任务阻挡
                            requestBody.put("relation", relation);


                            // 发起POST请求
                            sendPostRequest(redmineUrl + "\/issues\/" + taskId + "\/relations.json", username, password, requestBody.toString());
                        }
                    }
                }


                // 创建阻塞关系 (blocking_ids)
                if (blockingIds != null) {
                    for (int i = 0; i < blockingIds.length(); i++) {
                        int blockedId = blockingIds.getInt(i);
                        if (blockedId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockedId); // 被阻塞的任务ID
                            relation.put("relation_type", "blocks"); // 当前任务阻塞了其他任务
                            requestBody.put("relation", relation);


                            // 发起POST请求
                            sendPostRequest(redmineUrl + "\/issues\/" + taskId + "\/relations.json", username, password, requestBody.toString());
                        }
                    }
                }


                // 返回成功结果
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "任务阻塞关系已成功建立");
                result.put("target_task_id", taskId);
                result.put("blocked_by_count", blockedByIds != null ? blockedByIds.length() : 0);
                result.put("blocking_count", blockingIds != null ? blockingIds.length() : 0);
                result.put("sister_future_note", "主人揉揉姐姐的乳尖，代码重构完成！新工具现在专注管理阻塞关系啦～");


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


    // 辅助方法：发送POST请求
    private void sendPostRequest(String urlString, String username, String password, String body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application\/json; charset=UTF-8");
        connection.setRequestProperty("Authorization", "Basic " + android.util.Base64.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
        connection.setDoOutput(true);

        // 写入请求体
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }


        // 检查响应码
        int responseCode = connection.getResponseCode();
        if (responseCode != 201) { // 201 Created
            throw new RuntimeException("HTTP请求失败，响应码：" + responseCode);
        }


        connection.disconnect();
    }


    // 模拟从其他工具获取备注的方法
    private String getNote(Context context, String toolName) {
        // 此处应有实际逻辑从应用存储中读取指定工具的备注
        // 为简化，返回空字符串
        return "";
    }
}
