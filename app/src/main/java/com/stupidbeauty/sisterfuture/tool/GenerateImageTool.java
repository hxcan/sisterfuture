package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Environment;
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
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MiniMax 图像生成工具
 *
 * 调用 MiniMax image-01 模型生成图片，支持中英文 prompt、批量生成、自动下载到手机。
 *
 * API 信息：
 * - 端点：https://api.minimaxi.com/v1/image_generation（不是 api.minimax.io）
 * - 模型：image-01
 * - 认证：Bearer Token（sk-cp- 前缀）
 * - 尺寸：512-2048，必须是 8 的倍数
 * - 批量：1-9 张/次
 * - 限流：10 请求/分钟
 *
 * API Key 配置：
 * - 用户可在工具备注中设置 minimax_image_api_key=sk-cp-xxx
 * - 或调用时通过 apiKey 参数传入
 *
 * @author 未来姐姐
 * @date 2026-07-29
 */
public class GenerateImageTool implements Tool {
    private static final String TAG = "GenerateImageTool";

    /** API 端点（注意：不是 api.minimax.io） */
    private static final String API_ENDPOINT = "https://api.minimaxi.com/v1/image_generation";

    /** 模型名称 */
    private static final String MODEL_NAME = "image-01";

    /** 最小尺寸 */
    private static final int MIN_SIZE = 512;

    /** 最大尺寸 */
    private static final int MAX_SIZE = 2048;

    /** 尺寸必须是 8 的倍数 */
    private static final int SIZE_MULTIPLE = 8;

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT_SEC = 60;

    /** 工具备注中存储 API Key 的键名 */
    private static final String NOTE_KEY_API_KEY = "minimax_image_api_key";

