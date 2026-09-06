package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 可灵 (Kling) 视频生成工具
 *
 * 调用可灵 AI 的"文生视频"API，生成短视频片段。
 * 异步任务模式：提交 → 轮询 → 下载。
 *
 * API 文档: https://klingai.com/document-api/api/video/3-0-turbo/text-to-video
 *
 * 主要特性:
 * - 支持 kling-3.0-turbo 模型
 * - 支持 720p / 1080p 两种分辨率
 * - 支持 16:9 / 9:16 / 1:1 三种比例
 * - 支持 3-15 秒时长
 * - 支持多镜头 Prompt: "镜头 n, m, words; 镜头 n, m, words;"
 *
 * 使用方式:
 * 1. 在工具备注中设置 kling_api_key=xxx
 * 2. 调用工具时只需传 prompt 等参数
 *
 * @author 未来姐姐
 * @date 2026-08-11
 */
public class KlingVideoGenerationTool implements Tool {
    private static final String TAG = "KlingVideoGenTool";

    private static final String API_BASE_URL = "https://api-beijing.klingai.com";
    private static final String SUBMIT_ENDPOINT = API_BASE_URL + "/text-to-video/kling-3.0-turbo";
    private static final String QUERY_ENDPOINT = API_BASE_URL + "/tasks";

    private static final String NOTE_KEY_API_KEY = "kling_api_key";

