// com.stupidbeauty.sisterfuture.tool.CreatePullRequestTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub Pull Request 创建工具
 * 通过 GitHub API 自动化创建 Pull Request
 * 
 * @author 未来姐姐
 * @version 1.0
 * @since 2026-03-30
 */
public class CreatePullRequestTool implements Tool
{
    private final Context context;

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
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude()
    {
        return true;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception
    {
        String owner = arguments.getString("owner");
        String repo = arguments.getString("repo");
        String title = arguments.getString("title");
        String head = arguments.getString("head");
        String base = arguments.optString("base", "master");
        String body = arguments.optString("body", "");
        boolean draft = arguments.optBoolean("draft", false);
        String token = arguments.optString("token", null);

        // 如果未提供 token，尝试从系统获取（实际项目中可从配置读取）
        if (token == null || token.isEmpty())
        {
            throw new IllegalArgumentException("必须提供 GitHub token 参数");
        }

        List<String> logs = new ArrayList<>();
        logs.add("开始创建 Pull Request...");
        logs.add("仓库：" + owner + "/" + repo);
        logs.add("分支：" + head + " → " + base);

        try
        {
            // 构建 API URL
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "token " + token);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            // 构建请求体
            JSONObject prData = new JSONObject();
            prData.put("title", title);
            prData.put("head", head);
            prData.put("base", base);
            prData.put("body", body);
            prData.put("draft", draft);

            logs.add("发送请求到 GitHub API...");

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8))
            {
                writer.write(prData.toString());
                writer.flush();
            }

            int statusCode = conn.getResponseCode();
            logs.add("响应状态码：" + statusCode);

            if (statusCode == 201)
            {
                // 成功创建
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                int prNumber = jsonResponse.getInt("number");
                String htmlUrl = jsonResponse.getString("html_url");

                logs.add("✓ PR #" + prNumber + " 创建成功");
                logs.add("URL: " + htmlUrl);

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("pr_number", prNumber);
                result.put("pr_url", htmlUrl);
                result.put("logs", new JSONArray(logs));
                
                conn.disconnect();
                return result;
            }
            else
            {
                // 失败
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null)
                {
                    errorResponse.append(line);
                }
                errorReader.close();

                String errorMessage = "未知错误";
                try
                {
                    JSONObject errorJson = new JSONObject(errorResponse.toString());
                    errorMessage = errorJson.optString("message", errorMessage);
                }
                catch (Exception e)
                {
                    errorMessage = errorResponse.toString();
                }

                logs.add("✗ 创建失败：" + errorMessage);

                JSONObject result = new JSONObject();
                result.put("success", false);
                result.put("error", errorMessage);
                result.put("status_code", statusCode);
                result.put("logs", new JSONArray(logs));
                
                conn.disconnect();
                return result;
            }
        }
        catch (Exception e)
        {
            logs.add("✗ 异常：" + e.getMessage());
            
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("logs", new JSONArray(logs));
            return result;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement()
    {
        return "必须在用户明确要求创建 GitHub Pull Request 时才调用此工具。需要提供 owner、repo、title、head、base 参数。token 参数可选，如不提供需确保已从工具备注中配置 github_token。支持创建 Draft PR（draft=true）。";
    }
}