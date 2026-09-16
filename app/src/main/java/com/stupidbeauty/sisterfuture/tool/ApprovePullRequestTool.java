package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/** Submits an APPROVE review, never a merge request. */
public final class ApprovePullRequestTool implements Tool {
    private final OkHttpClient client;
    private final HttpUrl apiBase;
    private final Executor executor;
    private final Supplier<String> note;

    public ApprovePullRequestTool(Context context) {
        this(new OkHttpClient(), HttpUrl.get("https://api.github.com/"),
                Executors.newSingleThreadExecutor(), null, context);
    }

    ApprovePullRequestTool(OkHttpClient client, HttpUrl apiBase, Executor executor,
                          Supplier<String> note, Context context) {
        // A review is a write: don't replay it on transport failure or redirect it.
        this.client = client.newBuilder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(60, TimeUnit.SECONDS).build();
        this.apiBase = apiBase;
        this.executor = executor;
        this.note = note != null ? note : () -> getNote(context);
    }

    @Override public String getName() { return "approvePullRequest"; }
    @Override public boolean shouldInclude() { return true; }
    @Override public boolean isAsync() { return true; }
    @Override public boolean shouldRecordParameterHistory() { return false; }

    @Override public JSONObject getDefinition() {
        try {
            JSONObject properties = new JSONObject()
                    .put("owner", field("仓库所有者（GitHub 用户名或组织名）"))
                    .put("repo", field("仓库名称"))
                    .put("pull_number", new JSONObject().put("type", "integer")
                            .put("minimum", 1).put("description", "要批准的 Pull Request 编号"))
                    .put("body", field("可选，审核说明"))
                    .put("commit_id", field("可选，已审核的提交 SHA；省略时 GitHub 使用 PR 最新提交"))
                    .put("token", field("GitHub Token；省略时从本工具备注的 github_token 读取。需要 Pull requests 写权限"));
            return new JSONObject().put("type", "function").put("function", new JSONObject()
                    .put("name", getName())
                    .put("description", "批准 GitHub Pull Request，提交 APPROVE 审核（不是合并）。使用 token 所属账号，不能批准自己创建的 PR。")
                    .put("parameters", new JSONObject().put("type", "object")
                            .put("properties", properties)
                            .put("required", new JSONArray(new String[]{"owner", "repo", "pull_number"}))));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static JSONObject field(String description) throws Exception {
        return new JSONObject().put("type", "string").put("description", description);
    }

    @Override public String getDefaultSystemPromptEnhancement() {
        return "仅在用户授权或既定审核流程允许时批准 PR，并先检查改动。"
                + "优先传入实际审核的 commit_id，避免批准未看过的最新改动。批准不代表合并或 CI 通过。"
                + "网络异常时审核可能已经提交，先核实 GitHub 审核状态，不要盲目重复调用。";
    }

    @Override public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        executor.execute(() -> {
            JSONObject result;
            try { result = execute(arguments); }
            catch (Exception e) { callback.onError(e); return; }
            callback.onResult(result);
        });
    }

    @Override public JSONObject execute(JSONObject arguments) throws Exception {
        String owner = segment(arguments.getString("owner"));
        String repo = segment(arguments.getString("repo"));
        Object number = arguments.get("pull_number");
        if (!(number instanceof Number) || ((Number) number).doubleValue() < 1
                || ((Number) number).doubleValue() > Integer.MAX_VALUE
                || ((Number) number).doubleValue() != ((Number) number).intValue())
            throw new IllegalArgumentException("pull_number 必须是正整数");
        int pullNumber = ((Number) number).intValue();
        String token = arguments.optString("token", "").trim();
        if (token.isEmpty()) {
            String saved = note.get();
            if (saved != null && !saved.trim().isEmpty()) {
                try { token = new JSONObject(saved).optString("github_token", "").trim(); }
                catch (Exception invalid) {
                    throw new IllegalArgumentException("本工具备注必须为包含 github_token 的 JSON 对象");
                }
            }
        }
        if (token.isEmpty()) throw new IllegalArgumentException("缺少 token，请传入或在本工具备注配置 github_token");

        JSONObject payload = new JSONObject().put("event", "APPROVE");
        if (arguments.has("body")) payload.put("body", arguments.getString("body"));
        String commit = arguments.optString("commit_id", "").trim();
        if (!commit.isEmpty()) payload.put("commit_id", commit);
        HttpUrl url = apiBase.newBuilder().addPathSegment("repos").addPathSegment(owner)
                .addPathSegment(repo).addPathSegment("pulls").addPathSegment(String.valueOf(pullNumber))
                .addPathSegment("reviews").build();
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("User-Agent", "SisterFuture-ApprovePullRequestTool")
                .post(RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            JSONObject data;
            try { data = new JSONObject(text); }
            catch (Exception invalid) { data = new JSONObject(); }
            JSONObject result = new JSONObject().put("status_code", response.code()).put("pr_number", pullNumber);
            boolean approved = response.code() == 200 && "APPROVED".equals(data.optString("state"));
            result.put("success", approved);
            if (approved) {
                result.put("review_id", data.getLong("id")).put("state", "APPROVED")
                        .put("review_url", data.optString("html_url"))
                        .put("commit_id", data.optString("commit_id"));
            } else {
                result.put("error", data.optString("message", "未确认批准成功，HTTP " + response.code()
                        + "；请检查 GitHub 审核状态").replace(token, "[REDACTED]"));
            }
            return result;
        } catch (IOException e) {
            throw new IOException("GitHub 请求失败，审核可能已提交；请先核实 PR 审核状态，再决定是否重试");
        }
    }

    private static String segment(String value) {
        value = value.trim();
        if (!value.matches("[A-Za-z0-9_.-]+") || value.equals(".") || value.equals(".."))
            throw new IllegalArgumentException("owner/repo 必须是有效仓库路径名称");
        return value;
    }
}
