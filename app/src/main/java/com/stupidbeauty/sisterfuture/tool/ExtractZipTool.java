package com.stupidbeauty.sisterfuture.tool;

import android.os.Environment;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class ExtractZipTool implements Tool {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "extract-zip");
        thread.setDaemon(true);
        return thread;
    });
    @Override public String getName() { return "extractZip"; }
    @Override public boolean shouldInclude() { return true; }
    @Override public boolean isAsync() { return true; }
    @Override public String getDefaultSystemPromptEnhancement() {
        return "网络 ZIP 先下载到本地，再将真实本地路径传给 extractZip。解压完成后可用 listPhoneDirectory 查看内容。"
            + "不覆盖已有目录，不修改源 ZIP；不支持密码 ZIP。";
    }
    @Override public JSONObject getDefinition() {
        try {
            return new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", getName()).put("description", "后台解压本地 ZIP 到新目录，保留原文件，不覆盖已有目录。不支持加密 ZIP；最多10000条目、解压总量2GiB。")
                .put("parameters", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()
                        .put("zipPath", new JSONObject().put("type", "string").put("description", "应用可读的 ZIP 文件绝对路径，不支持 URL/content URI。"))
                        .put("outputDirectory", new JSONObject().put("type", "string").put("description", "可选，新输出目录绝对路径，父目录须存在。默认在 Download 下创建唯一目录。")))
                    .put("required", new JSONArray().put("zipPath"))));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static JSONObject parseArguments(JSONObject arguments) throws Exception {
        JSONObject args = ToolParameterAliases.normalize(arguments == null ? new JSONObject() : arguments,
            "zipPath", "outputDirectory");
        // Wrongly structured values are treated as absent, not serialized into filesystem paths.
        for (String key : new String[]{"zipPath", "outputDirectory"}) {
            if (!(args.opt(key) instanceof String)) args.remove(key);
        }
        if (args.optString("zipPath", "").trim().isEmpty()) throw new IllegalArgumentException("缺少 zipPath");
        return args;
    }
    @Override public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        WORKER.execute(() -> {
            JSONObject result;
            long started = System.nanoTime();
            try {
                JSONObject args = parseArguments(arguments);
                String path = args.optString("outputDirectory", "");
                File output;
                if (path.trim().isEmpty()) {
                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloads.isDirectory() && !downloads.mkdirs()) throw new java.io.IOException("无法创建 Download 目录");
                    output = new File(downloads, "extracted_" + UUID.randomUUID());
                } else output = new File(path);
                ZipExtractor.Result extracted = ZipExtractor.extract(new File(args.getString("zipPath")), output);
                result = new JSONObject().put("status", "success").put("outputDirectory", output.getAbsolutePath())
                    .put("filesCount", extracted.files).put("extractedBytes", extracted.bytes);
            } catch (Exception e) {
                result = new JSONObject();
                try { result.put("status", "error").put("message", e.getMessage()).put("type", e.getClass().getSimpleName()); }
                catch (Exception jsonError) { callback.onError(jsonError); return; }
            }
            try { result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000); }
            catch (Exception e) { callback.onError(e); return; }
            callback.onResult(result);
        });
    }
}