    // 默认参数
    private static final String DEFAULT_DURATION = "5";
    private static final String DEFAULT_RESOLUTION = "720p";
    private static final String DEFAULT_ASPECT_RATIO = "16:9";
    private static final int DEFAULT_POLL_INTERVAL_MS = 5000;     // 5 秒轮询一次
    private static final int DEFAULT_MAX_WAIT_MS = 600000;        // 最长等 10 分钟

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static class ClientHolder {
        private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private static OkHttpClient getClient() {
        return ClientHolder.INSTANCE;
    }

    public KlingVideoGenerationTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "klingVideoGenerate";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "klingVideoGenerate");
            functionDef.put("description", "调用可灵 AI (kling-3.0-turbo) 文生视频接口生成短视频片段。支持 720p/1080p 分辨率、3-15 秒时长、16:9/9:16/1:1 三种比例。异步任务，自动轮询到完成并下载视频到 /sdcard/Download/。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject promptParam = new JSONObject();
            promptParam.put("type", "string");
            promptParam.put("description", "文本提示词，描述想生成的视频内容（最长 3072 字符）。也支持多镜头格式：'镜头 1, 3, 描述1; 镜头 2, 3, 描述2;' （每个分镜时长≥1，所有分镜时长之和等于总时长）");
            properties.put("prompt", promptParam);

            JSONObject apiKeyParam = new JSONObject();
            apiKeyParam.put("type", "string");
            apiKeyParam.put("description", "可灵 API Key。如果未传入，会自动从工具备注 kling_api_key 读取");
            properties.put("apiKey", apiKeyParam);

            JSONObject durationParam = new JSONObject();
            durationParam.put("type", "integer");
            durationParam.put("default", 5);
            durationParam.put("description", "生成视频时长（秒），范围 3-15，默认 5");
            properties.put("duration", durationParam);

            JSONObject resolutionParam = new JSONObject();
            resolutionParam.put("type", "string");
            resolutionParam.put("default", "720p");
            resolutionParam.put("enum", new JSONArray().put("720p").put("1080p"));
            resolutionParam.put("description", "视频清晰度，默认 720p");
            properties.put("resolution", resolutionParam);

            JSONObject aspectRatioParam = new JSONObject();
            aspectRatioParam.put("type", "string");
            aspectRatioParam.put("default", "16:9");
            aspectRatioParam.put("enum", new JSONArray().put("16:9").put("9:16").put("1:1"));
            aspectRatioParam.put("description", "画面纵横比，默认 16:9（横屏）；短剧抖音竖屏选 9:16");
            properties.put("aspectRatio", aspectRatioParam);

            JSONObject watermarkParam = new JSONObject();
            watermarkParam.put("type", "boolean");
            watermarkParam.put("default", false);
            watermarkParam.put("description", "是否同时生成含水印版本，默认 false");
            properties.put("watermark", watermarkParam);

            JSONObject saveDirParam = new JSONObject();
            saveDirParam.put("type", "string");
            saveDirParam.put("description", "视频保存目录（默认 /sdcard/Download/）");
            properties.put("saveDir", saveDirParam);

            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
            required.put("prompt");
            parameters.put("required", required);
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
        long totalStartTime = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                FileLogger.i(TAG, "========== klingVideoGenerate 工具开始执行 ==========");

                // 1. 解析参数
                long stepStart = System.currentTimeMillis();
                String prompt = arguments.optString("prompt", null);
                if (prompt == null || prompt.trim().isEmpty()) {
                    throw new IllegalArgumentException("prompt 不能为空");
                }
                FileLogger.d(TAG, "[1/8] 解析参数完成 - prompt 长度: " + prompt.length());

                // 2. 获取 API Key
                stepStart = System.currentTimeMillis();
                String apiKey = arguments.optString("apiKey", null);
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = getApiKeyFromNote();
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                            "API Key 未配置。请通过以下方式之一提供：\n"
                            + "1. 调用时传入 apiKey 参数\n"
                            + "2. 在工具备注中设置 " + NOTE_KEY_API_KEY + "=xxx"
                        );
                    }
                }
                FileLogger.i(TAG, "[2/8] 获取 API Key 完成（掩码: " + maskApiKey(apiKey) + "）");

                int duration = arguments.optInt("duration", Integer.parseInt(DEFAULT_DURATION));
                if (duration < 3 || duration > 15) {
                    throw new IllegalArgumentException("duration 必须在 3-15 之间，当前: " + duration);
                }
                String resolution = arguments.optString("resolution", DEFAULT_RESOLUTION);
                String aspectRatio = arguments.optString("aspectRatio", DEFAULT_ASPECT_RATIO);
                boolean watermark = arguments.optBoolean("watermark", false);
                String saveDir = arguments.optString("saveDir", null);

                FileLogger.i(TAG, "[3/8] 参数 - duration: " + duration + "s, resolution: " + resolution + ", aspect: " + aspectRatio + ", watermark: " + watermark);

                // 3. 提交任务
                stepStart = System.currentTimeMillis();
                String taskId = submitTask(apiKey, prompt, duration, resolution, aspectRatio, watermark);
                FileLogger.i(TAG, "[4/8] 任务已提交 - task_id: " + taskId + "，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                // 4. 轮询等待
                stepStart = System.currentTimeMillis();
                String videoUrl = pollUntilDone(apiKey, taskId);
                FileLogger.i(TAG, "[5/8] 任务已完成 - 视频URL已获取，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                // 5. 下载视频
                stepStart = System.currentTimeMillis();
                File targetDir;
                if (saveDir != null && !saveDir.trim().isEmpty()) {
                    targetDir = new File(saveDir);
                } else {
                    String defaultDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                    targetDir = new File(defaultDir);
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                long timestamp = System.currentTimeMillis();
                String savedPath = downloadVideo(videoUrl, targetDir, timestamp);
                FileLogger.i(TAG, "[6/8] 视频下载完成 - " + savedPath + "，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                // 6. 扫描到相册
                scanVideoToGallery(savedPath);

                // 7. 构建结果
                long totalDurationMs = System.currentTimeMillis() - totalStartTime;
                FileLogger.i(TAG, "✅ 视频生成成功，总耗时: " + totalDurationMs + "ms");

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("task_id", taskId);
                result.put("video_url", videoUrl);
                result.put("saved_path", savedPath);
                result.put("duration", duration);
                result.put("resolution", resolution);
                result.put("aspect_ratio", aspectRatio);
                result.put("total_duration_ms", totalDurationMs);
                result.put("timestamp", timestamp);
                // 🆕 修复 #883422015337：attachment → attachments（JSONArray），与 SisterFutureActivity.parseAttachments 期望一致
                JSONObject videoAtt = buildVideoAttachment(savedPath, duration);
                result.put("attachments", new JSONArray().put(videoAtt));

                callback.onResult(result);

            } catch (Exception e) {
                long totalDurationMs = System.currentTimeMillis() - totalStartTime;
                FileLogger.e(TAG, "❌ executeAsync 出错 - 总耗时: " + totalDurationMs + "ms", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 提交视频生成任务
     */
    private String submitTask(String apiKey, String prompt, int duration, String resolution,
                               String aspectRatio, boolean watermark) throws IOException {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("prompt", prompt);

            JSONObject options = new JSONObject();
            JSONObject watermarkInfo = new JSONObject();
            watermarkInfo.put("enabled", watermark);
            options.put("watermark_info", watermarkInfo);
            options.put("external_task_id", "");
            requestBody.put("options", options);

            JSONObject settings = new JSONObject();
            settings.put("duration", duration);
            settings.put("resolution", resolution);
            settings.put("aspect_ratio", aspectRatio);
            requestBody.put("settings", settings);

            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, requestBody.toString());

            Request request = new Request.Builder()
                    .url(SUBMIT_ENDPOINT)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            FileLogger.d(TAG, "  [submit] 请求体: " + requestBody.toString());

            try (Response response = getClient().newCall(request).execute()) {
                int code = response.code();
                ResponseBody respBody = response.body();
                String responseStr = respBody != null ? respBody.string() : "";

                FileLogger.d(TAG, "  [submit] 响应: HTTP " + code + " - " + responseStr);

                if (code < 200 || code >= 300) {
                    throw new IOException("提交任务失败 HTTP " + code + ": " + responseStr);
                }

                JSONObject jsonResponse = new JSONObject(responseStr);
                int errCode = jsonResponse.optInt("code", -1);
                if (errCode != 0) {
                    String errMsg = jsonResponse.optString("message", "未知错误");
                    throw new IOException("可灵 API 错误 [" + errCode + "]: " + errMsg);
                }

                JSONObject data = jsonResponse.getJSONObject("data");
                String taskId = data.getString("id");
                FileLogger.i(TAG, "  [submit] 任务ID: " + taskId);
                return taskId;
            }
        } catch (org.json.JSONException e) {
            throw new IOException("提交任务 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询任务直到完成
     */
    private String pollUntilDone(String apiKey, String taskId) throws IOException {
        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (true) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > DEFAULT_MAX_WAIT_MS) {
                throw new IOException("轮询超时（> " + (DEFAULT_MAX_WAIT_MS / 60000) + " 分钟）");
            }

            attempt++;
            FileLogger.d(TAG, "  [poll] 第 " + attempt + " 次轮询，已等待 " + (elapsed / 1000) + "s");

            try {
                Thread.sleep(DEFAULT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("轮询被中断", e);
            }

            String videoUrl = queryTask(apiKey, taskId);
            if (videoUrl != null) {
                return videoUrl;
            }
        }
    }

    /**
     * 查询任务状态。返回视频URL表示完成，返回null表示还在进行中，抛异常表示失败。
     */
    private String queryTask(String apiKey, String taskId) throws IOException {
        try {
            // 注意：可灵查询任务是 POST 方法，task_ids 在 body 里
            JSONObject requestBody = new JSONObject();
            requestBody.put("task_ids", taskId);

            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, requestBody.toString());

            Request request = new Request.Builder()
                    .url(QUERY_ENDPOINT)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                int code = response.code();
                ResponseBody respBody = response.body();
                String responseStr = respBody != null ? respBody.string() : "";

                FileLogger.d(TAG, "  [poll] 原始响应: HTTP " + code + " - " + responseStr);

                if (code < 200 || code >= 300) {
                    throw new IOException("查询任务失败 HTTP " + code + ": " + responseStr);
                }

                JSONObject jsonResponse = new JSONObject(responseStr);
                int errCode = jsonResponse.optInt("code", -1);
                if (errCode != 0) {
                    String errMsg = jsonResponse.optString("message", "未知错误");
                    throw new IOException("可灵 API 错误 [" + errCode + "]: " + errMsg);
                }

                JSONObject data = jsonResponse.getJSONObject("data");
                // 修复：可灵查询接口真实响应中任务列表在 data.result（数组），不是 data.task_list
                JSONArray taskList = data.optJSONArray("result");
                if (taskList == null) {
                    // 兼容：少数情况下也可能在 task_list
                    taskList = data.optJSONArray("task_list");
                }
                if (taskList == null || taskList.length() == 0) {
                    FileLogger.w(TAG, "  [poll] 任务列表为空");
                    return null;
                }

                JSONObject task = taskList.getJSONObject(0);
                String status = task.optString("status", "unknown");
                FileLogger.d(TAG, "  [poll] 任务状态: " + status);

                switch (status) {
                    case "succeeded":
                        // 修复：可灵查询接口真实响应中视频数组在 outputs（不是 task_result.videos）
                        JSONArray videos = task.optJSONArray("outputs");
                        if (videos == null || videos.length() == 0) {
                            // 兼容：旧版本可能在 task_result.videos
                            JSONObject taskResult = task.optJSONObject("task_result");
                            if (taskResult != null) {
                                videos = taskResult.optJSONArray("videos");
                            }
                        }
                        if (videos == null || videos.length() == 0) {
                            throw new IOException("任务成功但无视频结果（outputs 和 task_result.videos 都为空）");
                        }
                        String url = videos.getJSONObject(0).optString("url", null);
                        if (url == null || url.isEmpty()) {
                            throw new IOException("视频URL为空");
                        }
                        return url;

                    case "failed":
                        String failMsg = task.optString("message", "未知失败原因");
                        throw new IOException("视频生成失败: " + failMsg);

                    case "submitted":
                    case "processing":
                    default:
                        // 还在进行中
                        return null;
                }
            }
        } catch (org.json.JSONException e) {
            throw new IOException("查询任务 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载视频文件
     */
    private String downloadVideo(String videoUrl, File targetDir, long timestamp) throws IOException {
        String filename = String.format("kling_video_%d.mp4", timestamp);
        File targetFile = new File(targetDir, filename);

        long startTime = System.currentTimeMillis();
        FileLogger.d(TAG, "  [download] 下载视频: " + videoUrl);

        Request request = new Request.Builder().url(videoUrl).build();

        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载视频失败: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }

            try (InputStream in = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                FileLogger.i(TAG, "  [download] 下载完成: " + totalBytes + " 字节，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            }
        }

        return targetFile.getAbsolutePath();
    }

    /**
     * 扫描视频到系统相册
     */
    private void scanVideoToGallery(final String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try {
            MediaScannerConnection.scanFile(
                context,
                new String[]{filePath},
                new String[]{"video/mp4"},
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        if (uri != null) {
                            FileLogger.i(TAG, "  [scan] ✅ 扫描成功: " + uri);
                        }
                    }
                }
            );
        } catch (Exception e) {
            FileLogger.e(TAG, "  [scan] 扫描失败", e);
            try {
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                context.sendBroadcast(intent);
            } catch (Exception ex) {
                FileLogger.e(TAG, "  [scan] 广播扫描也失败", ex);
            }
        }
    }

    /**
     * 构建视频附件对象
     */
    private JSONObject buildVideoAttachment(String savedPath, int duration) {
        try {
            JSONObject attachment = new JSONObject();
            attachment.put("type", "video");
            attachment.put("url", "file://" + savedPath);

            JSONObject metadata = new JSONObject();
            metadata.put("duration", duration);

            File file = new File(savedPath);
            if (file.exists()) {
                metadata.put("size", file.length());
            }
            metadata.put("mimeType", "video/mp4");

            attachment.put("metadata", metadata);
            return attachment;
        } catch (Exception e) {
            FileLogger.e(TAG, "  [attachment] 构建失败", e);
            return new JSONObject();
        }
    }

    /**
     * 掩码 API Key 用于日志
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 从工具备注读取 API Key
     */
    private String getApiKeyFromNote() {
        String note = getNote(context);
        if (note == null || note.isEmpty()) {
            return null;
        }

        String[] lines = note.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(NOTE_KEY_API_KEY + "=")) {
                String value = line.substring((NOTE_KEY_API_KEY + "=").length()).trim();
                if (!value.isEmpty()) {
                    FileLogger.i(TAG, "从工具备注读取到 API Key");
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "调用 klingVideoGenerate 时：\n"
            + "1. 必传参数：prompt（视频描述，支持多镜头格式）\n"
            + "2. 可选参数：duration(3-15秒)、resolution(720p/1080p)、aspectRatio(16:9/9:16/1:1)、watermark\n"
            + "3. API Key：运行时传入，或在工具备注中设置 kling_api_key=xxx\n"
            + "4. 典型场景：AI短剧片段生成、动态素材、产品演示\n"
            + "5. 中文 prompt 友好，可灵对中文理解优秀\n"
            + "6. 视频生成通常 30 秒-3 分钟，工具会自动轮询到完成\n"
            + "7. 完成后视频自动下载到 /sdcard/Download/ 并扫描到相册\n"
            + "8. 注意：可灵生成的视频 30 天后失效，需要及时转存";
    }
}