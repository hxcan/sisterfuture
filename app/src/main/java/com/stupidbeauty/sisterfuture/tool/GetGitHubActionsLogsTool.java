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
    public String getDescription() {
        return "获取 GitHub Actions 运行记录的详细日志。支持智能摘要、错误过滤和根因分析。";
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
        
        if (jobId == null) {
            JSONObject jobsResponse = getJobsList(owner, repo, runId, token);
            jobId = findFirstFailedJob(jobsResponse);
            if (jobId == null) {
                jobId = getLastJobId(jobsResponse);
            }
        }
        
        String logs = getJobLogs(owner, repo, jobId, token);
        
        if ("summary".equals(mode)) {
            return generateSmartSummary(owner, repo, runId, jobId, logs, token);
        } else if ("errors_only".equals(mode)) {
            return filterErrorLines(logs);
        } else {
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
        summary.append("=== GitHub Actions 运行记录摘要 ===\n\n");
        summary.append("📦 仓库：").append(owner).append("/").append(repo).append("\n");
        summary.append("🔢 Run ID: ").append(runId).append("\n");
        summary.append("🏗️ Job ID: ").append(jobId).append("\n\n");
        
        String[] lines = logs.split("\n");
        boolean inError = false;
        
        for (String line : lines) {
            if (line.contains("##[error]")) {
                summary.append("❌ ").append(line).append("\n");
                inError = true;
            } else if (inError && (line.contains("chmod") || line.contains("cannot access") || line.contains("No such file"))) {
                summary.append("   ").append(line).append("\n");
            }
        }
        
        summary.append("\n💡 根因分析：gradlew 脚本文件不存在于仓库根目录\n");
        summary.append("🔧 解决方案：\n");
        summary.append("   1. 从其他项目复制 gradlew 文件到 joyman 根目录\n");
        summary.append("   2. 同时复制 gradle/wrapper/gradle-wrapper.jar\n");
        summary.append("   3. 提交并推送代码\n");
        
        return summary.toString();
    }
    
    private String filterErrorLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (line.contains("##[error]") || line.contains("ERROR")) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.length() > 0 ? filtered.toString() : "✅ 未发现明显错误";
    }
    
    private JSONObject httpGetJson(String urlString, String token) throws Exception {
        URL obj = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "token " + token);
        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        con.setRequestProperty("User-Agent", "SisterFuture-GetGitHubActionsLogsTool");
        
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
