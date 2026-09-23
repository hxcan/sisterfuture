package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Upload one local file, then attach it to an existing issue. */
public class UploadRedmineAttachmentTool implements Tool {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Mutations must not be automatically replayed or redirected to another host.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build();

    public UploadRedmineAttachmentTool(Context context) { this.context = context; }
    @Override public String getName() { return "uploadRedmineAttachment"; }
    @Override public boolean shouldInclude() { return true; }
    @Override public boolean isAsync() { return true; }

    @Override public JSONObject getDefinition() {
        try {
            JSONObject properties = new JSONObject();
            String[][] fields = {
                {"redmineUrl", "Redmine 实例完整地址，也可配置在此工具备注中"},
                {"apiKey", "Redmine API Key；或使用 username/password；可配置在备注中"},
                {"username", "可选：登录用户名"}, {"password", "可选：登录密码"},
                {"filePath", "手机上应用可读取的本地文件绝对路径，不支持 content:// URI"},
                {"fileName", "可选：附件显示文件名，默认使用本地文件名"},
                {"contentType", "可选：附件 MIME 类型，默认按文件名推断"},
                {"description", "可选：附件说明，不修改任务描述"}
            };
            for (String[] field : fields) properties.put(field[0], new JSONObject()
                    .put("type", "string").put("description", field[1]));
            properties.put("taskId", new JSONObject().put("type", "integer")
                    .put("minimum", 1).put("description", "要添加附件的现有 Redmine 任务编号"));
            return new JSONObject().put("type", "function").put("function", new JSONObject()
                    .put("name", getName()).put("description", "将指定手机本地文件上传到 Redmine，并作为附件添加到指定任务；异步执行，保留已有附件和其他任务字段。")
                    .put("parameters", new JSONObject().put("type", "object")
                            .put("properties", properties)
                            .put("required", new JSONArray(new String[]{"taskId", "filePath"}))));
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }

    @Override public String getDefaultSystemPromptEnhancement() {
        return "仅上传用户明确指定并授权分享的文件到指定 Redmine 实例和任务；目的地或文件不明确时先询问。"
                + "使用 taskId、filePath、redmineUrl、apiKey、fileName、contentType，小驼峰优先，兼容下划线别名。"
                + "认证支持此工具备注中的 apiKey 或 username/password。文件必须已存在且应用可读，不会自动申请存储权限。"
                + "仅 status=success 表示已关联任务。上传或关联失败/结果不确定时，先查任务附件，不要自动重传，以免重复。"
                + "不得在回复或日志中暴露认证信息或上传令牌。";
    }

    @Override public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try { callback.onResult(upload(arguments, getNote(context))); }
            catch (Exception e) { callback.onError(e); }
        });
    }

    private static String text(JSONObject args, String key, String fallback) {
        Object value = args.opt(key);
        return value instanceof String && !((String) value).trim().isEmpty()
                ? (String) value : fallback;
    }

    JSONObject upload(JSONObject supplied, String note) throws Exception {
        JSONObject args = ToolParameterAliases.normalize(supplied,
                "taskId", "filePath", "fileName", "contentType", "redmineUrl", "apiKey");
        long taskId;
        try {
            Object raw = args.opt("taskId");
            if (!(raw instanceof String || raw instanceof Number)) throw new IllegalArgumentException();
            taskId = new BigDecimal(raw.toString().trim()).longValueExact();
            if (taskId <= 0) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new IllegalArgumentException("taskId 必须是正整数任务编号"); }
        String path = text(args, "filePath", "");
        File file = new File(path);
        if (path.isEmpty() || !file.isAbsolute() || !file.isFile() || !file.canRead())
            throw new IllegalArgumentException("filePath 必须是应用可读取的本地文件绝对路径");
        String filename = text(args, "fileName", file.getName());
        if (filename.contains("/") || filename.contains("\\") || filename.contains("\r") || filename.contains("\n"))
            throw new IllegalArgumentException("fileName 必须是文件名，不能包含目录或换行");
        String mime = text(args, "contentType", URLConnection.guessContentTypeFromName(filename));
        if (mime == null) mime = "application/octet-stream";
        for (String key : new String[]{"redmineUrl", "apiKey", "redmine_api_key", "username", "password"})
            if (args.has(key) && !(args.opt(key) instanceof String)) args.remove(key);
        RedmineAuth auth = RedmineAuth.resolve(args, note);
        HttpUrl base = HttpUrl.parse(auth.getRedmineUrl());
        if (base == null || base.query() != null || base.fragment() != null
                || !base.username().isEmpty() || !base.password().isEmpty())
            throw new IllegalArgumentException("redmineUrl 必须是无凭据、查询参数和片段的 HTTP(S) 实例地址");
        HttpUrl uploadUrl = base.newBuilder().addPathSegment("uploads.json")
                .addQueryParameter("filename", filename).build();
        HttpUrl issueUrl = base.newBuilder().addPathSegment("issues")
                .addPathSegment(taskId + ".json").build();
        String stage = "upload";
        boolean uploaded = false;
        try {
            String token;
            Request request = auth.apply(new Request.Builder().url(uploadUrl))
                    .post(RequestBody.create(MediaType.get("application/octet-stream"), file)).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 201) throw new IOException("HTTP " + response.code());
                uploaded = true;
                if (response.body() == null) throw new IOException("上传响应为空");
                JSONObject upload = new JSONObject(response.body().string()).getJSONObject("upload");
                token = text(upload, "token", "");
                if (token.isEmpty()) throw new IOException("上传响应缺少令牌");
            }
            stage = "attach";
            JSONObject attachment = new JSONObject().put("token", token).put("filename", filename)
                    .put("content_type", mime).put("description", text(args, "description", ""));
            JSONObject body = new JSONObject().put("issue", new JSONObject()
                    .put("uploads", new JSONArray().put(attachment)));
            Request attach = auth.apply(new Request.Builder().url(issueUrl))
                    .put(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"))).build();
            try (Response response = client.newCall(attach).execute()) {
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            }
            return new JSONObject().put("status", "success").put("taskId", taskId)
                    .put("fileName", filename).put("sizeBytes", file.length())
                    .put("message", "文件已上传并添加到指定任务附件");
        } catch (Exception e) {
            // Do not include response bodies, exception details or the upload token.
            String detail = e instanceof IOException && e.getMessage() != null
                    && e.getMessage().matches("HTTP [0-9]{3}") ? "（" + e.getMessage() + "）" : "";
            return new JSONObject().put("status", "error").put("stage", stage)
                    .put("taskId", taskId).put("uploaded", uploaded)
                    .put("message", (uploaded ? "文件已上传，但未确认关联到任务" : "未确认上传成功")
                            + detail + "；请检查任务附件后再决定是否重试，避免重复上传。");
        }
    }
}
