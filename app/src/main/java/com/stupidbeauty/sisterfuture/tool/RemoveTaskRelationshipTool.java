package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.Context;
import android.util.Log;
import android.util.Base64;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;


/**
 * 工具类：删除 Redmine 任务之间的阻塞关系
 */
public class RemoveTaskRelationshipTool implements Tool {
    private static final String TAG = "RemTaskRel";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RemoveTaskRelationshipTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "removeTaskRelationship";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "removeTaskRelationship");
            functionDef.put("description", "删除 Redmine 任务之间的阻塞关系。支持通过 relation_id 删除指定关系，或批量删除某任务的所有阻塞关系。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("task_id", new JSONObject().put("type", "long").put("description", "目标任务的 ID（支持长整型，如 JoyMan 生成的 750160066086）"));
            props.put("relation_id", new JSONObject().put("type", "long").put("description", "要删除的关系 ID（可选）"));
            props.put("redmine_url", new JSONObject().put("type", "string").put("description", "Redmine 实例 URL"));
            props.put("username", new JSONObject().put("type", "string").put("description", "用户名"));
            props.put("password", new JSONObject().put("type", "string").put("description", "密码"));
            parameters.put("properties", props);
            parameters.put("required", new JSONArray().put("task_id"));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() { return true; }
    @Override
    public boolean isAsync() { return true; }

    @Override
    public void executeAsync(@NonNull JSONObject args, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // ✅ 修复：使用 long 类型处理 task_id（JoyMan 生成的任务 ID 是长整型）
                long taskId = args.getLong("task_id");
                long relationId = args.optLong("relation_id", -1);
                String redmineUrl = args.optString("redmine_url", "").trim();
                String username = args.optString("username", "").trim();
                String password = args.optString("password", "").trim();

                if (taskId <= 0 || redmineUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    throw new IllegalArgumentException("参数验证失败");
                }

                int deletedCount = 0;
                JSONArray deletedRelations = new JSONArray();

                if (relationId > 0) {
                    deleteRelation(redmineUrl, taskId, relationId, username, password);
                    deletedCount = 1;
                    deletedRelations.put(relationId);
                } else {
                    JSONArray relations = getRelations(redmineUrl, taskId, username, password);
                    for (int i = 0; i < relations.length(); i++) {
                        long relId = relations.getJSONObject(i).getLong("id");
                        try {
                            deleteRelation(redmineUrl, taskId, relId, username, password);
                            deletedCount++;
                            deletedRelations.put(relId);
                        } catch (Exception e) {
                            Log.e(TAG, "删除关系 " + relId + " 失败", e);
                        }
                    }
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "成功删除 " + deletedCount + " 个阻塞关系");
                result.put("task_id", taskId);
                result.put("deleted_count", deletedCount);
                result.put("deleted_relation_ids", deletedRelations);
                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                // ✅ 修复：直接调用 onError，让 ToolManager 的 handleParameterError 统一处理
                callback.onError(e);
            }
        });
    }

    private JSONArray getRelations(String url, long taskId, String user, String pass) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url + "/issues/" + taskId + "/relations.json").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        StringBuilder resp = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) resp.append(line);
        }
        conn.disconnect();
        return new JSONObject(resp.toString()).optJSONArray("relations");
    }

    private void deleteRelation(String url, long taskId, long relationId, String user, String pass) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url + "/issues/" + taskId + "/relations/" + relationId + ".json").openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        if (conn.getResponseCode() != 200) throw new RuntimeException("删除失败：" + conn.getResponseCode());
        conn.disconnect();
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求删除 Redmine 任务之间的阻塞关系时才调用此工具。需要提供 task_id 参数（支持长整型，如 JoyMan 生成的 750160066086），以及 redmine_url, username, password 等认证参数。";
    }
}