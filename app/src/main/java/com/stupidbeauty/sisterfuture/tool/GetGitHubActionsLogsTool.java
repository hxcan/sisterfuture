package com.stupidbeauty.sisterfuture.tool;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub Actions 日志获取工具
 * 
 * @author 太极美术工程狮狮长
 * @version 1.0.0
 */
public class GetGitHubActionsLogsTool implements Tool {
    
    private static final String TAG = "GetGHActionsLogs";
    private static final String API_BASE = "https://api.github.com/repos";
    
    @Override
    public String getName() {
        return "get_github_actions_logs";
    }
    
    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject def = new JSONObject();
            def.put("name", getName());
            def.put("description", "获取 GitHub Actions 运行记录的详细日志。支持智能摘要、错误过滤和根因分析。");
            
            JSONArray params = new JSONArray();
            params.put(createParam("owner", "string", "仓库所有者（必需）"));
            params.put(createParam("repo", "string", "仓库名称（必需）"));
            params.put(createParam("runId", "integer", "Workflow Run ID（必需）"));
            params.put(createParam("jobId", "integer", "Job ID（可选，不填则自动选择第一个失败的 job）"));
            params.put(createParam("mode", "string", "返回模式 summary|errors_only|full（可选，默认 summary）"));
            params.put(createParam("token", "string", "GitHub Token（可选，从工具备注读取）"));
            
