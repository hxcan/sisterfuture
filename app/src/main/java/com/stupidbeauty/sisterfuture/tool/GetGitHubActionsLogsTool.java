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
    private static final Pattern[] RUNNER_OP_PATTERNS = {
        // 匹配 "Post job cleanup" 或类似内容（行中任何位置出现）
        Pattern.compile("Post job cleanup"),
        Pattern.compile("Cleaning up orphan processes"),
        Pattern.compile("Terminate orphan process"),
        // Git 命令（行中任何位置出现）
        Pattern.compile("\\[command\\]/usr/bin/git "),
        Pattern.compile("Temporarily overriding HOME"),
        Pattern.compile("Adding repository directory.*safe\\.directory"),
        // GitHub Actions 颜色代码
        Pattern.compile("\\[[0-9;]+m\\[0m"),
        // Git 版本输出行
        Pattern.compile("git version [0-9]"),
        // git config/safe.directory 等操作
        Pattern.compile("git config.*safe\\.directory"),
        // git init/remote/fetch/checkout/log/branch/status
        Pattern.compile("git (init|remote|fetch|checkout|log|branch|status)"),
        // 清理相关的 orphan process
        Pattern.compile("orphan"),
        // Node.js deprecation warning
        Pattern.compile("Node\\.js 20 actions are deprecated"),
        // Post job cleanup group/endgroup
        Pattern.compile("#\\[group\\]Post job cleanup"),
        Pattern.compile("#\\[endgroup\\]"),
        // tar 命令（缓存恢复时）
        Pattern.compile("\\[command\\]/usr/bin/tar "),
        // cp 命令
        Pattern.compile("\\[command\\]/usr/bin/cp "),
        // apksigner 命令
        Pattern.compile("\\[command\\]/usr/local/lib/android/sdk/build-tools/[0-9.]+/apksigner "),
        // zipalign 命令
        Pattern.compile("\\[command\\]/usr/local/lib/android/sdk/build-tools/[0-9.]+/zipalign "),
        // sdkmanager 命令
        Pattern.compile("\\[command\\].*sdkmanager "),
        // gradlew 命令
        Pattern.compile("\\[command\\].*gradlew "),
        // chmod 命令
        Pattern.compile("\\[command\\].*chmod "),
        // export 命令
        Pattern.compile("\\[command\\].*export "),
        // yes 命令
        Pattern.compile("\\[command\\].*yes "),
        // echo 命令
        Pattern.compile("\\[command\\].*echo "),
        // ls 命令
        Pattern.compile("\\[command\\].*ls "),
        // find 命令
        Pattern.compile("\\[command\\].*find "),
        // Setting up auth
        Pattern.compile("Setting up auth"),
        // Disabling automatic garbage collection
        Pattern.compile("Disabling automatic garbage collection"),
        // Initializing the repository
        Pattern.compile("Initializing the repository"),
        // Checking out the ref
        Pattern.compile("Checking out the ref"),
        // Determining the checkout info
        Pattern.compile("Determining the checkout info"),
        // Fetching the repository
        Pattern.compile("Fetching the repository"),
        // Syncing repository
        Pattern.compile("Syncing repository"),
        // Getting Git version info
        Pattern.compile("Getting Git version info"),
        // Prepare workflow directory
        Pattern.compile("Prepare workflow directory"),
        // Prepare all required actions
        Pattern.compile("Prepare all required actions"),
        // Getting action download info
        Pattern.compile("Getting action download info"),
        // Download action repository
        Pattern.compile("Download action repository"),
        // Complete job name
        Pattern.compile("Complete job name"),
        // Runner Image Provisioner
        Pattern.compile("Runner Image Provisioner"),
        // Operating System
        Pattern.compile("Operating System"),
        // Runner Image
        Pattern.compile("Runner Image"),
        // GITHUB_TOKEN Permissions
        Pattern.compile("GITHUB_TOKEN Permissions"),
        // Secret source
        Pattern.compile("Secret source"),
        // Installed distributions
        Pattern.compile("Installed distributions"),
        // Resolved Java
        Pattern.compile("Resolved Java"),
        // Setting Java
        Pattern.compile("Setting Java"),
        // Creating toolchains.xml
        Pattern.compile("Creating toolchains.xml"),
        // Writing to
        Pattern.compile("Writing to"),
        // Java configuration
        Pattern.compile("Java configuration"),
        // Creating settings.xml
        Pattern.compile("Creating settings.xml"),
        // Cache hit for
        Pattern.compile("Cache hit for"),
        // Received
        Pattern.compile("Received"),
        // Cache Size
        Pattern.compile("Cache Size"),
        // Cache restored successfully
        Pattern.compile("Cache restored successfully"),
        // Cache restored from key
        Pattern.compile("Cache restored from key"),
        // Starting a Gradle Daemon
        Pattern.compile("Starting a Gradle Daemon"),
        // Configuration on demand
        Pattern.compile("Configuration on demand"),
        // Welcome to Gradle
        Pattern.compile("Welcome to Gradle"),
        // Here are the highlights
        Pattern.compile("Here are the highlights"),
        // For more details see
        Pattern.compile("For more details see"),
        // To honour the JVM settings
        Pattern.compile("To honour the JVM settings"),
        // Daemon will be stopped
        Pattern.compile("Daemon will be stopped"),
        // It will be removed in version
        Pattern.compile("It will be removed in version"),
        // Using it has no effect
        Pattern.compile("Using it has no effect"),
        // Android SDK Build Tools
        Pattern.compile("Android SDK Build Tools"),
        // To suppress this warning
        Pattern.compile("To suppress this warning"),
        // Checking the license
        Pattern.compile("Checking the license"),
        // License for package
        Pattern.compile("License for package"),
        // Preparing
        Pattern.compile("Preparing"),
        // ready
        Pattern.compile("ready"),
        // Installing
        Pattern.compile("Installing"),
        // complete
        Pattern.compile("complete"),
        // finished
        Pattern.compile("finished"),
        // Unable to strip the following libraries
        Pattern.compile("Unable to strip the following libraries"),
        // packaging them as they are
        Pattern.compile("packaging them as they are"),
        // Wrote HTML report
        Pattern.compile("Wrote HTML report"),
        // With the provided path
        Pattern.compile("With the provided path"),
        // Artifact name is valid
        Pattern.compile("Artifact name is valid"),
        // Root directory input is valid
        Pattern.compile("Root directory input is valid"),
        // Beginning upload
        Pattern.compile("Beginning upload"),
        // Uploaded bytes
        Pattern.compile("Uploaded bytes"),
        // Finished uploading
        Pattern.compile("Finished uploading"),
        // SHA256 digest
        Pattern.compile("SHA256 digest"),
        // Finalizing artifact upload
        Pattern.compile("Finalizing artifact upload"),
        // Artifact .* successfully finalized
        Pattern.compile("Artifact .* successfully finalized"),
        // Artifact .* has been successfully uploaded
        Pattern.compile("Artifact .* has been successfully uploaded"),
        // Artifact download URL
        Pattern.compile("Artifact download URL"),
        // Releases signed
        Pattern.compile("Releases signed"),
        // Verification succesful
        Pattern.compile("Verification succesful"),
        // The `set-output` command is deprecated
        Pattern.compile("The `set-output` command is deprecated"),
        // Please upgrade to using Environment Files
        Pattern.compile("Please upgrade to using Environment Files"),
        // For more information see
        Pattern.compile("For more information see"),
        // Node.js 20 actions are deprecated
        Pattern.compile("Node.js 20 actions are deprecated"),
        // Actions will be forced to run with Node.js 24
        Pattern.compile("Actions will be forced to run with Node.js 24"),
        // Node.js 20 will be removed
        Pattern.compile("Node.js 20 will be removed"),
        // Please check if updated versions
        Pattern.compile("Please check if updated versions"),
        // To opt into Node.js 24 now
        Pattern.compile("To opt into Node.js 24 now"),
        // set the FORCE_JAVASCRIPT_ACTIONS_TO_NODE24
        Pattern.compile("set the FORCE_JAVASCRIPT_ACTIONS_TO_NODE24"),
        // Once Node.js 24 becomes the default
        Pattern.compile("Once Node.js 24 becomes the default"),
        // you can temporarily opt out
        Pattern.compile("you can temporarily opt out"),
        // setting ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION
        Pattern.compile("setting ACTIONS_ALLOW_USE_UNSECURE_NODE_VERSION"),
        // deprecation-of-node-20-on-github-actions-runners
        Pattern.compile("deprecation-of-node-20-on-github-actions-runners")
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