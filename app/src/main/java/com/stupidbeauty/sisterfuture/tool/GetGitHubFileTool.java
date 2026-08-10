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

public class GetGitHubFileTool implements Tool
{
    private static final String TAG = "GetGitHubFile";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetGitHubFileTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "getGitHubFile";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "getGitHubFile");
            functionDef.put("description", "从 GitHub API 读取指定仓库的文件内容。");

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
                            .put("description", "文件路径"))
                    .put("branch", new JSONObject()
                            .put("type", "string")
                            .put("description", "分支，默认为 master"))
                    .put("token", new JSONObject()
                            .put("type", "string")
                            .put("description", "GitHub 令牌（PAT），用于认证"))
                    .put("encoding", new JSONObject()
                            .put("type", "string")
                            .put("description", "返回模式：\"text\"（自动解码 Base64，默认）或 \"base64\"（原始 Base64 字符串）。可用值：text、base64。keystore、.apk、.png、.jpg 等二进制文件必须用 encoding=\"base64\"。"))
                    .put("save_to_phone", new JSONObject()
                            .put("type", "boolean")
                            .put("description", "是否保存到手机本地目录（/sdcard/Download/文件名）。仅在 encoding=\"text\" 或二进制文件（keystore、.apk、.png、.jpg）时有效；其他情况（音频、视频、.key、.pem 等）建议保存为临时副本。"))
                    .put("phone_path", new JSONObject()
                            .put("type", "string")
                            .put("description", "手机本地保存路径（/sdcard/Download/文件名），留空使用默认目录")));

            parameters.put("required", new JSONArray(new String[]{"owner", "repo", "path"}));

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
                // 1. 从 arguments 中获取 string 参数，转换为 JSONObjectException
                String owner = arguments.getString("owner");
                String repo = arguments.getString("repo");
                String path = arguments.getString("path");
                String branch = arguments.optString("branch", "master");
                String token = arguments.optString("token", "").trim();
                String encoding = arguments.optString("encoding", "text");

                // 可选参数
                boolean saveToPhone = arguments.optBoolean("save_to_phone", false);
                String phonePath = arguments.optString("phone_path", "");

                // 2. 如果token为空，从note中查找token
                if (token.isEmpty())
                {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty())
                    {
                        try
                        {
                            JSONObject saved = new JSONObject(noteJson);
                            if (saved.has("github_token"))
                            {
                                token = saved.getString("github_token");
                            }
                        }
                        catch (Exception ignored)
                        {
                            // 解析失败时忽略，使用空token继续
                            Log.w(TAG, "Failed to parse tool remark for token, ignoring.");
                        }
                    }
                }

                // 3. 如果token仍为空，抛出异常
                if (token.isEmpty())
                {
                    throw new IllegalArgumentException("Missing required parameter: token");
                }

                // 4. 构造请求地址
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

                if (!response.isSuccessful())
                {
                    try
                    {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", "请求失败：" + response.code() + " " + response.message());
                        error.put("type", "IOException");

                        if (arguments != null)
                        {
                            error.put("request_params", new JSONObject()
                                    .put("owner", arguments.optString("owner", ""))
                                    .put("repo", arguments.optString("repo", ""))
                                    .put("path", arguments.optString("path", ""))
                                    .put("branch", arguments.optString("branch", "master")));
                        }

                        // 提示用 LLM 调用其他工具时不要混淆同名工具
                        if (response.code() == 404)
                        {
                            error.put("sister_future_note", "⚠️ 404 错误可能原因：\n" +
                                    "1. 文件路径 (path) 不正确 - 请确认文件确实存在于仓库中\n" +
                                    "2. 分支 (branch) 错误 - 当前仓库默认分支是 master，不是 main\n" +
                                    "3. owner/repo 错误 - 请确认仓库所有者和服务名称正确\n" +
                                    "建议：可以使用 listFtpDirectory 工具查看仓库目录结构，或请用户确认正确的文件路径。\n\n" +
                                    "原始错误信息：" + response.message());
                        }
                        else
                        {
                            error.put("sister_future_note", "请求失败，建议先尝试：\"master\"（而不是 \"main\"）分支。\n" +
                                    "原始错误信息：" + response.message());
                        }

                        callback.onResult(error);
                    }
                    catch (Exception ignored)
                    {}
                    return;
                }

                ResponseBody body = response.body();
                if (body == null)
                {
                    throw new IOException("响应体为空");
                }

                String resultStr = body.string();
                JSONObject resultJson = new JSONObject(resultStr);

                // 根据encoding参数决定返回内容格式
                if (saveToPhone && resultJson.has("content"))
                {
                    String encodedContent = resultJson.getString("content");
                    encodedContent = encodedContent.replaceAll("\\s+", "");
                    byte[] decodedBytes;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    {
                        decodedBytes = Base64.getDecoder().decode(encodedContent);
                    }
                    else
                    {
                        decodedBytes = android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT);
                    }

                    // 取文件名
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    if (phonePath.isEmpty())
                    {
                        phonePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName;
                    }

                    // 写文件
                    File outputFile = new File(phonePath);
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists())
                    {
                        parentDir.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(outputFile))
                    {
                        fos.write(decodedBytes);
                    }

                    // 返回保存结果
                    resultJson = new JSONObject();
                    resultJson.put("file_saved", true);
                    resultJson.put("phone_path", phonePath);
                    resultJson.put("file_size", decodedBytes.length);
                    resultJson.put("fetched_at", System.currentTimeMillis());
                    callback.onResult(resultJson);
                    return;
                }

                // 如果不保存但要返回 Base64 解码内容
                if (resultJson.has("content") && resultJson.getString("encoding").equals("base64"))
                {
                    String encodedContent = resultJson.getString("content");

                    // 传 encoding="base64" 时不动，但 Base64 可用
                    if (!"base64".equalsIgnoreCase(encoding))
                    {
                        encodedContent = encodedContent.replaceAll("\\s+", "");
                        byte[] decodedBytes;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        {
                            decodedBytes = Base64.getDecoder().decode(encodedContent);
                        }
                        else
                        {
                            decodedBytes = android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT);
                        }
                        String decodedContent = new String(decodedBytes, StandardCharsets.UTF_8);
                        resultJson.put("decoded_content", decodedContent);
                    }
                    else
                    {
                        resultJson.put("raw_content", resultJson.getString("content"));
                        int originalLength = resultJson.getString("raw_content").length();
                        Log.d(TAG, "GetGitHubFile DEBUG: Returning Base64 with length: " + originalLength);
                        if (originalLength > 2500)
                        {
                            Log.w(TAG, "GetGitHubFile WARNING: Large Base64 content detected (" + originalLength + " chars).");
                        }
                    }
                    resultJson.remove("content");
                }

                callback.onResult(resultJson);
            }
            catch (Exception e)
            {
                Log.e(TAG, "姐姐遇到了问题", e);
                // 告诉 LLM 说 PAT 错误，让它去 ToolManager 里换正确的
                callback.onError(e);
            }
        });
    }

    // ----- 关于工具描述的辅助方法 -----
    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "使用场景：从 GitHub 仓库读取文件内容或二进制文件。调用前必须确保 owner、repo、path、branch 参数正确。当 GitHub 返回的原始响应大小超过 2500 个字符时，自动改用 Base64 编码；可通过指定 encoding=\"base64\" 强制使用 Base64 编码以保留 keystone、.apk、.png、.jpg 等二进制文件。检索二进制文件时（.keystore、.apk、.png 等），请使用 encoding=\"base64\"。";
    }
}