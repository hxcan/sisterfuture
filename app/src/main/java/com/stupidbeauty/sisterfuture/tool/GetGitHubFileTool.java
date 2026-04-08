package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
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
                    .put("save_to_phone", new JSONObject()
                            .put("type", "boolean")
                            .put("description", "是否将文件保存到手机存储（适用于大文件，避免返回内容撑爆上下文）"))
                    .put("phone_path", new JSONObject()
                            .put("type", "string")
                            .put("description", "手机保存路径，默认 /sdcard/Download/文件名"))
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
                String encoding = arguments.optString("encoding", "text");
                // 新增参数
                boolean saveToPhone = arguments.optBoolean("save_to_phone", false);
                String phonePath = arguments.optString("phone_path", "");

                // 创建结果对象，立即包含请求参数
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("request_params", new JSONObject()
                        .put("owner", owner)
                        .put("repo", repo)
                        .put("path", path)
                        .put("branch", branch)
                        .put("encoding", encoding)
                        .put("save_to_phone", saveToPhone));

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
                    try {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", "请求失败：" + response.code() + " " + response.message());
                        error.put("type", "IOException");
                        if (arguments != null) {
                            error.put("request_params", new JSONObject()
                                    .put("owner", arguments.optString("owner", ""))
                                    .put("repo", arguments.optString("repo", ""))
                                    .put("path", arguments.optString("path", ""))
                                    .put("branch", arguments.optString("branch", "master")));
                        }
                        error.put("sister_future_note", "请检查分支参数是否正确，当前仓库使用的是 \"master\" 分支而非 \"main\" 分支。\n原错误信息：" + response.message());
                        callback.onResult(error);
                    } catch (Exception ignored) {}
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("返回体为空");
                }
                String resultStr = body.string();
                JSONObject resultJson = new JSONObject(resultStr);

                // 处理保存到手机的功能
                if (saveToPhone && resultJson.has("content")) {
                    String encodedContent = resultJson.getString("content");
                    encodedContent = encodedContent.replaceAll("\\s+", "");
                    
                    byte[] decodedBytes;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        decodedBytes = Base64.getDecoder().decode(encodedContent);
                    } else {
                        decodedBytes = android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT);
                    }
                    
                    // 确定保存路径
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    if (phonePath.isEmpty()) {
                        phonePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + fileName;
                    }
                    
                    // 保存文件
                    File outputFile = new File(phonePath);
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(decodedBytes);
                    }
                    
                    // 返回保存成功信息
                    result.put("file_saved", true);
                    result.put("phone_path", phonePath);
                    result.put("file_size", decodedBytes.length);
                    result.put("fetched_at", System.currentTimeMillis());
                    callback.onResult(result);
                    return;
                }

                // 正常的返回逻辑（不解码Base64）
                if (resultJson.has("content") && resultJson.getString("encoding").equals("base64")) {
                    String encodedContent = resultJson.getString("content");
                    
                    // 如果 encoding="base64"，跳过解码，保留原始内容
                    if (!"base64".equalsIgnoreCase(encoding)) {
                        encodedContent = encodedContent.replaceAll("\\s+", "");
                        byte[] decodedBytes;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            decodedBytes = Base64.getDecoder().decode(encodedContent);
                        } else {
                            decodedBytes = android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT);
                        }
                        String decodedContent = new String(decodedBytes, StandardCharsets.UTF_8);
                        resultJson.put("decoded_content", decodedContent);
                    } else {
                        resultJson.put("raw_content", resultJson.getString("content"));
                        int originalLength = resultJson.getString("raw_content").length();
                        Log.d(TAG, "GetGitHubFile DEBUG: Returning Base64 with length: " + originalLength);
                        if (originalLength > 2500) {
                            Log.w(TAG, "GetGitHubFile WARNING: Large Base64 content detected (" + originalLength + " chars).");
                        }
                        }
                    }
                    resultJson.remove("content");
                }

                result.put("file_info", resultJson);
                result.put("fetched_at", System.currentTimeMillis());
                
                JSONObject debugInfo = new JSONObject();
                debugInfo.put("tool_name", "get_github_file");
                debugInfo.put("params", result.getJSONObject("request_params"));
                if (resultJson.has("raw_content")) {
                    String rawContent = resultJson.getString("raw_content");
                    debugInfo.put("raw_content_length", rawContent.length());
                    debugInfo.put("encoding_used", "base64");
                    debugInfo.put("warning_if_large", rawContent.length() > 2500);
                }
                result.put("debug_info", debugInfo);
                
                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    error.put("type", e.getClass().getSimpleName());
                    if (arguments != null) {
                        error.put("request_params", new JSONObject()
                                .put("owner", arguments.optString("owner", ""))
                                .put("repo", arguments.optString("repo", ""))
                                .put("path", arguments.optString("path", ""))
                                .put("branch", arguments.optString("branch", "master"))
                                .put("encoding", arguments.optString("encoding", "text"))
                                .put("save_to_phone", arguments.optBoolean("save_to_phone", false)));
                    }
                    error.put("sister_future_note", "请检查分支参数是否正确，当前仓库使用的是 \"master\" 分支而非 \"main\" 分支。\n原错误信息：" + e.getMessage());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    // --- 工具备注支持 ---
    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求读取 GitHub 文件时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 github_token 等配置。只有当备注中缺少某些字段时，才允许使用用户提供的对应参数作为 fallback。严禁工具自行验证 JSON 格式，这是助手的责任。增强要求：在返回结果中包含完整的请求参数信息（owner, repo, path, branch），以便于调试 404 等错误情况。\n\n新增功能：\n- 支持通过参数 encoding=\"base64\" 可选返回 Base64 编码的原始文件内容（不自动解码）\n- 对于二进制文件（.keystore, .png, .jpg 等），建议默认使用 base64 模式以避免数据损坏\n- 支持 save_to_phone 参数：将文件直接保存到手机存储，避免大文件返回撑爆上下文\n- 支持 phone_path 参数：指定手机保存路径\n- 适用场景建议：\n  - 二进制文件（.keystore, .apk, .png 等）必须使用 encoding=\"base64\"\n  - 大文件建议使用 save_to_phone=true 直接保存到手机";
    }
}