            def.put("parameters", params);
            return def;
        } catch (Exception e) {
            Log.e(TAG, "创建定义失败", e);
            return null;
        }
    }
    
    private JSONObject createParam(String name, String type, String desc) throws Exception {
        JSONObject param = new JSONObject();
        param.put("name", name);
        param.put("type", type);
        param.put("description", desc);
        return param;
    }
    
    @Override
    public boolean shouldInclude() {
        return true;
    }
    
    @Override
    public List<String> getParameterNames() {
        List<String> params = new ArrayList<>();
        params.add("owner");
        params.add("repo");
        params.add("runId");
        params.add("jobId");
        params.add("mode");
        params.add("token");
        return params;
    }
    
    @Override
    public String callTool(List<String> args) throws Exception {
        if (args.size() < 3) {
            throw new IllegalArgumentException("需要至少 3 个参数：owner, repo, runId");
        }
        
        String owner = args.get(0);
        String repo = args.get(1);
        long runId = Long.parseLong(args.get(2));
        Long jobId = args.size() > 3 && !args.get(3).isEmpty() ? Long.parseLong(args.get(3)) : null;
        String mode = args.size() > 4 && !args.get(4).isEmpty() ? args.get(4) : "summary";
        String token = args.size() > 5 && !args.get(5).isEmpty() ? args.get(5) : null;
        
        // 如果未提供 token，尝试从工具备注读取
        if (token == null || token.isEmpty()) {
            token = getToolRemarkToken();
        }
        
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("需要提供 GitHub Token 参数或在工具备注中配置");
        }
        
        Log.d(TAG, "获取日志：owner=" + owner + ", repo=" + repo + ", runId=" + runId);
        
        // 如果未指定 jobId，先获取 Job 列表并自动选择
        if (jobId == null) {
            JSONObject jobsResponse = getJobsList(owner, repo, runId, token);
            jobId = findFirstFailedJob(jobsResponse);
            
            if (jobId == null) {
                // 如果没有失败的 job，使用最后一个 job
                jobId = getLastJobId(jobsResponse);
            }
            
            if (jobId == null) {
                return "❌ 错误：未找到任何 Job";
            }
            
            Log.d(TAG, "自动选择 jobId: " + jobId);
        }
        
        // 获取详细日志（纯文本）
        String logs = getJobLogs(owner, repo, jobId, token);
        
        // 根据 mode 处理日志
        if ("summary".equals(mode)) {
            return generateSmartSummary(owner, repo, runId, jobId, logs, token);
        } else if ("errors_only".equals(mode)) {
            return filterErrorLines(logs);
        } else {
            // full mode
            return logs;
        }
    }
    
    /**
     * 从工具备注读取默认 token（模拟实现）
     * 实际使用时需要从 ToolManager 获取备注
     */
    private String getToolRemarkToken() {
        // TODO: 实现从 ToolManager 读取备注的逻辑
        // 这里返回 null，强制用户传入 token 参数
        return null;
    }
    
    private JSONObject getJobsList(String owner, String repo, long runId, String token) throws Exception {
        String url = API_BASE + "/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs";
        return httpGetJson(url, token);
    }
    
    private String getJobLogs(String owner, String repo, long jobId, String token) throws Exception {
        String url = API_BASE + "/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "token " + token);
        con.setRequestProperty("User-Agent", "SisterFuture-GetGitHubActionsLogsTool");
        
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder logs = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            logs.append(inputLine).append("\n");
        }
        in.close();
        con.disconnect();
        
        return logs.toString();
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
    
    private String generateSmartSummary(String owner, String repo, long runId, long jobId, 
                                        String logs, String token) {
        StringBuilder summary = new StringBuilder();
        
        // 获取 Job 元数据
        String jobName = "unknown";
        String jobConclusion = "unknown";
        JSONArray steps = null;
        
        try {
            JSONObject jobsResponse = getJobsList(owner, repo, runId, token);
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
            Log.e(TAG, "获取 Job 元数据失败", e);
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
        
        // 分析错误步骤
        List<JSONObject> errorSteps = new ArrayList<>();
        List<JSONObject> skippedSteps = new ArrayList<>();
        
        if (steps != null) {
            try {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    String conclusion = step.optString("conclusion", "");
                    
                    if ("failure".equals(conclusion)) {
                        errorSteps.add(step);
                    } else if ("skipped".equals(conclusion)) {
                        skippedSteps.add(step);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "解析步骤失败", e);
            }
        }
        
        // 输出错误步骤详情
        if (!errorSteps.isEmpty()) {
            summary.append("--- ❌ 错误步骤 ---\n\n");
            
            for (JSONObject step : errorSteps) {
                String stepName = step.optString("name", "Unknown");
                int stepNumber = step.optInt("number", -1);
                
                summary.append("❌ [Step ").append(stepNumber).append("] ").append(stepName).append("\n");
                
                // 从日志中提取该步骤的错误信息
                String errorMessage = extractErrorMessageForStep(logs, stepName);
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    summary.append("   错误信息：").append(errorMessage).append("\n");
                }
                
                // 根因分析
                String rootCause = analyzeRootCause(stepName, errorMessage);
                summary.append("\n💡 根因分析：").append(rootCause).append("\n");
                
                // 解决方案建议
                String solution = suggestFix(stepName, errorMessage);
                summary.append("🔧 解决方案：").append(solution).append("\n\n");
            }
        }
        
        // 输出跳过的步骤
        if (!skippedSteps.isEmpty()) {
            summary.append("--- ⏭️ 跳过的步骤（由于上述错误）---\n");
            for (JSONObject step : skippedSteps) {
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
                Log.e(TAG, "统计失败", e);
            }
            summary.append("成功 ").append(successCount).append(" 步，失败 ")
                   .append(failureCount).append(" 步，跳过 ").append(skippedCount).append(" 步\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 从日志中提取指定步骤的错误信息
     */
    private String extractErrorMessageForStep(String logs, String stepName) {
        // 简单实现：查找包含 ##[error] 的行
        String[] lines = logs.split("\n");
        StringBuilder errors = new StringBuilder();
        
        for (String line : lines) {
            if (line.contains("##[error]") || line.contains("cannot access") || 
                line.contains("No such file") || line.contains("failed")) {
                errors.append(line.trim()).append("\n");
            }
        }
        
        return errors.length() > 0 ? errors.toString().trim() : null;
    }
    
    /**
     * 根因分析
     */
    private String analyzeRootCause(String stepName, String errorMessage) {
        if (errorMessage == null) errorMessage = "";
        
        if (stepName.contains("gradlew") && errorMessage.contains("No such file")) {
            return "gradlew 脚本文件不存在于仓库根目录";
        } else if (errorMessage.contains("permission denied")) {
            return "文件权限不足，需要执行 chmod +x";
        } else if (errorMessage.contains("OutOfMemoryError")) {
            return "内存不足，Gradle 构建需要更多内存";
        } else if (errorMessage.contains("connection timed out")) {
            return "网络连接超时，无法下载依赖";
        } else if (errorMessage.contains("compilation failed")) {
            return "代码编译失败，存在语法错误或类型不匹配";
        } else {
            return "未知错误，需要查看详细日志";
        }
    }
    
    /**
     * 提供解决方案建议
     */
    private String suggestFix(String stepName, String errorMessage) {
        if (errorMessage == null) errorMessage = "";
        
        if (stepName.contains("gradlew") && errorMessage.contains("No such file")) {
            return "1. 从其他项目复制 gradlew 文件到仓库根目录\n" +
                   "   2. 同时复制 gradle/wrapper/gradle-wrapper.jar\n" +
                   "   3. 提交并推送代码";
        } else if (errorMessage.contains("permission denied")) {
            return "在 CI 配置中添加步骤：chmod +x gradlew";
        } else if (errorMessage.contains("OutOfMemoryError")) {
            return "在 gradle.properties 中增加：org.gradle.jvmargs=-Xmx4096m";
        } else if (errorMessage.contains("connection timed out")) {
            return "1. 检查网络连接\n" +
                   "   2. 配置国内镜像源（如阿里云 Maven 镜像）\n" +
                   "   3. 重试构建";
        } else if (errorMessage.contains("compilation failed")) {
            return "1. 查看完整编译错误信息\n" +
                   "   2. 修复代码语法错误\n" +
                   "   3. 重新提交代码";
        } else {
            return "请查看详细日志以定位具体问题";
        }
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
     * HTTP GET 请求，返回 JSON
     */
    private JSONObject httpGetJson(String urlString, String token) throws Exception {
        URL obj = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "token " + token);
        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        con.setRequestProperty("User-Agent", "SisterFuture-GetGitHubActionsLogsTool");
        
        int responseCode = con.getResponseCode();
        Log.d(TAG, "HTTP 响应码：" + responseCode);
        
        if (responseCode != 200) {
            throw new Exception("HTTP 请求失败：" + responseCode);
        }
        
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String inputLine;
        
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        con.disconnect();
        
        return new JSONObject(response.toString());
    }
}
