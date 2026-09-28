package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Merge local PDFs without rasterizing pages. Sources are never modified. */
public class MergePdfTool implements Tool {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "merge-pdf");
        thread.setDaemon(true);
        return thread;
    });
    private final Context context;
    public MergePdfTool(Context context) { this.context = context.getApplicationContext(); }
    @Override public String getName() { return "mergePdf"; }
    @Override public boolean shouldInclude() { return true; }
    @Override public boolean isAsync() { return true; }
    @Override public String getDefaultSystemPromptEnhancement() {
        return "长 PDF 可先用 renderPdf 逐页或分批生成，等待每次成功后收集 pdfPath，"
            + "再按最终页序传给 mergePdf 的 filePaths。合并不修改源文件，不需电脑或联网。"
            + "只使用已确认存在的文件路径；输出同名时换一个文件名，不删除原文件。";
    }

    @Override public JSONObject getDefinition() {
        try {
            return new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", getName())
                .put("description", "按 filePaths 数组顺序合并手机本地 PDF 的全部页面。适合先用 renderPdf 逐页或分批生成，再合并。后台执行，完成后返回 pdfPath。保留源文件，不覆盖已有文件；不支持加密 PDF。")
                .put("parameters", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()
                        .put("filePaths", new JSONObject().put("type", "array").put("minItems", 2)
                            .put("items", new JSONObject().put("type", "string"))
                            .put("description", "至少两个应用可读的本地 PDF 绝对路径，按最终页序排列；可直接使用 renderPdf 返回的 pdfPath，不支持 URL 或 content URI。"))
                        .put("outputFilename", new JSONObject().put("type", "string")
                            .put("description", "可选输出文件名，不含目录，保存到 Download；默认生成唯一文件名。")))
                    .put("required", new JSONArray().put("filePaths"))));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        WORKER.execute(() -> {
            JSONObject result;
            try {
                long started = System.nanoTime();
                JSONObject args = ToolParameterAliases.normalize(
                    arguments == null ? new JSONObject() : arguments, "filePaths", "outputFilename");
                List<File> inputs = parseInputs(args);
                String name = outputName(args);
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new IOException("无法创建 Download 目录");
                PDFBoxResourceLoader.init(context);
                File output = new File(directory, name);
                int pages = mergeFiles(inputs, output, context.getCacheDir());
                result = new JSONObject().put("status", "success").put("pdfPath", output.getAbsolutePath())
                    .put("filesCount", inputs.size()).put("pagesCount", pages)
                    .put("fileSizeKb", output.length() / 1024)
                    .put("generationTimeSec", (System.nanoTime() - started) / 1_000_000_000.0);
                try {
                    MediaScannerConnection.scanFile(context, new String[]{output.getAbsolutePath()},
                        new String[]{"application/pdf"}, null);
                } catch (Exception ignored) { /* PDF is already complete; indexing is best effort. */ }
            } catch (Exception e) {
                try { result = new JSONObject().put("status", "failed")
                    .put("error", e.getClass().getSimpleName() + ": " + e.getMessage()); }
                catch (Exception jsonError) { callback.onError(jsonError); return; }
            }
            callback.onResult(result);
        });
    }

    static List<File> parseInputs(JSONObject args) throws Exception {
        Object raw = args.opt("filePaths");
        if (raw instanceof String) raw = new JSONArray((String) raw);
        if (!(raw instanceof JSONArray) || ((JSONArray) raw).length() < 2)
            throw new IllegalArgumentException("filePaths 必须包含至少两个 PDF 路径");
        JSONArray paths = (JSONArray) raw;
        List<File> files = new ArrayList<>();
        for (int i = 0; i < paths.length(); i++) {
            if (!(paths.get(i) instanceof String)) throw new IllegalArgumentException("filePaths 元素必须是路径字符串");
            File file = new File(paths.getString(i));
            if (!file.isAbsolute() || !file.isFile() || !file.canRead())
                throw new IllegalArgumentException("第 " + (i + 1) + " 个文件必须是应用可读的本地绝对路径");
            files.add(file.getCanonicalFile());
        }
        return files;
    }

    static String outputName(JSONObject args) {
        String name = args.optString("outputFilename", "").trim();
        if (name.isEmpty()) name = "merged_" + UUID.randomUUID() + ".pdf";
        if (name.contains("/") || name.contains("\\") || name.indexOf(0) >= 0 || name.equals(".") || name.equals(".."))
            throw new IllegalArgumentException("outputFilename 只能是文件名，不能包含目录");
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }

    static int mergeFiles(List<File> inputs, File output, File cacheDir) throws IOException {
        if (output.exists()) throw new IOException("输出文件已存在，请换一个 outputFilename");
        File temporary = File.createTempFile(".merge-pdf-", ".tmp", output.getParentFile());
        boolean reserved = false;
        try {
            int pages;
            try (PDDocument destination = new PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(cacheDir))) {
                PDFMergerUtility merger = new PDFMergerUtility();
                for (File input : inputs) {
                    try (PDDocument source = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly().setTempDir(cacheDir))) {
                        if (source.isEncrypted()) throw new IOException("不支持加密 PDF");
                        merger.appendDocument(destination, source);
                    }
                }
                pages = destination.getNumberOfPages();
                if (pages == 0) throw new IOException("输入 PDF 没有可合并的页面");
                destination.save(temporary);
            }
            // Reserve only after a complete merge, and never overwrite a pre-existing output.
            if (!output.createNewFile()) throw new IOException("输出文件已存在，请换一个 outputFilename");
            reserved = true;
            if (!temporary.renameTo(output)) throw new IOException("无法发布合并后的 PDF");
            reserved = false;
            return pages;
        } finally {
            if (reserved) output.delete(); // Only the empty reservation created by this invocation.
            temporary.delete(); // Only our temporary file; never delete input PDFs.
        }
    }
}
