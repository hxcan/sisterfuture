// com.stupidbeauty.sisterfuture.tool.CreatePullRequestTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * GitHub Pull Request 创建工具（异步版本）
 * 通过 GitHub API 自动化创建 Pull Request
 * 
 * @author 未来姐姐
 * @version 2.0 (异步重构 + 增强调试)
 * @since 2026-03-30
 */
public class CreatePullRequestTool implements Tool
{
    private static final String TAG = "CreatePRTool";
    private static final String API_BASE = "https://api.github.com/repos";
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CreatePullRequestTool(Context context)
    {
        this.context = context;
    }

    @Override
    public String getName()
    {
        return "create_pull_request";
    }

    @Override
    public JSONObject getDefinition()
    {
        try
        {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "create_pull_request");
            functionDef.put("description", "通过 GitHub API 创建 Pull Request。支持指定标题、描述、分支、审核者等参数");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            JSONObject properties = new JSONObject();
            
            properties.put("owner", new JSONObject()
                .put("type", "string")
                .put("description", "仓库所有者（GitHub 用户名或组织名）"));
            
            properties.put("repo", new JSONObject()
                .put("type", "string")
                .put("description", "仓库名称"));
            
            properties.put("title", new JSONObject()
                .put("type", "string")
                .put("description", "Pull Request 标题"));
            
            properties.put("head", new JSONObject()
                .put("type", "string")
                .put("description", "源分支名称（要合并的分支）"));
            
            properties.put("base", new JSONObject()
                .put("type", "string")
                .put("default", "master")
                .put("description", "目标分支名称（被合并到的分支），默认 master"));
            
            properties.put("body", new JSONObject()
                .put("type", "string")
                .put("description", "Pull Request 描述（可选）"));
            
            properties.put("draft", new JSONObject()
                .put("type", "boolean")
                .put("default", false)
                .put("description", "是否创建为 Draft PR，默认 false"));
            
            properties.put("token", new JSONObject()
                .put("type", "string")
                .put("description", "GitHub Personal Access Token。如不提供，将尝试从工具备注读取 github_token"));
            
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray(new String[]{"owner", "repo", "title", "head", "base"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        }
        catch (Exception e)
        {
            FileLogger.e(TAG, "Failed to build definition", e);
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
        executor.execute(() -> {
            try
            {
                // === 1. 解析参数 ===
                String owner = arguments.getString("owner");
                String repo = arguments.getString("repo");
                String title = arguments.getString("title");
                String head = arguments.getString("head");
                String base = arguments.optString("base", "master");
                String body = arguments.optString("body", "");
                boolean draft = arguments.optBoolean("draft", false);
                String token = arguments.optString("token", "").trim();

                FileLogger.d(TAG, "=== 开始创建 Pull Request ===");
                FileLogger.d(TAG, "参数：owner=" + owner + ", repo=" + repo + ", title=" + title);
                FileLogger.d(TAG, "参数：head=" + head + ", base=" + base + ", draft=" + draft);
                FileLogger.d(TAG, "Token 长度：" + (token.isEmpty() ? 0 : token.length()));

                // === 2. 获取 Token（如果未提供）===
                if (token.isEmpty())
                {
                    FileLogger.d(TAG, "Token 为空，尝试从备注读取...");
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty())
                    {
                        try
                        {
                            JSONObject saved = new JSONObject(noteJson);
                            if (saved.has("github_token"))
                            {
                                token = saved.getString("github_token");
                                FileLogger.d(TAG, "✓ 从备注中读取到 github_token，长度：" + token.length());
                            }
                        }
                        catch (Exception e)
                        {
                            FileLogger.w(TAG, "解析备注失败：" + e.getMessage());
                        }
                    }
                }

                if (token.isEmpty())
                {
                    FileLogger.e(TAG, "✗ 缺少 GitHub token");
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "缺少 GitHub 访问令牌 (token)，且未在备注中配置");
                    error.put("logs", new JSONArray().put("✗ 错误：缺少 token"));
                    callback.onResult(error);
                    return;
                }

                // === 3. 构建请求 ===
                String apiUrl = API_BASE + "/" + owner + "/" + repo + "/pulls";
                FileLogger.d(TAG, "API URL: " + apiUrl);

                JSONObject prData = new JSONObject();
                prData.put("title", title);
                prData.put("head", head);
                prData.put("base", base);
                prData.put("body", body.isEmpty() ? "" : body);
                prData.put("draft", draft);

                FileLogger.d(TAG, "请求体：" + prData.toString(2));

                // === 4. 发送 HTTP 请求 ===
                OkHttpClient client = new OkHttpClient();
                
                RequestBody requestBody = RequestBody.create(
                    prData.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(requestBody)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "SisterFuture-CreatePullRequestTool")
                    .build();

                FileLogger.d(TAG, "发送 POST 请求到 GitHub API...");

                Response response = null;
                try
                {
                    response = client.newCall(request).execute();
                    int statusCode = response.code();
                    FileLogger.d(TAG, "响应状态码：" + statusCode);

                    String responseBody = response.body() != null ? response.body().string() : "";
                    FileLogger.d(TAG, "响应体长度：" + responseBody.length());
                    
                    if (statusCode >= 400)
                    {
                        FileLogger.e(TAG, "错误响应：" + responseBody);
                    }

                    // === 5. 处理成功响应 ===
                    if (statusCode == 201)
                    {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        int prNumber = jsonResponse.getInt("number");
                        String htmlUrl = jsonResponse.getString("html_url");

                        FileLogger.d(TAG, "✓ PR #" + prNumber + " 创建成功");
                        FileLogger.d(TAG, "URL: " + htmlUrl);

                        JSONArray logs = new JSONArray();
                        logs.put("开始创建 Pull Request...");
                        logs.put("仓库：" + owner + "/" + repo);
                        logs.put("分支：" + head + " → " + base);
                        logs.put("发送请求到 GitHub API...");
                        logs.put("响应状态码：" + statusCode);
                        logs.put("✓ PR #" + prNumber + " 创建成功");
                        logs.put("URL: " + htmlUrl);

                        JSONObject result = new JSONObject();
                        result.put("success", true);
                        result.put("pr_number", prNumber);
                        result.put("pr_url", htmlUrl);
                        result.put("logs", logs);
                        
                        callback.onResult(result);
                    }
                    // === 6. 处理失败响应 ===
                    else
                    {
                        String errorMessage = "未知错误";
                        try
                        {
                            JSONObject errorJson = new JSONObject(responseBody);
                            errorMessage = errorJson.optString("message", errorMessage);
                            
                            // 提取详细错误信息
                            if (errorJson.has("errors"))
                            {
                                JSONArray errors = errorJson.getJSONArray("errors");
                                FileLogger.e(TAG, "GitHub API 详细错误：");
                                for (int i = 0; i < errors.length(); i++)
                                {
                                    JSONObject err = errors.getJSONObject(i);
                                    FileLogger.e(TAG, "  - " + err.optString("field", "") + ": " + err.optString("message", ""));
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            FileLogger.w(TAG, "解析错误响应失败：" + e.getMessage());
                            errorMessage = responseBody.isEmpty() ? "HTTP " + statusCode : responseBody;
                        }

                        FileLogger.e(TAG, "✗ 创建失败：" + errorMessage);

                        JSONArray logs = new JSONArray();
                        logs.put("开始创建 Pull Request...");
                        logs.put("仓库：" + owner + "/" + repo);
                        logs.put("分支：" + head + " → " + base);
                        logs.put("发送请求到 GitHub API...");
                        logs.put("响应状态码：" + statusCode);
                        logs.put("✗ 创建失败：" + errorMessage);

                        JSONObject result = new JSONObject();
                        result.put("success", false);
                        result.put("error", errorMessage);
                        result.put("status_code", statusCode);
                        result.put("logs", logs);
                        
                        callback.onResult(result);
                    }
                }
                finally
                {
                    if (response != null)
                    {
                        response.close();
                    }
                }
            }
            catch (Exception e)
            {
                // === 7. 捕获异常 ===
                FileLogger.e(TAG, "✗ 执行异常", e);
                FileLogger.e(TAG, "异常类型：" + e.getClass().getName());
                FileLogger.e(TAG, "异常消息：" + (e.getMessage() != null ? e.getMessage() : "null"));
                
                // 打印完整堆栈到日志
                StringBuilder stackTrace = new StringBuilder();
                for (StackTraceElement element : e.getStackTrace())
                {
                    stackTrace.append("  at ").append(element.toString()).append("\n");
                }
                FileLogger.e(TAG, "堆栈跟踪:\n" + stackTrace.toString());

                try
                {
                    JSONArray logs = new JSONArray();
                    logs.put("开始创建 Pull Request...");
                    logs.put("✗ 异常：" + e.getClass().getSimpleName());
                    logs.put("详情：" + (e.getMessage() != null ? e.getMessage() : "null"));

                    JSONObject result = new JSONObject();
                    result.put("success", false);
                    result.put("error", e.getMessage() != null ? e.getMessage() : "Unknown exception: " + e.getClass().getName());
                    result.put("exception_type", e.getClass().getName());
                    result.put("logs", logs);
                    
                    callback.onResult(result);
                }
                catch (Exception ex)
                {
                    FileLogger.e(TAG, "构建错误响应时失败", ex);
                    // 最后的fallback
                    callback.onResult(new JSONObject());
                }
            }
        });
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求创建 GitHub Pull Request 时才调用此工具。需要提供 owner、repo、title、head、base 参数。token 参数可选，如不提供需确保已从工具备注中配置 github_token。支持创建 Draft PR（draft=true）。此为异步工具，不会阻塞主线程。";
    }

    /**
     * 从工具备注读取默认 token
     */
    @Override
    public String getNote(Context context)
    {
        // TODO: 实现从 ToolManager 读取备注的逻辑
        // 这里暂时返回空字符串
        return "";
    }
}