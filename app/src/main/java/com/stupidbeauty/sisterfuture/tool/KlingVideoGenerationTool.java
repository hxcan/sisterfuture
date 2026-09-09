package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.manager.OssManager;
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
 * 调用可灵 AI 的文生视频或图生视频 API，生成短视频片段。
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
    private static final String TEXT_SUBMIT_ENDPOINT = API_BASE_URL + "/text-to-video/kling-3.0-turbo";
    private static final String IMAGE_SUBMIT_ENDPOINT = API_BASE_URL + "/image-to-video/kling-3.0-turbo";
    private static final String QUERY_ENDPOINT = API_BASE_URL + "/tasks";

    private static final String NOTE_KEY_API_KEY = "kling_api_key";

    // 默认参数
    private static final String DEFAULT_DURATION = "5";
    private static final String DEFAULT_RESOLUTION = "720p";
    private static final String DEFAULT_ASPECT_RATIO = "16:9";
    private static final int DEFAULT_POLL_INTERVAL_MS = 5000;     // 5 秒轮询一次
    private static final int DEFAULT_MAX_WAIT_MS = 600000;        // 最长等 10 分钟
    private static final int MAX_IMAGE_TO_VIDEO_PROMPT_LENGTH = 2500;
    private static final long MAX_REFERENCE_IMAGE_BYTES = 50L * 1024L * 1024L;

    private final Context context;
    private final OssManager ossManager;
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
        this.ossManager = new OssManager(context);
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
            functionDef.put("description", "调用可灵 AI (kling-3.0-turbo) 生成短视频片段。可选传入一张首帧参考图进行图生视频；不传图片时保持文生视频。支持 720p/1080p 分辨率和 3-15 秒时长。异步任务，自动轮询到完成并下载视频到 /sdcard/Download/。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject promptParam = new JSONObject();
            promptParam.put("type", "string");
            promptParam.put("description", "文本提示词，描述想生成的视频内容（文生视频最长 3072 字符，图生视频最长 2500 字符）。也支持多镜头格式：'镜头 1, 3, 描述1; 镜头 2, 3, 描述2;' （每个分镜时长≥1，所有分镜时长之和等于总时长）");
            properties.put("prompt", promptParam);

            JSONObject referenceImageParam = new JSONObject();
            referenceImageParam.put("type", "string");
            referenceImageParam.put("description", "可选的首帧参考图，可传入聊天上下文中的图片本地绝对路径或 http(s) 公网 URL。本地图片会自动上传到已配置的 OSS；省略时使用文生视频。");
            properties.put("referenceImage", referenceImageParam);

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
            aspectRatioParam.put("description", "文生视频的画面纵横比，默认 16:9（横屏）；短剧抖音竖屏选 9:16。图生视频由首帧图片决定，此参数不生效");
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
                String referenceImage = arguments.optString("referenceImage", null);

                FileLogger.i(TAG, "[3/8] 参数 - duration: " + duration + "s, resolution: " + resolution + ", aspect: " + aspectRatio + ", watermark: " + watermark);

                // 可灵服务无法访问应用私有路径。本地参考图先经共享 OSS 管理器上传，
                // 再把短期签名 URL 交给图生视频接口。
                String referenceImageUrl = null;
                if (referenceImage != null && !referenceImage.trim().isEmpty()) {
                    if (prompt.length() > MAX_IMAGE_TO_VIDEO_PROMPT_LENGTH) {
                        throw new IllegalArgumentException("图生视频 prompt 最长 2500 字符，当前: "
                            + prompt.length());
                    }
                    referenceImageUrl = resolveReferenceImageUrl(referenceImage.trim());
                    FileLogger.i(TAG, "参考图已准备完成，将使用图生视频模式");
                }

                // 3. 提交任务
                stepStart = System.currentTimeMillis();
                String taskId = submitTask(apiKey, prompt, duration, resolution, aspectRatio,
                    watermark, referenceImageUrl);
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
                if (referenceImageUrl == null) {
                    result.put("aspect_ratio", aspectRatio);
                }
                result.put("generation_mode", referenceImageUrl == null
                    ? "text_to_video" : "image_to_video");
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
                               String aspectRatio, boolean watermark,
                               String referenceImageUrl) throws IOException {
        try {
            JSONObject requestBody = new JSONObject();
            boolean imageToVideo = referenceImageUrl != null && !referenceImageUrl.isEmpty();
            if (imageToVideo) {
                JSONArray contents = new JSONArray();
                contents.put(new JSONObject().put("type", "prompt").put("text", prompt));
                contents.put(new JSONObject().put("type", "first_frame").put("url", referenceImageUrl));
                requestBody.put("contents", contents);
            } else {
                requestBody.put("prompt", prompt);
            }

            JSONObject options = new JSONObject();
            JSONObject watermarkInfo = new JSONObject();
            watermarkInfo.put("enabled", watermark);
            options.put("watermark_info", watermarkInfo);
            options.put("external_task_id", "");
            requestBody.put("options", options);

            JSONObject settings = new JSONObject();
            settings.put("duration", duration);
            settings.put("resolution", resolution);
            // 图生视频的画幅由首帧决定，避免默认 16:9 与竖图冲突。
            if (!imageToVideo) {
                settings.put("aspect_ratio", aspectRatio);
            }
            requestBody.put("settings", settings);

            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, requestBody.toString());

            String endpoint = imageToVideo ? IMAGE_SUBMIT_ENDPOINT : TEXT_SUBMIT_ENDPOINT;
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            // 图生视频请求体含 OSS 签名 URL，不可完整写入日志。
            FileLogger.d(TAG, "  [submit] 模式: "
                + (imageToVideo ? "image_to_video" : "text_to_video")
                + ", duration: " + duration + ", resolution: " + resolution);

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
     * 将本地参考图转换为可灵可访问的短期签名 URL；公网 URL 可直接使用。
     */
    private String resolveReferenceImageUrl(String referenceImage) throws Exception {
        if (referenceImage.regionMatches(true, 0, "https://", 0, 8)
            || referenceImage.regionMatches(true, 0, "http://", 0, 7)) {
            return referenceImage;
        }

        String localPath = referenceImage;
        if (referenceImage.regionMatches(true, 0, "file://", 0, 7)) {
            localPath = Uri.parse(referenceImage).getPath();
        }
        File imageFile = localPath == null ? null : new File(localPath).getCanonicalFile();
        if (imageFile == null || !imageFile.isFile()) {
            throw new IOException("参考图文件不存在或已被缓存清理，请重新选择图片: " + referenceImage);
        }
        if (imageFile.length() > MAX_REFERENCE_IMAGE_BYTES) {
            throw new IOException("参考图不能超过 50MB，当前: " + imageFile.length() + " 字节");
        }

        BitmapFactory.Options imageInfo = new BitmapFactory.Options();
        imageInfo.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), imageInfo);
        String mimeType = imageInfo.outMimeType;
        if (!("image/jpeg".equals(mimeType) || "image/png".equals(mimeType))) {
            throw new IOException("可灵参考图仅支持 JPEG/PNG，请重新选择兼容图片");
        }
        if (imageInfo.outWidth < 300 || imageInfo.outHeight < 300) {
            throw new IOException("参考图宽高都必须至少为 300 像素，当前: "
                + imageInfo.outWidth + "x" + imageInfo.outHeight);
        }
        double ratio = (double) imageInfo.outWidth / (double) imageInfo.outHeight;
        if (ratio < 0.4d || ratio > 2.5d) {
            throw new IOException("参考图宽高比必须在 1:2.5 到 2.5:1 之间，当前: "
                + imageInfo.outWidth + ":" + imageInfo.outHeight);
        }

        String extension = "image/png".equals(mimeType) ? ".png" : ".jpg";
        String objectKey = "sisterfuture/kling-reference-images/"
            + System.currentTimeMillis() + extension;
        JSONObject uploadOverrides = new JSONObject().put("contentType", mimeType);
        JSONObject uploadResult = ossManager.uploadFile(imageFile, objectKey, false,
            OssManager.DEFAULT_URL_EXPIRY_SECONDS, uploadOverrides);
        return uploadResult.getString("signedUrl");
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
            + "2. 当用户明确要求让刚选择的图片动起来、以图生成视频或将某张图片作为首帧时，把上下文中最近的图片本地绝对路径原样传给 referenceImage；不要虚构路径，也不要擅自复用无关的旧图片\n"
            + "3. referenceImage 也支持 http(s) 公网 URL；本地图片会自动经 OSS 上传，因此需先配置 ossUploadFile 工具备注。省略 referenceImage 时仍为文生视频\n"
            + "4. 可选参数：duration(3-15秒)、resolution(720p/1080p)、aspectRatio(仅文生视频，16:9/9:16/1:1)、watermark\n"
            + "5. API Key：运行时传入，或在工具备注中设置 kling_api_key=xxx\n"
            + "6. 典型场景：参考图动画化、AI短剧片段生成、动态素材、产品演示\n"
            + "7. 中文 prompt 友好，可灵对中文理解优秀\n"
            + "8. 视频生成通常 30 秒-3 分钟，工具会自动轮询到完成\n"
            + "9. 完成后视频自动下载到 /sdcard/Download/ 并扫描到相册\n"
            + "10. 注意：可灵生成的视频 30 天后失效，需要及时转存";
    }
}
