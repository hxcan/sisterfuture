package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
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
 * 通义万相图像生成工具（阿里云百炼 Token Plan）
 *
 * 核心能力：
 * - 文生图（text-to-image）
 * - 图生图（image-to-image / 风格迁移）
 * - 接受本地图片路径或公网 URL
 * - 本地图片自动转 base64 上传
 *
 * 调用方式：异步任务（提交任务 + 轮询结果）
 *
 * @author 未来姐姐
 * @date 2026-07-31
 */
public class WanxiangTool implements Tool {
    private static final String TAG = "WanxiangTool";

    /**
     * 阿里云百炼 API 提交任务端点（Token Plan 异步）
     */
    private static final String SUBMIT_ENDPOINT = "https://token-plan.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/image-generation/generation";

    /**
     * 阿里云百炼 API 轮询任务端点（Task ID 前缀）
     */
    private static final String POLL_ENDPOINT_PREFIX = "https://token-plan.cn-beijing.maas.aliyuncs.com/api/v1/tasks/";

    /**
     * 默认模型：通义万相图生图
     */
    private static final String MODEL_NAME = "wan2.7-image";

    private static final int DEFAULT_TIMEOUT_SEC = 120;
    private static final int MAX_IMAGE_SIZE_MB = 10;
    private static final int POLL_INTERVAL_MS = 2000;
    private static final int POLL_MAX_ATTEMPTS = 60; // 最多等 120 秒

