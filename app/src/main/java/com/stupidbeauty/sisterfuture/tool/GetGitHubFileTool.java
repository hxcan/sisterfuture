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
            functionDef.put("description", "通过 GitHub API 读取指定仓库的文件内容。支持认证访问私有仓库。");
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
                            .put("description", "目标分支，默认为 master"))
                    .put("token", new JSONObject()
                            .put("type", "string")
                            .put("description", "GitHub 个人访问令牌 (PAT)，用于认证"))
                    .put("encoding", new JSONObject()
                            .put("type", "string")
                            .put("description", "返回模式：\"text\"（自动解码 Base64，默认）或 \"base64\"（保留原始编码）"))
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
                String encoding = arguments.optString("encoding", "text"); // 新增参数，默认"text"

                // 创建结果对象，立即包含请求参数
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("request_params", new JSONObject()
                        .put("owner", owner)
                        .put("repo", repo)
                        .put("path", path)
                        .put("branch", branch)
                        .put("encoding", encoding));

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
                    // 在错误情况下利用 sister_future_note 字段提供提示
                    try {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", "请求失败：" + response.code() + " " + response.message());
                        error.put("type", "IOException");

                        // 必须返回请求参数
                        if (arguments != null) {
                            error.put("request_params", new JSONObject()
                                    .put("owner", arguments.optString("owner", ""))
                                    .put("repo", arguments.optString("repo", ""))
                                    .put("path", arguments.optString("path", ""))
                                    .put("branch", arguments.optString("branch", "master")));
                        }

                        // 利用该字段向大模型发送调试提示
                        error.put("sister_future_note", "请检查分支参数是否正确，当前仓库使用的是 \"master\" 分支而非 \"main\" 分支。\n原错误信息：" + response.message());

                        callback.onResult(error);
                    } catch (Exception ignored) {}
                    return; // 结束执行
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("返回体为空");
                }
                String resultStr = body.string();
                JSONObject resultJson = new JSONObject(resultStr);

                // 正确的解码逻辑：当存在 content 字段、编码方式为 base64 且 encoding 参数不是"base64"时才解码
                if (resultJson.has("content") && resultJson.getString("encoding").equals("base64")) {
                    String encodedContent = resultJson.getString("content");
                    
                    // 调试日志：记录原始内容长度
                    int originalLength = encodedContent.length();
                    Log.d(TAG, "GetGitHubFile DEBUG: Original Base64 content length: " + originalLength);

                    // 如果 encoding="base64"，跳过解码，保留原始内容
                    if (!"base64".equalsIgnoreCase(encoding)) {
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
                        // 调试日志：记录解码后的大小
                        Log.d(TAG, "GetGitHubFile DEBUG: Decoded content size: " + decodedContent.length() + " bytes");
                    } else {
                        // 保留原始的 encoding="base64" 模式下的 content 字段
                        resultJson.put("raw_content", resultJson.getString("content"));
                        // 调试日志：记录返回的 Base64 内容长度和预计文件大小
                        Log.d(TAG, "GetGitHubFile DEBUG: Returning Base64 with length: " + originalLength);
                        Log.d(TAG, "GetGitHubFile DEBUG: Expected binary size approx: " + (originalLength * 3 / 4) + " bytes");
                        
                        // 添加完整性验证提示
                        if (originalLength > 2500) {
                            Log.w(TAG, "GetGitHubFile WARNING: Large Base64 content detected (" + originalLength + " chars). " +
                                   "May be truncated by LLM during transfer. Consider using write_memory or add_note for storage.");
                        }
                    }
                    resultJson.remove("content"); // 移除原始 content 字段以节省带宽
                }

                result.put("file_info", resultJson);
                result.put("fetched_at", System.currentTimeMillis());
                
                // 添加调试信息到结果中
                JSONObject debugInfo = new JSONObject();
                debugInfo.put("tool_name", "get_github_file");
                debugInfo.put("params", result.getJSONObject("request_params"));
                if (resultJson.has("raw_content")) {
                    debugInfo.put("raw_content_length", resultJson.getString("raw_content").length());
                    debugInfo.put("encoding_used", "base64");
                    debugInfo.put("warning_if_large", resultJson.getInt("raw_content").length() > 2500);
                }
                result.put("debug_info", debugInfo);
                
                // 成功情况下不再添加任何附加信息
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
                                .put("branch", arguments.optString("branch", "master"))
                                .put("encoding", arguments.optString("encoding", "text")));
                    }

                    // 利用该字段向大模型发送调试提示
                    error.put("sister_future_note", "请检查分支参数是否正确，当前仓库使用的是 \"master\" 分支而非 \"main\" 分支。\n原错误信息：" + e.getMessage());

                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求读取 GitHub 文件时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 github_token 等配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。增强要求：在返回结果中包含完整的请求参数信息（owner, repo, path, branch），以便于调试 404 等错误情况。\n\n新增功能：\n- 支持通过参数 encoding=\"base64\" 可选返回 Base64 编码的原始文件内容（不自动解码）\n- 对于二进制文件（.keystore, .png, .jpg 等），建议默认使用 base64 模式以避免数据损坏\n- 返回结构包含：content (Base64 字符串), encoding(\"base64\"或\"text\"), 以及原有的 file_info 和 request_params\n- 适用场景建议：\n  - 二进制文件（.keystore, .apk, .png 等）必须使用 encoding=\"base64\"\n  - 文本文件（.yml, .md, .java 等）使用默认 encoding=\"text\" 以节省资源\n\n**调试增强：**\n- 自动记录并返回 raw_content_length 参数\n- 对超过 2500 字符的 Base64 内容发出警告提示\n- 在 response 中包含 debug_info 字段供分析截断问题";
    }
}
