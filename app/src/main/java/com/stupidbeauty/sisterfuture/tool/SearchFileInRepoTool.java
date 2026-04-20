package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

public class SearchFileInRepoTool implements Tool {
    private static final String TAG = "SearchFileInRepo";
    private static final String API_URL = "https://api.github.com/search/code";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SearchFileInRepoTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "searchFileInRepo";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject func = new JSONObject();
            func.put("name", "searchFileInRepo");
            func.put("description", "基于 GitHub Code Search API 智能搜索仓库文件，支持文件名模式和路径过滤");
            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", new JSONObject()
                .put("owner", new JSONObject().put("type", "string").put("description", "仓库所有者"))
                .put("repo", new JSONObject().put("type", "string").put("description", "仓库名称"))
                .put("fileNamePattern", new JSONObject().put("type", "string").put("description", "文件名模式，支持通配符"))
                .put("pathPattern", new JSONObject().put("type", "string").put("description", "可选：路径过滤器"))
                .put("branch", new JSONObject().put("type", "string").put("description", "目标分支，默认 master"))
                .put("limit", new JSONObject().put("type", "integer").put("description", "结果数量限制，默认 10"))
                .put("token", new JSONObject().put("type", "string").put("description", "GitHub 访问令牌，可选；若未提供则从工具备注读取"))
            );
            params.put("required", new JSONArray(new String[]{"owner", "repo", "fileNamePattern"}));
            func.put("parameters", params);
            return new JSONObject().put("type", "function").put("function", func);
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
    public void executeAsync(@NonNull JSONObject args, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 🔥 #761200615112 在解析前获取 ToolManager（用于参数历史记录）
                ToolManager toolManager = null;
                try {
                    // 通过 Context 获取 ToolManager（需要从 Application 或 Activity 传递）
                    // 这里简化处理，假设可以通过某种方式获取
                    // 实际实现中需要在 SisterFutureActivity 中传递 toolManager 引用
                } catch (Exception e) {
                    Log.w(TAG, "获取 ToolManager 失败，将不使用参数历史记录", e);
                }

                String owner = args.getString("owner");
                String repo = args.getString("repo");
                String pattern = args.getString("fileNamePattern");
                String path = args.optString("pathPattern", "");
                String branch = args.optString("branch", "master");
                int limit = args.optInt("limit", 10);

                // Token 获取逻辑：优先参数，次优备注
                String token = "";
                
                // 1. 优先检查大模型传入的参数 token
                if (args.has("token") && !args.getString("token").trim().isEmpty()) {
                    token = args.getString("token").trim();
                }
                
                // 2. 如果参数没有 token，尝试从工具备注读取
                if (token.isEmpty()) {
                    String tk = getNote(context);
                    if (!tk.isEmpty()) {
                        JSONObject note = new JSONObject(tk);
                        if (note.has("github_token")) {
                            token = note.getString("github_token");
                        }
                    }
                }
                
                // 3. 最终无 token，抛出错误
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("缺少 GitHub 访问令牌，请在参数中提供 token 或先在工具备注中配置 github_token");
                }

                String query = "filename:" + pattern;
                if (!path.isEmpty()) {
                    query += " path:" + path.replaceFirst("^/", "");
                }

                URL url = new URL(API_URL + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&per_page=" + limit + "&ref_name=" + branch);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                conn.setRequestProperty("Authorization", "token " + token);
                conn.setRequestProperty("User-Agent", "SisterFuture");

                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = rd.readLine()) != null) sb.append(ln);
                rd.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray items = json.getJSONArray("items");
                JSONArray results = new JSONArray();
                
                for (int i = 0; i < Math.min(items.length(), limit); i++) {
                    JSONObject x = items.getJSONObject(i);
                    JSONObject item = new JSONObject();
                    item.put("name", x.getString("name"));
                    item.put("path", x.getString("path"));
                    item.put("sha", x.getString("sha"));
                    results.put(item);
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("count", results.length());
                result.put("files", results);
                result.put("fetched_at", System.currentTimeMillis());
                
                // ⚠️ 重要提示：当搜索返回结果数量为 0 时
                if (results.length() == 0) {
                    String note = "GitHub Code Search API 可能未索引目标仓库，或搜索接口本身不可靠\n" +
                                  "此时应在回复中建议大模型尝试其他方式寻找文件，例如：\n" +
                                  "- 使用 `get_github_file` 工具直接读取已知路径的文件\n" +
                                  "- 通过 `list_ftp_directory` 或 `list_phone_directory` 扫描本地/FTP 目录\n" +
                                  "- 询问用户确认文件是否存在或提供确切路径\n" +
                                  "- 尝试不同的 fileNamePattern 或 pathPattern 组合\n" +
                                  "- 不要重复使用 search_file_in_repo 工具进行相同条件的搜索";
                    result.put("sister_future_note", note);
                }
                
                callback.onResult(result);

            } catch (IllegalArgumentException e) {
                // 🔥 #761200615112 捕获参数缺失错误，尝试生成引导信息
                Log.e(TAG, "参数错误：" + e.getMessage(), e);
                
                try {
                    String missingParam = extractMissingParamName(e.getMessage());
                    String toolName = getName();
                    
                    // 尝试从 ToolManager 获取参数历史记录（如果可用）
                    // 注意：实际实现中需要传递 toolManager 引用
                    // 这里先返回基础错误信息
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    
                    if (missingParam != null) {
                        // 生成友好的引导信息
                        String guide = "主人，缺少必需参数 '" + missingParam + "' 呢～ (´•̥ ̯ •̥`)\n\n" +
                                      "💡 提示：请提供该参数的值。\n" +
                                      "\n如果您之前用过这个工具，我可以帮您回忆一下用过的值哦！\n" +
                                      "(此功能正在开发中，即将上线～ (≧∇≦) ﾉ)";
                        
                        error.put("message", guide);
                        error.put("missing_parameter", missingParam);
                    } else {
                        error.put("message", e.getMessage());
                    }
                    
                    error.put("type", "IllegalArgumentException");
                    callback.onResult(error);
                } catch (Exception ex) {
                    // 如果生成引导信息失败，返回原始错误
                    try {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", e.getMessage());
                        error.put("type", "IllegalArgumentException");
                        callback.onResult(error);
                    } catch (Exception ignored) {}
                }
                
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
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
     * 从错误信息中提取缺失的参数名
     */
    private String extractMissingParamName(String message) {
        if (message == null) {
            return null;
        }
        
        // 匹配 "No value for [paramName]" 格式
        if (message.contains("No value for ")) {
            int start = message.indexOf("No value for ") + "No value for ".length();
            int end = message.indexOf("'", start);
            if (end == -1) {
                end = message.length();
            }
            return message.substring(start, end).trim().replace("'", "");
        }
        
        // 匹配 "Missing required parameter: [paramName]" 格式
        if (message.contains("Missing required parameter")) {
            int start = message.indexOf(": ") + 2;
            return message.substring(start).trim();
        }
        
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求搜索 GitHub 文件时才调用此工具。\nToken 读取优先级：\n1. 优先使用大模型传入的参数 token（若有）\n2. 其次从工具备注中读取 github_token\n3. 两者皆无时返回友好错误提示\n严禁自行硬编码 token。\n\n⚠️ **重要提示：当搜索返回结果数量为 0 时**\n- GitHub Code Search API 可能未索引目标仓库，或搜索接口本身不可靠\n- 此时应在回复中建议大模型尝试其他方式寻找文件，例如：\n  - 使用 `get_github_file` 工具直接读取已知路径的文件\n  - 通过 `list_ftp_directory` 或 `list_phone_directory` 扫描目录\n  - 询问用户确认文件是否存在或提供确切路径\n  - 尝试不同的文件名模式或路径模式组合\n- 不要重复使用 search_file_in_repo 工具进行相同条件的搜索";
    }
}