    private static final String NOTE_KEY_API_KEY = "dashscope_api_key";
    private static final String NOTE_KEY_DEFAULT_MODEL = "wanxiang_default_model";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static class ClientHolder {
        private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private static OkHttpClient getClient() {
        return ClientHolder.INSTANCE;
    }

    public WanxiangTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "wanxiangImage";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "wanxiangImage");
            functionDef.put("description", "调用阿里云百炼通义万相（wan2.7-image）生成或编辑图片。支持文生图、图生图、风格迁移。需要传入参考图（本地路径或公网 URL）时，会自动转 base64 上传。返回图片自动下载到手机存储并扫描到相册。典型场景：照片转动漫/油画/水彩、商品图、头像、插画。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject promptParam = new JSONObject();
            promptParam.put("type", "string");
            promptParam.put("description", "文本提示词，描述想生成或转换的图片内容/风格。中文友好，例如：'转成动漫风格'、'梵高油画风格'、'水墨画风格'");
            properties.put("prompt", promptParam);

            JSONObject apiKeyParam = new JSONObject();
            apiKeyParam.put("type", "string");
            apiKeyParam.put("description", "阿里云百炼 API Key（sk- 前缀）。如果未传入，会自动从工具备注 dashscope_api_key 读取");
            properties.put("apiKey", apiKeyParam);

            JSONObject referenceImageParam = new JSONObject();
            referenceImageParam.put("type", "string");
            referenceImageParam.put("description", "【图生图必填】参考图本地路径（如 /sdcard/Download/原图.jpg）或公网 URL。如果是本地路径，会自动转 base64 上传");
            properties.put("referenceImage", referenceImageParam);

            JSONObject sizeParam = new JSONObject();
            sizeParam.put("type", "string");
            sizeParam.put("default", "2K");
            sizeParam.put("description", "图片尺寸，支持 '1K'、'2K'、'1024*1024'、'1280*720' 等");
            properties.put("size", sizeParam);

            JSONObject nParam = new JSONObject();
            nParam.put("type", "integer");
            nParam.put("default", 1);
            nParam.put("description", "生成数量（1-4，建议 1-2，因为通义万相较慢）");
            properties.put("n", nParam);

            JSONObject modelParam = new JSONObject();
            modelParam.put("type", "string");
            modelParam.put("default", "wan2.7-image");
            modelParam.put("description", "模型名称。常用：wan2.7-image、wan2.7-image-pro。默认从工具备注 wanxiang_default_model 读取");
            properties.put("model", modelParam);

            JSONObject saveDirParam = new JSONObject();
            saveDirParam.put("type", "string");
            saveDirParam.put("description", "图片保存目录（默认 /sdcard/Download/）");
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
                FileLogger.i(TAG, "========== wanxiangImage 工具开始执行 ==========");

                String prompt = arguments.optString("prompt", null);
                if (prompt == null || prompt.trim().isEmpty()) {
                    throw new IllegalArgumentException("prompt 不能为空");
                }
                String referenceImage = arguments.optString("referenceImage", null);
                String size = arguments.optString("size", "2K");
                int n = arguments.optInt("n", 1);
                String saveDir = arguments.optString("saveDir", null);
                String modelOverride = arguments.optString("model", null);

                String apiKey = arguments.optString("apiKey", null);
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = getApiKeyFromNote();
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                            "API Key 未配置。请通过以下方式之一提供：\n"
                            + "1. 调用时传入 apiKey 参数\n"
                            + "2. 在工具备注中设置 " + NOTE_KEY_API_KEY + "=sk-xxx"
                        );
                    }
                }
                FileLogger.i(TAG, "[1/10] 获取 API Key 完成（掩码: " + maskApiKey(apiKey) + "）");

                String modelName = modelOverride;
                if (modelName == null || modelName.trim().isEmpty()) {
                    modelName = getModelFromNote();
                    if (modelName == null || modelName.trim().isEmpty()) {
                        modelName = MODEL_NAME;
                    }
                }
                FileLogger.i(TAG, "  使用模型: " + modelName);

                validateParams(n);

                String imageContent = null;
                if (referenceImage != null && !referenceImage.trim().isEmpty()) {
                    imageContent = processReferenceImage(referenceImage);
                    FileLogger.i(TAG, "[2/10] 参考图处理完成，长度: " + imageContent.length() + " 字符");
                } else {
                    FileLogger.i(TAG, "[2/10] 无参考图，纯文生图模式");
                }

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", modelName);

                JSONObject input = new JSONObject();
                JSONArray messages = new JSONArray();

                JSONObject message = new JSONObject();
                message.put("role", "user");

                JSONArray content = new JSONArray();
                if (imageContent != null) {
                    JSONObject imageItem = new JSONObject();
                    imageItem.put("image", imageContent);
                    content.put(imageItem);
                }

                JSONObject textItem = new JSONObject();
                textItem.put("text", prompt);
                content.put(textItem);

                message.put("content", content);
                messages.put(message);
                input.put("messages", messages);

                requestBody.put("input", input);

                JSONObject params = new JSONObject();
                params.put("size", size);
                params.put("n", n);
                params.put("watermark", false);
                requestBody.put("parameters", params);

                FileLogger.i(TAG, "[3/10] 请求体构建完成");

                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(mediaType, requestBody.toString());

                Request request = new Request.Builder()
                        .url(SUBMIT_ENDPOINT)
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-DashScope-Async", "enable")
                        .build();

                FileLogger.i(TAG, "[4/10] 提交异步任务到: " + SUBMIT_ENDPOINT);

                String taskId;
                long submitTime = System.currentTimeMillis();
                try (Response response = getClient().newCall(request).execute()) {
                    ResponseBody respBody = response.body();
                    if (respBody == null) {
                        throw new IOException("响应体为空");
                    }
                    String respStr = respBody.string();
                    FileLogger.i(TAG, "  提交响应: " + respStr);

                    if (response.code() < 200 || response.code() >= 300) {
                        handleHttpError(response.code(), respStr, callback);
                        return;
                    }

                    JSONObject jsonResp = new JSONObject(respStr);
                    taskId = jsonResp.getJSONObject("output").getString("task_id");
                    FileLogger.i(TAG, "  获取到 task_id: " + taskId);
                }

                FileLogger.i(TAG, "[5/10] 开始轮询任务状态");

                String imageUrl = pollTaskResult(taskId, apiKey);

                if (imageUrl == null) {
                    throw new IOException("任务未返回图片 URL");
                }
                FileLogger.i(TAG, "[6/10] 获取到图片 URL: " + imageUrl);

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

                JSONArray savedPaths = new JSONArray();
                JSONArray attachmentsArray = new JSONArray();
                long timestamp = System.currentTimeMillis();

                String filename = String.format("wanxiang_image_%d.png", timestamp);
                File targetFile = new File(targetDir, filename);
                String savedPath = downloadImageFromUrl(imageUrl, targetFile);
                savedPaths.put(savedPath);
                scanImageToGallery(savedPath);

                String[] sizeParts = size.replace("K", "").replace("k", "").split("\\*");
                int imgWidth = 1024, imgHeight = 1024;
                if (sizeParts.length == 2) {
                    try {
                        imgWidth = Integer.parseInt(sizeParts[0]);
                        imgHeight = Integer.parseInt(sizeParts[1]);
                    } catch (Exception ignore) {}
                } else if ("2K".equalsIgnoreCase(size)) {
                    imgWidth = imgHeight = 2048;
                } else if ("1K".equalsIgnoreCase(size)) {
                    imgWidth = imgHeight = 1024;
                }

                JSONObject attachment = new JSONObject();
                attachment.put("type", "image");
                attachment.put("url", "file://" + savedPath);
                JSONObject metadata = new JSONObject();
                metadata.put("width", imgWidth);
                metadata.put("height", imgHeight);
                File file = new File(savedPath);
                if (file.exists()) {
                    metadata.put("size", file.length());
                }
                metadata.put("mimeType", "image/png");
                attachment.put("metadata", metadata);
                attachmentsArray.put(attachment);

                long totalDurationMs = System.currentTimeMillis() - totalStartTime;
                FileLogger.i(TAG, "✅ 成功生成图片，总耗时: " + totalDurationMs + "ms");

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("image_count", 1);
                result.put("saved_paths", savedPaths);
                result.put("save_dir", targetDir.getAbsolutePath());
                result.put("model", modelName);
                result.put("size", size);
                result.put("task_id", taskId);
                result.put("total_duration_ms", totalDurationMs);
                result.put("timestamp", timestamp);
                result.put("attachments", attachmentsArray);

                callback.onResult(result);

            } catch (Exception e) {
                long totalDurationMs = System.currentTimeMillis() - totalStartTime;
                FileLogger.e(TAG, "❌ executeAsync 出错 - 总耗时: " + totalDurationMs + "ms", e);
                FileLogger.e(TAG, "  异常类型: " + e.getClass().getName());
                FileLogger.e(TAG, "  异常信息: " + e.getMessage());
                callback.onError(e);
            }
        });
    }

    /**
     * 轮询任务结果
     */
    private String pollTaskResult(String taskId, String apiKey) throws IOException, InterruptedException {
        String pollUrl = POLL_ENDPOINT_PREFIX + taskId;
        FileLogger.i(TAG, "  轮询端点: " + pollUrl);

        for (int attempt = 1; attempt <= POLL_MAX_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            Request pollRequest = new Request.Builder()
                    .url(pollUrl)
                    .get()
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = getClient().newCall(pollRequest).execute()) {
                ResponseBody respBody = response.body();
                if (respBody == null) {
                    throw new IOException("响应体为空");
                }
                String respStr = respBody.string();

                if (response.code() < 200 || response.code() >= 300) {
                    FileLogger.e(TAG, "  轮询失败 (尝试 " + attempt + "/" + POLL_MAX_ATTEMPTS + "): HTTP " + response.code());
                    continue;
                }

                JSONObject jsonResp = new JSONObject(respStr);
                JSONObject output = jsonResp.getJSONObject("output");
                String status = output.getString("task_status");
                FileLogger.d(TAG, "  轮询状态 (尝试 " + attempt + "/" + POLL_MAX_ATTEMPTS + "): " + status);

                if ("SUCCEEDED".equals(status)) {
                    JSONArray choices = output.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONArray content = choices.getJSONObject(0).getJSONObject("message").getJSONArray("content");
                        for (int i = 0; i < content.length(); i++) {
                            JSONObject item = content.getJSONObject(i);
                            if ("image".equals(item.optString("type"))) {
                                return item.getString("image");
                            }
                        }
                    }
                    throw new IOException("任务成功但未返回图片: " + respStr);
                } else if ("FAILED".equals(status)) {
                    String code = output.optString("code", "");
                    String message = output.optString("message", "");
                    throw new IOException("任务失败: " + code + " - " + message);
                } else if ("CANCELED".equals(status)) {
                    throw new IOException("任务被取消");
                }
            }
        }
        throw new IOException("任务超时（等待 " + (POLL_MAX_ATTEMPTS * POLL_INTERVAL_MS / 1000) + " 秒）");
    }

    /**
     * 处理参考图：本地路径转 base64，或直接使用公网 URL
     */
    private String processReferenceImage(String referenceImage) throws IOException {
        if (referenceImage.startsWith("http://") || referenceImage.startsWith("https://")) {
            FileLogger.d(TAG, "  [ref] 公网 URL，直接使用: " + referenceImage);
            return referenceImage;
        }

        File file = new File(referenceImage);
        if (!file.exists()) {
            throw new IOException("参考图文件不存在: " + referenceImage);
        }
        long fileSize = file.length();
        if (fileSize > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
            throw new IOException("参考图过大: " + fileSize + " 字节，最大允许 " + MAX_IMAGE_SIZE_MB + "MB");
        }
        FileLogger.d(TAG, "  [ref] 读取本地图片: " + referenceImage + " (" + fileSize + " 字节)");

        byte[] bytes = new byte[(int) fileSize];
        try (InputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < fileSize) {
                int read = in.read(bytes, offset, (int) (fileSize - offset));
                if (read < 0) break;
                offset += read;
            }
        }
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String lowerName = referenceImage.toLowerCase();
        String mimeType = "image/jpeg";
        if (lowerName.endsWith(".png")) {
            mimeType = "image/png";
        } else if (lowerName.endsWith(".webp")) {
            mimeType = "image/webp";
        } else if (lowerName.endsWith(".bmp")) {
            mimeType = "image/bmp";
        }
        FileLogger.d(TAG, "  [ref] 已转 base64，MIME: " + mimeType);
        return "data:" + mimeType + ";base64," + base64;
    }

    private void validateParams(int n) throws IllegalArgumentException {
        if (n < 1 || n > 4) {
            throw new IllegalArgumentException("n 必须在 1-4 之间（通义万相较慢），当前: " + n);
        }
    }

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

    private String getModelFromNote() {
        String note = getNote(context);
        if (note == null || note.isEmpty()) {
            return null;
        }
        String[] lines = note.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(NOTE_KEY_DEFAULT_MODEL + "=")) {
                String value = line.substring((NOTE_KEY_DEFAULT_MODEL + "=").length()).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String downloadImageFromUrl(String imageUrl, File targetFile) throws IOException {
        long startTime = System.currentTimeMillis();
        FileLogger.d(TAG, "  下载图片: " + imageUrl + " -> " + targetFile.getAbsolutePath());

        Request request = new Request.Builder().url(imageUrl).build();

        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载图片失败: HTTP " + response.code());
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
                FileLogger.d(TAG, "  下载完成: " + totalBytes + " 字节，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            }
        }

        FileLogger.i(TAG, "✅ 图片已保存: " + targetFile.getAbsolutePath());
        return targetFile.getAbsolutePath();
    }

    private void scanImageToGallery(final String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        String mimeType = "image/png";
        try {
            MediaScannerConnection.scanFile(
                context,
                new String[]{filePath},
                new String[]{mimeType},
                null
            );
            FileLogger.d(TAG, "  [scan] 已提交扫描任务: " + filePath);
        } catch (Exception e) {
            FileLogger.e(TAG, "  [scan] MediaScannerConnection 失败", e);
            try {
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                context.sendBroadcast(intent);
            } catch (Exception ex) {
                FileLogger.e(TAG, "  [scan] 广播扫描也失败了: " + ex.getMessage());
            }
        }
    }

    private void handleHttpError(int code, String body, OnResultCallback callback) {
        String errorDetail = "";
        try {
            if (!body.isEmpty()) {
                JSONObject errorJson = new JSONObject(body);
                if (errorJson.has("message")) {
                    errorDetail = errorJson.optString("message", "");
                } else if (errorJson.has("error")) {
                    Object errorVal = errorJson.get("error");
                    if (errorVal instanceof JSONObject) {
                        errorDetail = ((JSONObject) errorVal).optString("message", "");
                    } else {
                        errorDetail = errorVal.toString();
                    }
                }
            }
        } catch (Exception ignore) {}

        JSONObject errorResult = new JSONObject();
        try {
            errorResult.put("status", "error");
            errorResult.put("status_code", code);

            if (code == 401 || code == 403) {
                errorResult.put("error_type", "invalid_api_key");
                errorResult.put("error", "API Key 无效或已过期（请检查 Token Plan 是否激活）: " + errorDetail);
            } else if (code == 429) {
                errorResult.put("error_type", "rate_limited");
                errorResult.put("error", "触发限流（Token Plan 有额度限制），请稍后再试: " + errorDetail);
            } else if (code == 400) {
                errorResult.put("error_type", "bad_request");
                errorResult.put("error", "请求参数错误: " + errorDetail);
            } else if (code >= 500) {
                errorResult.put("error_type", "server_error");
                errorResult.put("error", "通义万相服务器错误 HTTP " + code + ": " + errorDetail);
            } else {
                errorResult.put("error_type", "http_error");
                errorResult.put("error", "HTTP " + code + ": " + errorDetail);
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to build error result", e);
        }

        callback.onResult(errorResult);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "调用 wanxiangImage 工具时：\n"
            + "1. 必传参数：prompt（描述图片内容/风格）\n"
            + "2. 可选参数：referenceImage（图生图时必传，支持本地路径或公网 URL）、size（如 '2K'/'1024*1024'）、n（1-4）、model（默认 wan2.7-image）\n"
            + "3. API Key：优先用调用时传入的 apiKey，否则从工具备注 dashscope_api_key 读取\n"
            + "4. 典型场景：照片转动漫/油画/水彩风格、商品图生成、头像定制、插画创作\n"
            + "5. 与 generateImage 工具的差异：wanxiangImage 支持图生图和风格迁移，但需要主人已有 Token Plan 订阅\n"
            + "6. 中文 prompt 友好，建议详细描述想要的风格、场景、变换效果\n"
            + "7. 返回的图片会自动下载到 /sdcard/Download/ 并扫描到系统相册\n"
            + "8. 注意：通义万相速度比 MiniMax 慢，n 建议不超过 2\n"
            + "9. 该工具使用异步调用，提交任务后会自动轮询直到完成";
    }
}