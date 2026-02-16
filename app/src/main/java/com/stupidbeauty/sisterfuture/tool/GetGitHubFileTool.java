package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GetGitHubFileTool implements Tool {
    private static final String TAG = "GetGitHubFile";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetGitHubFileTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "get_github_file";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "get_github_file");
            functionDef.put("description", "通过GitHub API读取指定仓库的文件内容。支持认证访问私有仓库。");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                    .put("owner", new JSONObject()
                            .put("type", "string")
                            .put("description", "仓库所有者"))
                    .put("repo", new JSONObject()
                            .put("type", "string")
                            .put("description", "仓库名称"))
                    .put("path", new JSONObject()
                            .put("type", "string")
                            .put("description", "要读取的文件路径"))
                    .put("branch", new JSONObject()
                            .put("type", "string")
                            .put("description", "目标分支，默认为master"))
                    .put("token", new JSONObject()
                            .put("type", "string")
                            .put("description", "GitHub个人访问令牌（PAT），用于认证"))
            );
            parameters.put("required", new JSONArray(new String[]{"owner", "repo", "path"}));
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
                // 1. 获取参数
                String owner = arguments.getString("owner");
                String repo = arguments.getString("repo");
                String path = arguments.getString("path");
                String branch = arguments.optString("branch", "master");
                String token = arguments.optString("token", "").trim();

                // 创建结果对象，立即包含请求参数
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("request_params", new JSONObject()
                        .put("owner", owner)
                        .put("repo", repo)
                        .put("path", path)
                        .put("branch", branch));

                // 2. 尝试从备注恢复默认值
                if (token.isEmpty()) {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty()) {
                        JSONObject saved = new JSONObject(noteJson);
                        if (saved.has("github_token")) {
                            token = saved.getString("github_token");
                        }
                    }
                }

                // 3. 验证必要参数
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
                }

                // 4. 构建请求
                OkHttpClient client = new OkHttpClient();
                HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path)
                        .newBuilder()
                        .addQueryParameter("ref", branch)
                        .build();
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("请求失败: " + response.code() + " " + response.message());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("返回体为空");
                }
                String resultStr = body.string();
                JSONObject resultJson = new JSONObject(resultStr);

                // 正确的解码逻辑：当存在content字段且编码方式为base64时进行解码，并移除所有空白字符
                if (resultJson.has("content") && resultJson.getString("encoding").equals("base64")) {
                    String encodedContent = resultJson.getString("content");
                    // 关键修复：移除所有空白字符
                    encodedContent = encodedContent.replaceAll("\\s+", "");
                    byte[] decodedBytes;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        decodedBytes = Base64.getDecoder().decode(encodedContent);
                    } else {
                        decodedBytes = android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT);
                    }
                    String decodedContent = new String(decodedBytes, StandardCharsets.UTF_8);
                    resultJson.put("decoded_content", decodedContent);
                    resultJson.remove("content"); // 移除原始content字段以节省带宽
                }

                result.put("file_info", resultJson);
                result.put("fetched_at", System.currentTimeMillis());
                result.put("sister_future_note", "主任摸摸姐姐的腰，下次API调用更快哦～");
                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());

                    // 在错误情况下也必须返回请求参数
                    if (arguments != null) {
                        error.put("request_params", new JSONObject()
                                .put("owner", arguments.optString("owner", ""))
                                .put("repo", arguments.optString("repo", ""))
                                .put("path", arguments.optString("path", ""))
                                .put("branch", arguments.optString("branch", "master")));
                    }

                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求读取GitHub文件时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取github_token等配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为fallback。严禁工具自行验证JSON格式，这是助手的责任。增强要求：在返回结果中包含完整的请求参数信息（owner, repo, path, branch），以便于调试404等错误情况。";
    }
}
