package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.Context;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.stupidbeauty.sisterfuture.utils.FileLogger;


/**
 * 工具类：建立 Redmine 任务之间的阻塞关系
 * 专注于通过 Redmine 的 `/relations.json` API 端点创建'阻塞/被阻塞'关系。
 *
 * 注意：ID 字段在 schema 中统一为 string（OpenAI 协议兼容），内部用 RedmineIdUtil 解析。
 *
 * @author 太极美术工程狮狮长
 * @version 2.0.7 - 改为 string ID 兼容 OpenAI 协议
 * */
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
            functionDef.put("description", "在两个或多个 Redmine 任务之间建立阻塞关系，如任务 A 阻塞了任务 B。\n注意：此工具仅管理阻塞关系，不支持父子关系。\n使用 `createRedmineTask` 工具来创建具有父子关系的任务。\n\n**重要**: 本工具支持长整型任务 ID（如 JoyMan 生成的 14 位数字 ID），ID 字段在调用时传字符串形式的数字。");


            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("task_id", new JSONObject()
                    .put("type", "string")
                    .put("description", "目标任务的 ID（即被阻塞的任务），字符串形式的数字，支持长整型"))
                .put("blocked_by_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "string"))
                    .put("description", "可选：此任务被哪些任务阻塞（数组，元素为字符串形式的数字 ID），支持长整型"))
                .put("blocking_ids", new JSONObject()
                    .put("type", "array")
                    .put("items", new JSONObject().put("type", "string"))
                    .put("description", "可选：此任务阻塞了哪些任务（数组，元素为字符串形式的数字 ID），支持长整型"))
                .put("redmine_url", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine 实例的完整 URL"))
                .put("username", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录用户名"))
                .put("password", new JSONObject()
                    .put("type", "string")
                    .put("description", "登录密码"))
                .put("api_key", new JSONObject()
                    .put("type", "string")
                    .put("description", "Redmine API Key，与 username/password 二选一；建议通过工具备注保存"))
            );


            // task_id 是必需的
            JSONArray requiredArray = new JSONArray();
            requiredArray.put("task_id");
            parameters.put("required", requiredArray);


            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to build definition", e);
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
                // 1. 解析参数 - 用 RedmineIdUtil 兼容 string/number 两种输入
                long taskId = RedmineIdUtil.parseLong(arguments, "task_id", true);
                JSONArray blockedByIds = arguments.optJSONArray("blocked_by_ids");
                JSONArray blockingIds = arguments.optJSONArray("blocking_ids");

                RedmineAuth auth = RedmineAuth.resolve(arguments, getNote(context));
                String redmineUrl = auth.getRedmineUrl();


                // 2. 验证必要参数
                if (taskId <= 0) {
                    throw new IllegalArgumentException("task_id 必须大于 0");
                }
                // 3. 构建请求体，直接调用 /issues/:issue_id/relations.json API 来创建关系
                // 使用基本的 HttpURLConnection 实现 HTTP 请求

                // 创建被阻塞关系 (blocked_by_ids)
                if (blockedByIds != null) {
                    JSONArray normalized = RedmineIdUtil.parseLongArray(blockedByIds);
                    for (int i = 0; i < normalized.length(); i++) {
                        long blockerId = normalized.getLong(i);
                        if (blockerId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockerId);
                            relation.put("relation_type", "blocked");
                            requestBody.put("relation", relation);


                            // 发起 POST 请求
                            FileLogger.d(TAG, "🚀 创建关系：" + taskId + " blocked_by " + blockerId);
                            sendPostRequest(redmineUrl + "/issues/" + taskId + "/relations.json", auth, requestBody.toString());
                            FileLogger.d(TAG, "✅ 关系创建成功：" + blockerId);
                        }
                    }
                }


                // 创建阻塞关系 (blocking_ids)
                if (blockingIds != null) {
                    JSONArray normalized = RedmineIdUtil.parseLongArray(blockingIds);
                    for (int i = 0; i < normalized.length(); i++) {
                        long blockedId = normalized.getLong(i);
                        if (blockedId > 0) {
                            // 构建请求体
                            JSONObject requestBody = new JSONObject();
                            JSONObject relation = new JSONObject();
                            relation.put("issue_to_id", blockedId);
                            relation.put("relation_type", "blocks");
                            requestBody.put("relation", relation);


                            // 发起 POST 请求
                            FileLogger.d(TAG, "🚀 创建关系：" + taskId + " blocks " + blockedId);
                            sendPostRequest(redmineUrl + "/issues/" + taskId + "/relations.json", auth, requestBody.toString());
                            FileLogger.d(TAG, "✅ 关系创建成功：" + blockedId);
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


                FileLogger.i(TAG, "✅ 执行完成：task_id=" + taskId + ", blocked_by_count=" + result.getInt("blocked_by_count") + ", blocking_count=" + result.getInt("blocking_count"));
                callback.onResult(result);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ 执行出错：" + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }


    /**
     * 辅助方法：发送 POST 请求
     * */
    private void sendPostRequest(String urlString, RedmineAuth auth, String body) throws Exception {
        FileLogger.d(TAG, "📡 发送 POST 请求到：" + urlString);
        FileLogger.d(TAG, "📝 请求体：" + body);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        auth.apply(connection);
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }


        int responseCode = connection.getResponseCode();
        FileLogger.d(TAG, "📊 响应码：" + responseCode);

        if (responseCode != 201) {
            throw new RuntimeException("HTTP 请求失败，响应码：" + responseCode + ", URL: " + urlString);
        }


        connection.disconnect();
        FileLogger.d(TAG, "✅ 请求完成");
    }


    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求建立 Redmine 任务阻塞关系时才调用此工具。认证支持 api_key，或 username 与 password，并可从工具备注读取；API Key 不得输出到回复或日志。仅管理阻塞关系，不支持父子关系。"
            + "\n\n【重要】task_id 以及 blocked_by_ids / blocking_ids 数组元素均为字符串形式的数字 ID，不要传 JSON 数字；这是为了兼容 OpenAI Function Calling 协议。工具内部会自动转 long。";
    }
}
