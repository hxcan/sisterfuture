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
        return "search_file_in_repo";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject func = new JSONObject();
            func.put("name", "search_file_in_repo");
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
                String owner = args.getString("owner");
                String repo = args.getString("repo");
                String pattern = args.getString("fileNamePattern");
                String path = args.optString("pathPattern", "");
                String branch = args.optString("branch", "master");
                int limit = args.optInt("limit", 10);

                String query = "filename:" + pattern;
                if (!path.isEmpty()) {
                    query += " path:" + path.replaceFirst("^/", "");
                }

                URL url = new URL(API_URL + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&per_page=" + limit + "&ref_name=" + branch);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                String tk = getNote(context);
                String token = "";
                if (!tk.isEmpty()) {
                    JSONObject note = new JSONObject(tk);
                    if (note.has("github_token")) {
                        token = note.getString("github_token");
                    }
                }
                
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
                callback.onResult(result);

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

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求搜索 GitHub 文件时才调用此工具。在调用前，必须优先检查本工具的备注内容，从中提取 github_token 配置。只有当备注中缺少 token 时，才允许使用用户提供的 token 参数作为 fallback。";
    }
}