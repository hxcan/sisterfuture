// com.stupidbeauty.sisterfuture.tool.GetGitHubActionsLogsTool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * GitHub Actions 日志获取工具
 * 
 * @author 太极美术工程狮狮长
 * @version 3.0.1 (修复 JSONException 未处理问题)
 */
public class GetGitHubActionsLogsTool implements Tool {
    
    private static final String TAG = "GetGHActionsLogs";
    private static final String API_BASE = "https://api.github.com/repos";
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetGitHubActionsLogsTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "get_github_actions_logs";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "get_github_actions_logs");
            functionDef.put("description", "获取 GitHub Actions 运行记录的详细日志。支持智能摘要和错误过滤，适用于任何类型的 GitHub 仓库。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("owner", new JSONObject()
                    .put("type", "string")
                    .put("description", "仓库所有者（必需）"))
                .put("repo", new JSONObject()
                    .put("type", "string")
                    .put("description", "仓库名称（必需）"))
                .put("runId", new JSONObject()
                    .put("type", "integer")
                    .put("description", "Workflow Run ID（必需）"))
                .put("jobId", new JSONObject()
                    .put("type", "integer")
                    .put("description", "Job ID（可选，不填则自动选择第一个失败的 job）"))
                .put("mode", new JSONObject()
                    .put("type", "string")
                    .put("description", "返回模式 summary|errors_only|full（可选，默认 summary）"))
                .put("token", new JSONObject()
                    .put("type", "string")
                    .put("description", "GitHub Token（可选，从工具备注读取）"))
            );
            parameters.put("required", new JSONArray(new String[]{"owner", "repo", "runId"}));

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
                String owner = arguments.getString("owner");
                String repo = arguments.getString("repo");
                long runId = arguments.getLong("runId");
                Long jobId = arguments.has("jobId") && !arguments.isNull("jobId") ? arguments.getLong("jobId") : null;
                String mode = arguments.optString("mode", "summary");
                String token = arguments.optString("token", "").trim();

                FileLogger.d(TAG, "获取日志：owner=" + owner + ", repo=" + repo + ", runId=" + runId + ", jobId=" + jobId + ", mode=" + mode);

                // 如果未提供 token，尝试从工具备注读取
                if (token.isEmpty()) {
                    String noteJson = getNote(context);
                    if (!noteJson.isEmpty()) {
                        JSONObject saved = new JSONObject(noteJson);
                        if (saved.has("github_token")) {
                            token = saved.getString("github_token");
                            FileLogger.d(TAG, "从备注中读取到 github_token");
                        }
                    }
                }

                if (token.isEmpty()) {
                    throw new IllegalArgumentException("缺少 GitHub 访问令牌 (token)，且未在备注中配置");
                }

                OkHttpClient client = new OkHttpClient();

                // 如果未指定 jobId，先获取 Job 列表并自动选择
                if (jobId == null) {
                    JSONObject jobsResponse = getJobsList(client, token, owner, repo, runId);
                    jobId = findFirstFailedJob(jobsResponse);

                    if (jobId == null) {
                        // 如果没有失败的 job，使用最后一个 job
                        jobId = getLastJobId(jobsResponse);
                    }

                    if (jobId == null) {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", "未找到任何 Job");
                        callback.onResult(error);
                        return;
                    }

                    FileLogger.d(TAG, "自动选择 jobId: " + jobId);
                }

                // 获取详细日志（纯文本）
                String logs = getJobLogs(client, token, owner, repo, jobId);

                // 根据 mode 处理日志
                String result;
                if ("summary".equals(mode)) {
                    result = generateSummary(client, token, owner, repo, runId, jobId, logs);
                } else if ("errors_only".equals(mode)) {
                    result = filterErrorLines(logs);
                } else {
                    // full mode
                    result = logs;
                }

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "日志获取成功！");
                response.put("content", result);
                response.put("run_id", runId);
                response.put("job_id", jobId);
                response.put("mode", mode);
                response.put("fetched_at", System.currentTimeMillis());

                callback.onResult(response);

            } catch (Exception e) {
                FileLogger.e(TAG, "执行出错", e);
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

    /**
     * 从工具备注读取默认 token
     */
    @Override
    public String getNote(Context context) {
        // TODO: 实现从 ToolManager 读取备注的逻辑
        // 这里暂时返回空字符串
        return "";
    }

    private JSONObject getJobsList(OkHttpClient client, String token, String owner, String repo, long runId) throws Exception {
        String url = API_BASE + "/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs";
        return httpGetJson(client, token, url);
    }

    private String getJobLogs(OkHttpClient client, String token, String owner, String repo, long jobId) throws Exception {
        String url = API_BASE + "/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs";
        
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("User-Agent", "SisterFuture-GetGitHubActionsLogsTool")
            .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("获取日志失败：" + response.code() + " " + response.message());
        }

        return response.body().string();
    }

    private Long findFirstFailedJob(JSONObject jobsResponse) throws Exception {
        JSONArray jobs = jobsResponse.getJSONArray("jobs");
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.getJSONObject(i);
            if ("failure".equals(job.optString("conclusion"))) {
                return job.getLong("id");
            }
        }
        return null;
    }

    private Long getLastJobId(JSONObject jobsResponse) throws Exception {
        JSONArray jobs = jobsResponse.getJSONArray("jobs");
        if (jobs.length() > 0) {
            return jobs.getJSONObject(jobs.length() - 1).getLong("id");
        }
        return null;
    }

    /**
     * 生成摘要 - 只展示事实信息，不做根因分析
     */
    private String generateSummary(OkHttpClient client, String token, String owner, String repo, 
                                    long runId, long jobId, String logs) {
        StringBuilder summary = new StringBuilder();

        // 获取 Job 元数据
        String jobName = "unknown";
        String jobConclusion = "unknown";
        JSONArray steps = null;

        try {
            JSONObject jobsResponse = getJobsList(client, token, owner, repo, runId);
            JSONArray jobs = jobsResponse.getJSONArray("jobs");
            for (int i = 0; i < jobs.length(); i++) {
                JSONObject job = jobs.getJSONObject(i);
                if (job.getLong("id") == jobId) {
                    jobName = job.getString("name");
                    jobConclusion = job.getString("conclusion");
                    steps = job.getJSONArray("steps");
                    break;
                }
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "获取 Job 元数据失败", e);
        }

        // 构建摘要头部
        summary.append("=== GitHub Actions 运行记录摘要 ===\n\n");
        summary.append("📦 仓库：").append(owner).append("/").append(repo).append("\n");
        summary.append("🔢 Run ID: ").append(runId).append("\n");
        summary.append("🏗️  Job: ").append(jobName).append("\n");
        summary.append("📊 状态：");

        if ("failure".equals(jobConclusion)) {
            summary.append("❌ 失败\n\n");
        } else if ("success".equals(jobConclusion)) {
            summary.append("✅ 成功\n\n");
        } else {
            summary.append("⚠️ ").append(jobConclusion).append("\n\n");
        }

        // 收集错误和跳过的步骤
        JSONArray errorSteps = new JSONArray();
        JSONArray skippedSteps = new JSONArray();

        if (steps != null) {
            try {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    String conclusion = step.optString("conclusion", "");

                    if ("failure".equals(conclusion)) {
                        errorSteps.put(step);
                    } else if ("skipped".equals(conclusion)) {
                        skippedSteps.put(step);
                    }
                }
            } catch (Exception e) {
                FileLogger.e(TAG, "解析步骤失败", e);
            }
        }

        // 输出错误步骤详情（只展示原始错误信息）
        if (errorSteps.length() > 0) {
            summary.append("--- ❌ 失败的步骤 ---\n\n");

            for (int i = 0; i < errorSteps.length(); i++) {
                JSONObject step;
                try {
                    step = errorSteps.getJSONObject(i);
                } catch (Exception e) {
                    FileLogger.e(TAG, "读取错误步骤失败", e);
                    continue;
                }
                String stepName = step.optString("name", "Unknown");
                int stepNumber = step.optInt("number", -1);

                summary.append("❌ [Step ").append(stepNumber).append("] ").append(stepName).append("\n");

                // 从日志中提取该步骤的错误信息（只展示原始日志）
                String errorMessage = extractErrorMessageForStep(logs, stepName);
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    summary.append("   错误日志：\n");
                    // 格式化错误日志，每行前加缩进
                    String[] errorLines = errorMessage.split("\n");
                    for (String line : errorLines) {
                        summary.append("   ").append(line).append("\n");
                    }
                }
                summary.append("\n");
            }
        }

        // 输出跳过的步骤
        if (skippedSteps.length() > 0) {
            summary.append("--- ⏭️ 跳过的步骤（由于上述错误）---\n");
            for (int i = 0; i < skippedSteps.length(); i++) {
                JSONObject step;
                try {
                    step = skippedSteps.getJSONObject(i);
                } catch (Exception e) {
                    FileLogger.e(TAG, "读取跳过步骤失败", e);
                    continue;
                }
                summary.append("   - ").append(step.optString("name", "Unknown"))
                       .append(" (Step ").append(step.optInt("number", -1)).append(")\n");
            }
            summary.append("\n");
        }

        // 统计信息
        summary.append("📊 统计：");
        if (steps != null) {
            int successCount = 0, failureCount = 0, skippedCount = 0;
            try {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    String conclusion = step.optString("conclusion", "");
                    if ("success".equals(conclusion)) successCount++;
                    else if ("failure".equals(conclusion)) failureCount++;
                    else if ("skipped".equals(conclusion)) skippedCount++;
                }
            } catch (Exception e) {
                FileLogger.e(TAG, "统计失败", e);
            }
            summary.append("成功 ").append(successCount).append(" 步，失败 ")
                   .append(failureCount).append(" 步，跳过 ").append(skippedCount).append(" 步\n");
        }

        return summary.toString();
    }

    /**
     * 从日志中提取错误信息
     */
    private String extractErrorMessageForStep(String logs, String stepName) {
        String[] lines = logs.split("\n");
        StringBuilder errors = new StringBuilder();
        boolean inErrorSection = false;

        for (String line : lines) {
            if (line.contains("##[error]")) {
                inErrorSection = true;
                errors.append(line.substring(line.indexOf("##[error]"))).append("\n");
            } else if (inErrorSection) {
                // 继续收集错误相关的行，直到遇到新的 section
                if (line.startsWith("20") && line.contains("Z ")) {
                    // 可能是新的时间戳行，停止收集
                    break;
                }
                errors.append(line).append("\n");
            }
        }

        return errors.length() > 0 ? errors.toString().trim() : null;
    }

    /**
     * 过滤只返回错误行
     */
    private String filterErrorLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");

        for (String line : lines) {
            if (line.contains("##[error]") || line.contains("ERROR") || 
                line.contains("failed") || line.contains("exception")) {
                filtered.append(line).append("\n");
            }
        }

        return filtered.length() > 0 ? filtered.toString() : "✅ 未发现明显错误";
    }

    private JSONObject httpGetJson(OkHttpClient client, String token, String urlString) throws Exception {
        Request request = new Request.Builder()
            .url(urlString)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "SisterFuture-GetGitHubActionsLogsTool")
            .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new Exception("HTTP 请求失败：" + response.code() + " " + response.message());
        }

        String responseBody = response.body().string();
        return new JSONObject(responseBody);
    }
}