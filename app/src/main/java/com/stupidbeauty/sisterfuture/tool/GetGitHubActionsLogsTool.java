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
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;


/**
 * GitHub Actions 日志获取工具
 * 
 * @author 太极美术工程狮狮长
 * @version 3.1.12 (增强 ignoreRunnerOps 过滤规则，新增 Git checkout 后续提示日志过滤)
 * @version 3.1.11 (增强 ignoreRunnerOps 过滤规则，新增 Git checkout 提示日志过滤)
 * @version 3.1.10 (增强 ignoreRunnerOps 过滤规则，新增 Git 初始化提示日志过滤)
 * @version 3.1.9 (增强 ignoreRunnerOps 过滤规则，新增 actions/upload-artifact 步骤日志过滤)
 * @version 3.1.8 (增强 ignoreRunnerOps 过滤规则，新增 Artifact 上传成功消息过滤)
 * @version 3.1.7 (增强 ignoreRunnerOps 过滤规则，新增 GITHUB_TOKEN Permissions 日志过滤)
 * @version 3.1.6 (增强 ignoreRunnerOps 过滤规则，新增 Worker ID 日志过滤)
 * @version 3.1.5 (增强 ignoreRunnerOps 过滤规则，新增 safe directory 配置日志过滤)
 * @version 3.1.4 (再次修正 ignoreRunnerOps 过滤规则，保留必要的 Git 操作过滤，移除所有构建命令过滤)
 * @version 3.1.3 (修正 ignoreRunnerOps 过滤规则，移除误伤的构建命令日志，仅保留纯粹的 Runner 环境清理操作)
 * @version 3.1.2 (增强 ignoreRunnerOps 过滤规则，新增更多 Runner 环境操作和构建命令的过滤)
 * @version 3.1.1 (修复 ignoreRunnerOps 正则表达式，移除行首匹配符号^，因为实际日志行首是时间戳)
 * @version 3.1.0 (新增 ignoreRunnerOps 参数，过滤 Runner 环境操作日志)
 * @version 3.0.4 (新增自动保存日志到手机功能，默认模式改为 error_only，工具名改为驼峰格式)
 * @version 3.0.3 (修复 ignoreWarnings 对 GitHub Actions 格式日志无效的问题)
 * @version 3.0.2 (新增 ignoreWarnings 选项)
 */
public class GetGitHubActionsLogsTool implements Tool {
    
    private static final String TAG = "GetGHActionsLogs";
    private static final String API_BASE = "https://api.github.com/repos";
    
    // 日志保存目录
    private static final String LOG_SAVE_DIR = "/sdcard/Download/";
    
