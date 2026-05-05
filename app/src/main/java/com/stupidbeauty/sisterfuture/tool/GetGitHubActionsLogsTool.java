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
    
    // Runner 环境操作日志过滤正则表达式模式
    private static final Pattern[] RUNNER_OP_PATTERNS = {
        Pattern.compile("^Post job cleanup"),
        Pattern.compile("^Cleaning up orphan processes"),
        Pattern.compile("^Terminate orphan process"),
        Pattern.compile("^[\\s]*\\[command\\]/usr/bin/git (version|config|init|remote|fetch|checkout|log|branch|status)"),
        Pattern.compile("^Temporarily overriding HOME"),
        Pattern.compile("^Adding repository directory.*safe\\.directory"),
        Pattern.compile("^\\[\\d{2};\\d{2}m.*\\[0m"),
        Pattern.compile("^#{4}\\[group\\]Post job cleanup"),
        Pattern.compile("^#{4}\\[endgroup\\]")
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
    // 最多记录被过滤内容的行数，避免日志过长
    private static final int MAX_FILTERED_LOG_LINES = 10;

    /**
     * 过滤 Runner 环境操作日志行
     * @param logs 原始日志
     * @return 过滤后的日志
     */
    private String filterRunnerOpLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        StringBuilder filteredOutContent = new StringBuilder();
        String[] lines = logs.split("\n");
        int totalLines = lines.length;
        int filteredOutCount = 0;

        for (String line : lines) {
            if (!isRunnerOpLine(line)) {
                filtered.append(line).append("\n");
            } else {
                filteredOutCount++;
                // 只记录前几行被过滤的内容，避免日志过长
                if (filteredOutCount <= MAX_FILTERED_LOG_LINES) {
                    filteredOutContent.append(line).append("\n");
                }
            }
        }

        // 记录调试日志
        FileLogger.d(TAG, "过滤 Runner 操作日志: 总行数=" + totalLines + ", 过滤行数=" + filteredOutCount);
        if (filteredOutCount > 0) {
            String contentPreview = filteredOutCount > MAX_FILTERED_LOG_LINES 
                ? filteredOutContent.toString() + "...(还有 " + (filteredOutCount - MAX_FILTERED_LOG_LINES) + " 行未显示)"
                : filteredOutContent.toString();
            FileLogger.d(TAG, "被过滤的 Runner 操作日志内容(最多显示" + MAX_FILTERED_LOG_LINES + "行):\n" + contentPreview);
        }

        return filtered.toString();
    }
            }
        }

        return filtered.toString();
    }