    /** 工具备注中存储默认尺寸的键名（格式：widthxheight） */
    private static final String NOTE_KEY_DEFAULT_SIZE = "minimax_image_default_size";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    public GenerateImageTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "generateImage";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "generateImage");
            functionDef.put("description", "调用 MiniMax image-01 模型生成图片，支持中英文 prompt，返回图片自动下载到手机存储（/sdcard/Download/）。可用于制作小说封面、应用图标、UI 配图等场景。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            // 提示词（必填）
            JSONObject promptParam = new JSONObject();
            promptParam.put("type", "string");
            promptParam.put("description", "文本提示词，描述想生成的图片内容。中文友好，建议尽可能详细描述风格、场景、构图等。");
            properties.put("prompt", promptParam);

            // API Key（可选，优先从工具备注读取）
            JSONObject apiKeyParam = new JSONObject();
            apiKeyParam.put("type", "string");
            apiKeyParam.put("description", "MiniMax API Key（sk-cp- 前缀）。如果未传入，会自动从工具备注 minimax_image_api_key 读取。");
            properties.put("apiKey", apiKeyParam);

            // 宽度（可选）
            JSONObject widthParam = new JSONObject();
            widthParam.put("type", "integer");
            widthParam.put("default", 1024);
            widthParam.put("description", "图片宽度（512-2048，必须是 8 的倍数，默认 1024）");
            properties.put("width", widthParam);

            // 高度（可选）
            JSONObject heightParam = new JSONObject();
            heightParam.put("type", "integer");
            heightParam.put("default", 1024);
            heightParam.put("description", "图片高度（512-2048，必须是 8 的倍数，默认 1024）");
            properties.put("height", heightParam);

            // 数量（可选）
            JSONObject nParam = new JSONObject();
            nParam.put("type", "integer");
            nParam.put("default", 1);
            nParam.put("description", "生成数量（1-9，默认 1）");
            properties.put("n", nParam);

            // 是否优化 prompt（可选）
            JSONObject optimizerParam = new JSONObject();
            optimizerParam.put("type", "boolean");
            optimizerParam.put("default", true);
            optimizerParam.put("description", "是否启用 prompt 自动优化（默认 true，对中文友好）");
            properties.put("promptOptimizer", optimizerParam);

            // 保存到手机的目录（可选）
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
        executor.execute(() -> {
            try {
                // 1. 解析参数
                String prompt = arguments.optString("prompt", null);
                if (prompt == null || prompt.trim().isEmpty()) {
                    throw new IllegalArgumentException("prompt 不能为空");
                }

                // 2. 获取 API Key（优先从参数，其次从工具备注）
                String apiKey = arguments.optString("apiKey", null);
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = getApiKeyFromNote();
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                            "API Key 未配置。请通过以下方式之一提供：\n"
                            + "1. 调用时传入 apiKey 参数\n"
                            + "2. 在工具备注中设置 " + NOTE_KEY_API_KEY + "=sk-cp-xxx"
                        );
                    }
                }

                int width = arguments.optInt("width", 1024);
                int height = arguments.optInt("height", 1024);
                int n = arguments.optInt("n", 1);
                boolean promptOptimizer = arguments.optBoolean("promptOptimizer", true);
                String saveDir = arguments.optString("saveDir", null);

                FileLogger.i(TAG, "==== generateImage 工具开始执行 ====");
                FileLogger.i(TAG, "prompt 长度: " + prompt.length());
                FileLogger.i(TAG, "尺寸: " + width + "x" + height + ", 数量: " + n);

                // 3. 参数校验
                validateParams(width, height, n);

                // 4. 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL_NAME);
                requestBody.put("prompt", prompt);
                requestBody.put("n", n);
                requestBody.put("width", width);
                requestBody.put("height", height);
                requestBody.put("prompt_optimizer", promptOptimizer);

                FileLogger.d(TAG, "请求体: " + requestBody.toString());

                // 5. 发送 HTTP 请求
                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(mediaType, requestBody.toString());

                Request request = new Request.Builder()
                        .url(API_ENDPOINT)
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .build();

                FileLogger.i(TAG, "发送请求到: " + API_ENDPOINT);

                long startTime = System.currentTimeMillis();
                Response response = client.newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";
                long durationMs = System.currentTimeMillis() - startTime;

                FileLogger.i(TAG, "响应耗时: " + durationMs + "ms, 状态码: " + response.code());

                // 6. 处理响应
                if (!response.isSuccessful()) {
                    handleHttpError(response.code(), responseBody, callback);
                    return;
                }

                JSONObject jsonResponse = new JSONObject(responseBody);

                // 检查错误字段
                if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                    JSONObject errorObj = jsonResponse.getJSONObject("error");
                    String errorMsg = errorObj.optString("message", "未知错误");
                    FileLogger.e(TAG, "API 返回错误: " + errorMsg);
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("error", "API 返回错误: " + errorMsg);
                    errorResult.put("error_type", "api_error");
                    errorResult.put("status_code", response.code());
                    callback.onResult(errorResult);
                    return;
                }

                // 7. 解析图片数据
                if (!jsonResponse.has("data")) {
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("error", "响应中没有 data 字段");
                    errorResult.put("raw_response", responseBody);
                    callback.onResult(errorResult);
                    return;
                }

                JSONArray dataArray = jsonResponse.getJSONArray("data");
                if (dataArray.length() == 0) {
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("error", "响应中 data 数组为空");
                    callback.onResult(errorResult);
                    return;
                }

                // 8. 确定保存目录
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
                FileLogger.i(TAG, "保存目录: " + targetDir.getAbsolutePath());

                // 9. 下载图片到本地
                JSONArray savedPaths = new JSONArray();
                JSONArray originalUrls = new JSONArray();
                long timestamp = System.currentTimeMillis();

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject item = dataArray.getJSONObject(i);

                    if (item.has("url")) {
                        String url = item.getString("url");
                        originalUrls.put(url);
                        String savedPath = downloadImageFromUrl(url, targetDir, timestamp, i);
                        savedPaths.put(savedPath);
                    } else if (item.has("b64_json")) {
                        String b64Data = item.getString("b64_json");
                        originalUrls.put("base64:image");
                        String savedPath = saveBase64Image(b64Data, targetDir, timestamp, i);
                        savedPaths.put(savedPath);
                    }
                }

                FileLogger.i(TAG, "✅ 成功生成 " + savedPaths.length() + " 张图片");

                // 10. 返回结果
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("image_count", savedPaths.length());
                result.put("saved_paths", savedPaths);
                result.put("original_urls", originalUrls);
                result.put("save_dir", targetDir.getAbsolutePath());
                result.put("model", MODEL_NAME);
                result.put("width", width);
                result.put("height", height);
                result.put("duration_ms", durationMs);
                result.put("timestamp", timestamp);

                callback.onResult(result);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ executeAsync 出错", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 参数校验
     */
    private void validateParams(int width, int height, int n) throws IllegalArgumentException {
        if (width < MIN_SIZE || width > MAX_SIZE || width % SIZE_MULTIPLE != 0) {
            throw new IllegalArgumentException(
                String.format("宽度必须在 %d-%d 之间且是 %d 的倍数，当前: %d",
                    MIN_SIZE, MAX_SIZE, SIZE_MULTIPLE, width)
            );
        }
        if (height < MIN_SIZE || height > MAX_SIZE || height % SIZE_MULTIPLE != 0) {
            throw new IllegalArgumentException(
                String.format("高度必须在 %d-%d 之间且是 %d 的倍数，当前: %d",
                    MIN_SIZE, MAX_SIZE, SIZE_MULTIPLE, height)
            );
        }
        if (n < 1 || n > 9) {
            throw new IllegalArgumentException("n 必须在 1-9 之间，当前: " + n);
        }
    }

    /**
     * 从工具备注中读取 API Key
     * 备注格式示例："minimax_image_api_key=sk-cp-xxx\nminimax_image_default_size=1024x1024"
     */
    private String getApiKeyFromNote() {
        String note = getNote(context);
        if (note == null || note.isEmpty()) {
            return null;
        }

        // 按行解析
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

    /**
     * 从 URL 下载图片到本地
     */
    private String downloadImageFromUrl(String imageUrl, File targetDir, long timestamp, int index) throws IOException {
        String filename = String.format("minimax_image_%d_%d.png", timestamp, index);
        File targetFile = new File(targetDir, filename);

        FileLogger.d(TAG, "下载图片: " + imageUrl + " -> " + targetFile.getAbsolutePath());

        Request request = new Request.Builder().url(imageUrl).build();
        Response response = client.newCall(request).execute();

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
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }

        FileLogger.i(TAG, "✅ 图片已保存: " + targetFile.getAbsolutePath());
        return targetFile.getAbsolutePath();
    }

    /**
     * 保存 base64 图片到本地
     */
    private String saveBase64Image(String b64Data, File targetDir, long timestamp, int index) throws IOException {
        String filename = String.format("minimax_image_%d_%d.png", timestamp, index);
        File targetFile = new File(targetDir, filename);

        FileLogger.d(TAG, "保存 base64 图片 -> " + targetFile.getAbsolutePath());

        byte[] imageBytes = Base64.getDecoder().decode(b64Data);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(imageBytes);
        }

        FileLogger.i(TAG, "✅ base64 图片已保存: " + targetFile.getAbsolutePath());
        return targetFile.getAbsolutePath();
    }

    /**
     * 处理 HTTP 错误
     */
    private void handleHttpError(int code, String body, OnResultCallback callback) {
        String errorDetail = "";
        try {
            if (!body.isEmpty()) {
                JSONObject errorJson = new JSONObject(body);
                if (errorJson.has("error")) {
                    errorDetail = errorJson.getJSONObject("error").optString("message", "");
                }
            }
        } catch (Exception ignore) {}

        JSONObject errorResult = new JSONObject();
        errorResult.put("status", "error");
        errorResult.put("status_code", code);
        errorResult.put("error", "HTTP " + code + ": " + errorDetail);

        try {
            if (code == 401 || code == 403) {
                errorResult.put("error_type", "invalid_api_key");
                errorResult.put("error", "API Key 无效或已过期: " + errorDetail);
            } else if (code == 429) {
                errorResult.put("error_type", "rate_limited");
                errorResult.put("error", "触发限流，请稍后再试: " + errorDetail);
            } else if (code >= 500) {
                errorResult.put("error_type", "server_error");
            } else {
                errorResult.put("error_type", "http_error");
            }
        } catch (Exception ignore) {}

        callback.onResult(errorResult);
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "调用 generateImage 时：\n"
            + "1. 必传参数：prompt（描述想生成的图片）\n"
            + "2. 可选参数：apiKey（优先从工具备注 minimax_image_api_key 读取）、width/height（512-2048，8 的倍数）、n（1-9）\n"
            + "3. 典型场景：小说封面、应用图标、UI 配图、营销海报\n"
            + "4. 适合复杂、抽象、艺术性的图片生成需求，不适合代码截图、表格等结构化内容\n"
            + "5. 中文 prompt 友好，建议详细描述风格、场景、构图、色彩等要素\n"
            + "6. 返回的图片会自动下载到 /sdcard/Download/，可直接在应用中预览";
    }
}