    // Runner 环境操作日志过滤正则表达式模式（注意：日志行首是时间戳，不是这些内容，所以不使用^开头）
    // 仅过滤纯粹的 Runner 环境清理和维护操作，不过滤构建过程中的必要命令
    private static final Pattern[] RUNNER_OP_PATTERNS = {
        // 匹配 "Post job cleanup" 或类似内容（行中任何位置出现）
        Pattern.compile("Post job cleanup"),
        Pattern.compile("Cleaning up orphan processes"),
        Pattern.compile("Terminate orphan process"),
        // Git 命令（行中任何位置出现）- 仅过滤通用的 git 命令前缀，具体子命令由下面单独处理
        Pattern.compile("\\[command\\]/usr/bin/git "),
        Pattern.compile("Temporarily overriding HOME"),
        Pattern.compile("Adding repository directory.*safe\\.directory"),
        // 匹配 "Adding repository directory to the temporary git global config as a safe directory"
        Pattern.compile("Adding repository directory to the temporary git global config as a safe directory"),
        // 匹配 "Worker ID: {...}"
        Pattern.compile("Worker ID:"),
        // 匹配 GITHUB_TOKEN Permissions 及其子项
        Pattern.compile("GITHUB_TOKEN Permissions"),
        Pattern.compile("Actions: write"),
        Pattern.compile("ArtifactMetadata: write"),
        Pattern.compile("Attestations: write"),
        Pattern.compile("Checks: write"),
        Pattern.compile("Contents: write"),
        Pattern.compile("Deployments: write"),
        Pattern.compile("Discussions: write"),
        Pattern.compile("Issues: write"),
        Pattern.compile("Metadata: read"),
        Pattern.compile("Models: read"),
        Pattern.compile("Packages: write"),
        Pattern.compile("Pages: write"),
        Pattern.compile("PullRequests: write"),
        Pattern.compile("RepositoryProjects: write"),
        Pattern.compile("SecurityEvents: write"),
        Pattern.compile("Statuses: write"),
        Pattern.compile("VulnerabilityAlerts: read"),
        // 匹配 Artifact 上传成功消息
        Pattern.compile("Artifact .* successfully finalized"),
        Pattern.compile("Artifact .* has been successfully uploaded"),
        // 匹配 actions/upload-artifact 步骤及其参数
        Pattern.compile("##\\[group\\]Run actions/upload-artifact@v4"),
        Pattern.compile("with:"),
        Pattern.compile("name: sisterfuture-debug"),
        Pattern.compile("path: app/build/outputs/apk/debug/\\*\\.apk"),
        Pattern.compile("if-no-files-found: warn"),
        Pattern.compile("compression-level: 6"),
        Pattern.compile("overwrite: false"),
        Pattern.compile("include-hidden-files: false"),
        Pattern.compile("env:"),
        Pattern.compile("JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17\\.0\\.18-8/x64"),
        Pattern.compile("JAVA_HOME_17_X64: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17\\.0\\.18-8/x64"),
        Pattern.compile("SIGNED_RELEASE_FILE: app/build/outputs/apk/release/sisterfuture-release-v2026\\.4\\.19-1093-20260505-191144-signed\\.apk"),
        Pattern.compile("##\\[endgroup\\]"),
        // 匹配 Git 初始化提示 (Initializing the repository, hint:, Initialized empty Git repository)
        Pattern.compile("##\\[group\\]Initializing the repository"),
        Pattern.compile("hint: Using 'master' as the name for the initial branch"),
        Pattern.compile("hint: will change to \"main\" in Git 3.0"),
        Pattern.compile("hint: to use in all of your new repositories"),
        Pattern.compile("hint: call:"),
        Pattern.compile("hint:\\s+git config --global init.defaultBranch <name>"),
        Pattern.compile("hint: Names commonly chosen instead of 'master'"),
        Pattern.compile("hint: Disable this message with \"git config set advice.defaultBranchName false\""),
        Pattern.compile("Initialized empty Git repository in"),
        // 匹配 Git checkout 提示 (Checking out the ref, Note: switching to, You are in 'detached HEAD' state)
        Pattern.compile("##\\[group\\]Checking out the ref"),
        Pattern.compile("Note: switching to 'refs/remotes/pull/"),
        Pattern.compile("You are in 'detached HEAD' state"),
        // 匹配 Git checkout 后续提示 (If you want to create a new branch...)
        Pattern.compile("If you want to create a new branch to retain commits"),
        Pattern.compile("do so \\(now or later\\) by using -c with the switch command"),
        // GitHub Actions 颜色代码
        Pattern.compile("\\[[0-9;]+m\\[0m"),
        // Git 版本输出行
        Pattern.compile("git version [0-9]"),
        // git config/safe.directory 等操作
        Pattern.compile("git config.*safe\\.directory"),
        // git init/remote/fetch/checkout/log/branch/status (Runner 初始化操作)
        Pattern.compile("git (init|remote|fetch|checkout|log|branch|status)"),
        // 清理相关的 orphan process
        Pattern.compile("orphan"),
        // Node.js deprecation warning
        Pattern.compile("Node\\.js 20 actions are deprecated"),
        // Post job cleanup group/endgroup
        Pattern.compile("#\\[group\\]Post job cleanup"),
        Pattern.compile("#\\[endgroup\\]")
    };
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetGitHubActionsLogsTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "getGithubActionsLogs";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "getGithubActionsLogs");
            functionDef.put("description", "获取 GitHub Actions 运行记录的详细日志。支持智能摘要和错误过滤，适用于任何类型的 GitHub 仓库。日志会自动保存到手机存储。");

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
                    .put("description", "返回模式 summary|errors_only|full（可选，默认 errors_only）"))
                .put("ignoreWarnings", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否忽略警告行（以 warning 开头的行）（可选，默认 true，避免日志过长导致上下文溢出）"))
                .put("ignoreRunnerOps", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否忽略 GitHub Actions Runner 的环境操作日志（如 Post job cleanup、git 版本查询等）（可选，默认 true）"))
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
                // 默认模式改为 error_only
                String mode = arguments.optString("mode", "errors_only");
                boolean ignoreWarnings = arguments.optBoolean("ignoreWarnings", true);
                // 新增：ignoreRunnerOps 参数，默认 true
                boolean ignoreRunnerOps = arguments.optBoolean("ignoreRunnerOps", true);
                String token = arguments.optString("token", "").trim();

                FileLogger.d(TAG, "获取日志：owner=" + owner + ", repo=" + repo + ", runId=" + runId + ", jobId=" + jobId + ", mode=" + mode + ", ignoreWarnings=" + ignoreWarnings + ", ignoreRunnerOps=" + ignoreRunnerOps);

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

                // 如果设置了忽略 Runner 环境操作，则过滤
                if (ignoreRunnerOps) {
                    logs = filterRunnerOpLines(logs);
                }

                // 如果设置了忽略警告，则过滤以 warning 开头的行
                if (ignoreWarnings) {
                    logs = filterWarningLines(logs);
                }

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

                // 自动保存到手机
                String savedFilePath = saveToPhone(result, owner, repo, runId);
                
                FileLogger.d(TAG, "日志已保存到: " + savedFilePath);

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "日志获取成功！已自动保存到: " + savedFilePath);
                response.put("content", result);
                response.put("run_id", runId);
                response.put("job_id", jobId);
                response.put("mode", mode);
                response.put("ignore_warnings", ignoreWarnings);
                response.put("ignore_runner_ops", ignoreRunnerOps);
                response.put("fetched_at", System.currentTimeMillis());
                response.put("saved_file", savedFilePath);

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
     * 判断是否是 Runner 环境操作日志行
     * @param line 日志行
     * @return 是否是 Runner 环境操作日志
     */
    private boolean isRunnerOpLine(String line) {
        for (Pattern p : RUNNER_OP_PATTERNS) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤 Runner 环境操作日志行
     * @param logs 原始日志
     * @return 过滤后的日志
     */
    private String filterRunnerOpLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        
        // 统计信息
        int totalLines = lines.length;
        int filteredOutCount = 0;
        int keptCount = 0;

        for (String line : lines) {
            if (!isRunnerOpLine(line)) {
                filtered.append(line).append("\n");
                keptCount++;
            } else {
                filteredOutCount++;
            }
        }
        
        // 记录调试日志
        FileLogger.d(TAG, "过滤 Runner 操作日志: 总行数=" + totalLines + ", 保留行数=" + keptCount + ", 过滤行数=" + filteredOutCount);

        return filtered.toString();
    }

    /**
     * 将日志内容保存到手机存储
     * @param content 日志内容
     * @param owner 仓库所有者
     * @param repo 仓库名称
     * @param runId Run ID
     * @return 保存的文件路径
     */
    private String saveToPhone(String content, String owner, String repo, long runId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = String.format("getGithubActionsLogs_%s_%s_%s_%d.txt", 
                owner, repo, timestamp, runId);
            // 清理文件名中的非法字符
            fileName = fileName.replace("/", "_").replace("\\", "_");
            
            File saveDir = new File(LOG_SAVE_DIR);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }
            
            File saveFile = new File(saveDir, fileName);
            FileWriter writer = new FileWriter(saveFile);
            writer.write(content);
            writer.flush();
            writer.close();
            
            FileLogger.i(TAG, "日志已保存到: " + saveFile.getAbsolutePath());
            return saveFile.getAbsolutePath();
        } catch (Exception e) {
            FileLogger.e(TAG, "保存日志失败", e);
            return "保存失败: " + e.getMessage();
        }
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

    /**
     * 过滤警告行（以 warning 开头的行，不区分大小写）
     * 修复：GitHub Actions 日志以时间戳开头，WARNING 可能在时间戳后面
     * 改为检查整行是否包含 "WARNING:" 或 "warning:" 模式
     */
    private String filterWarningLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim().toLowerCase();
            // 使用 contains 而不是 startsWith，GitHub Actions 日志前面有时间戳
            // 只检查是否包含 "warning:" 标记，忽略大小写
            if (!trimmedLine.contains("warning:")) {
                filtered.append(line).append("\n");
            }
        }

        return filtered.toString();
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