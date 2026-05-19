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
 * 工具类：建立 Redmine 任务之间的阻塞关系
 * 专注于通过 Redmine 的 `/relations.json` API 端点创建'阻塞/被阻塞'关系。
 * 
 * @author 太极美术工程狮狮长
 * @version 2.0.2 - 修复长整型 ID 溢出问题
 */
public class EstablishTaskRelationshipTool implements Tool {
    private static final String TAG = "EstabTaskRel";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public EstablishTaskRelationshipTool(Context context) {
        this.context = context;
    }


    @Override
    public String getName() {
        return "establishTaskRelationship";
    }


    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "establishTaskRelationship");
            functionDef.put("description", "在两个或多个 Redmine 任务之间建立阻塞关系，如任务 A 阻塞了任务 B。\n注意：此工具仅管理阻塞关系，不支持父子关系。\n使用 `createRedmineTask` 工具来创建具有父子关系的任务。\n\n**重要**: 本工具支持长整型任务 ID（如 JoyMan 生成的 14 位数字 ID）。");


            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("task_id", new JSONObject()
                    .put("type", "number")
                    .put("description", "目标任务的 ID（即被阻塞的任务），支持长整型"))
                .put("blocked_by_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "number"))
                    .put("description", "可选：此任务被哪些任务阻塞（数组），支持长整型"))
                .put("blocking_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "number"))
                    .put("description", "可选：此任务阻塞了哪些任务（数组），支持长整型"))
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
                // 1. 解析参数 - ✅ 修复：使用 long 类型解析长整型 ID
                long taskId = arguments.getLong("task_id");
                JSONArray blockedByIds = arguments.optJSONArray("blocked_by_ids");
                JSONArray blockingIds = arguments.optJSONArray("blocking_ids");

                String redmineUrl = arguments.optString("redmine_url", "").trim();
                String username = arguments.optString("username", "").trim();
                String password = arguments.optString("password", "").trim();


                // 2. 验证必要参数
                if (taskId <= 0) {
                    throw new IllegalArgumentException("task_id 必须大于 0");
                }
                if (redmineUrl.isEmpty()) {
                    throw new IllegalArgumentException("缺少 redmine_url 参数");
                }
                if (username.isEmpty()) {
                    throw new IllegalArgumentException("缺少 username 参数");
                }
                if (password.isEmpty()) {
                    throw new IllegalArgumentException("缺少 password 参数");
                }


                // 3. 构建请求体，直接调用 /issues/:issue_id/relations.json API 来创建关系
                // 使用基本的 HttpURLConnection 实现 HTTP 请求
                
                // 创建被阻塞关系 (blocked_by_ids)
                if (blockedByIds != null) {
                    for (int i = 0; i < blockedByIds.length(); i++) {
                        // ✅ 修复：使用 getLong 解析长整型 ID
                        long blockerId = blockedByIds.getLong(i);
                        if (blockerId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockerId); // 被阻塞的任务 ID (long)
                            relation.put("relation_type", "blocked"); // 当前任务被其他任务阻挡
                            requestBody.put("relation", relation);


                            // 发起 POST 请求
                            Log.d(TAG, "🚀 创建关系：" + taskId + " blocked_by " + blockerId);
                            sendPostRequest(redmineUrl + "/issues/" + taskId + "/relations.json", username, password, requestBody.toString());
                            Log.d(TAG, "✅ 关系创建成功：" + blockerId);
                        }
                    }
                }


                // 创建阻塞关系 (blocking_ids)
                if (blockingIds != null) {
                    for (int i = 0; i < blockingIds.length(); i++) {
                        // ✅ 修复：使用 getLong 解析长整型 ID
                        long blockedId = blockingIds.getLong(i);
                        if (blockedId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockedId); // 被阻塞的任务 ID (long)
                            relation.put("relation_type", "blocks"); // 当前任务阻塞了其他任务
                            requestBody.put("relation", relation);


                            // 发起 POST 请求
                            Log.d(TAG, "🚀 创建关系：" + taskId + " blocks " + blockedId);
                            sendPostRequest(redmineUrl + "/issues/" + taskId + "/relations.json", username, password, requestBody.toString());
                            Log.d(TAG, "✅ 关系创建成功：" + blockedId);
                        }
                    }
                }


                // 返回成功结果 - ✅ 修复：返回正确的长整型 ID
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "任务阻塞关系已成功建立");
                result.put("target_task_id", taskId); // 现在是 long 类型
                result.put("blocked_by_count", blockedByIds != null ? blockedByIds.length() : 0);
                result.put("blocking_count", blockingIds != null ? blockingIds.length() : 0);


                Log.i(TAG, "✅ 执行完成：task_id=" + taskId + ", blocked_by_count=" + result.getInt("blocked_by_count") + ", blocking_count=" + result.getInt("blocking_count"));
                callback.onResult(result);

            } catch (Exception e) {
                Log.e(TAG, "❌ 执行出错：" + e.getMessage(), e);
                // ✅ 修复：直接调用 onError，让 ToolManager 的 handleParameterError 统一处理
                callback.onError(e);
            }
        });
    }


    /**
     * 辅助方法：发送 POST 请求
     * @param urlString 目标 URL
     * @param username 用户名
     * @param password 密码
     * @param body 请求体 JSON 字符串
     * @throws Exception 如果 HTTP 请求失败
     */
    private void sendPostRequest(String urlString, String username, String password, String body) throws Exception {
        Log.d(TAG, "📡 发送 POST 请求到：" + urlString);
        Log.d(TAG, "📝 请求体：" + body);
        
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Authorization", "Basic " + android.util.Base64.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
        connection.setDoOutput(true);

        // 写入请求体
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }


        // 检查响应码
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "📊 响应码：" + responseCode);
        
        if (responseCode != 201) { // 201 Created
            throw new RuntimeException("HTTP 请求失败，响应码：" + responseCode + ", URL: " + urlString);
        }


        connection.disconnect();
        Log.d(TAG, "✅ 请求完成");
    }


    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求建立 Redmine 任务之间的阻塞关系时才调用此工具。需要提供 redmine_url, username, password 等认证参数。注意：此工具仅管理阻塞关系，不支持父子关系。本工具支持长整型任务 ID（如 JoyMan 生成的 14 位数字 ID）。";
    }
}