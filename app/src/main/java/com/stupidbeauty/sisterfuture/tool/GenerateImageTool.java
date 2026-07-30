package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;
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
 * 响应格式（兼容两种）：
 * - 新格式：{"image_urls": ["url1", "url2", ...]}
 * - 旧格式：{"data": [{"url": "url1"}, {"url": "url2"}, ...]}
 *
 * @author 未来姐姐
 * @date 2026-07-30
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

    /**
     * OkHttpClient 单例（修复 Bug 3：避免每次 new 客户端导致连接池失效）
     * 使用静态内部类 Holder 模式实现：线程安全 + 延迟加载
     * 完整超时配置（修复 Bug 1：补全 connect/read/write/call timeout）
     */
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

            JSONObject promptParam = new JSONObject();
            promptParam.put("type", "string");
            promptParam.put("description", "文本提示词，描述想生成的图片内容。中文友好，建议尽可能详细描述风格、场景、构图等。");
            properties.put("prompt", promptParam);

            JSONObject apiKeyParam = new JSONObject();
            apiKeyParam.put("type", "string");
            apiKeyParam.put("description", "MiniMax API Key（sk-cp- 前缀）。如果未传入，会自动从工具备注 minimax_image_api_key 读取。");
            properties.put("apiKey", apiKeyParam);

            JSONObject widthParam = new JSONObject();
            widthParam.put("type", "integer");
            widthParam.put("default", 1024);
            widthParam.put("description", "图片宽度（512-2048，必须是 8 的倍数，默认 1024）");
            properties.put("width", widthParam);

            JSONObject heightParam = new JSONObject();
            heightParam.put("type", "integer");
            heightParam.put("default", 1024);
            heightParam.put("description", "图片高度（512-2048，必须是 8 的倍数，默认 1024）");
            properties.put("height", heightParam);

            JSONObject nParam = new JSONObject();
            nParam.put("type", "integer");
            nParam.put("default", 1);
            nParam.put("description", "生成数量（1-9，默认 1）");
            properties.put("n", nParam);

            JSONObject optimizerParam = new JSONObject();
            optimizerParam.put("type", "boolean");
            optimizerParam.put("default", true);
            optimizerParam.put("description", "是否启用 prompt 自动优化（默认 true，对中文友好）");
            properties.put("promptOptimizer", optimizerParam);

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
                FileLogger.i(TAG, "========== generateImage 工具开始执行 ==========");

                long stepStart = System.currentTimeMillis();
                String prompt = arguments.optString("prompt", null);
                if (prompt == null || prompt.trim().isEmpty()) {
                    throw new IllegalArgumentException("prompt 不能为空");
                }
                FileLogger.d(TAG, "[1/10] 解析参数完成 - prompt 长度: " + prompt.length() + "，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                stepStart = System.currentTimeMillis();
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
                FileLogger.i(TAG, "[2/10] 获取 API Key 完成（掩码: " + maskApiKey(apiKey) + "），耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                int width = arguments.optInt("width", 1024);
                int height = arguments.optInt("height", 1024);
                int n = arguments.optInt("n", 1);
                boolean promptOptimizer = arguments.optBoolean("promptOptimizer", true);
                String saveDir = arguments.optString("saveDir", null);

                stepStart = System.currentTimeMillis();
                validateParams(width, height, n);
                FileLogger.i(TAG, "[3/10] 参数校验通过 - 尺寸: " + width + "x" + height + "，数量: " + n + "，optimizer: " + promptOptimizer + "，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");

                stepStart = System.currentTimeMillis();
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", MODEL_NAME);
                requestBody.put("prompt", prompt);
                requestBody.put("n", n);
                requestBody.put("width", width);
                requestBody.put("height", height);
                requestBody.put("prompt_optimizer", promptOptimizer);

                FileLogger.d(TAG, "[4/10] 请求体构建完成 - 大小: " + requestBody.toString().length() + " 字节，耗时: " + (System.currentTimeMillis() - stepStart) + "ms");
                FileLogger.d(TAG, "  请求体内容: " + requestBody.toString());

                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(mediaType, requestBody.toString());

                Request request = new Request.Builder()
                        .url(API_ENDPOINT)
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .build();

                FileLogger.i(TAG, "[5/10] 发送请求到: " + API_ENDPOINT);
                FileLogger.i(TAG, "  使用 OkHttpClient 单例，超时配置: connect=30s, read=120s, write=60s, call=180s");

                long requestStartTime = System.currentTimeMillis();

                String responseBody;
                int responseCode;
                try (Response response = getClient().newCall(request).execute()) {
                    responseCode = response.code();
                    FileLogger.i(TAG, "[6/10] HTTP 响应到达 - 耗时: " + (System.currentTimeMillis() - requestStartTime) + "ms");
                    FileLogger.i(TAG, "  状态码: " + responseCode);

                    ResponseBody respBody = response.body();
                    if (respBody == null) {
                        throw new IOException("响应体为空");
                    }
                    responseBody = respBody.string();
                    FileLogger.i(TAG, "  响应体大小: " + responseBody.length() + " 字节");
                }

                long requestDurationMs = System.currentTimeMillis() - requestStartTime;
                FileLogger.i(TAG, "  HTTP 请求总耗时: " + requestDurationMs + "ms");

                if (responseCode < 200 || responseCode >= 300) {
                    handleHttpError(responseCode, responseBody, callback);
                    return;
                }

                JSONObject jsonResponse = new JSONObject(responseBody);

                if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                    JSONObject errorObj = jsonResponse.getJSONObject("error");
                    String errorMsg = errorObj.optString("message", "未知错误");
                    FileLogger.e(TAG, "API 返回错误: " + errorMsg);
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("error", "API 返回错误: " + errorMsg);
                    errorResult.put("error_type", "api_error");
                    errorResult.put("status_code", responseCode);
                    callback.onResult(errorResult);
                    return;
                }

                // 兼容两种响应格式：
                // 新格式：{"image_urls": ["url1", "url2", ...]}
                // 旧格式：{"data": [{"url": "url1"}, {"url": "url2"}, ...]}
                JSONArray urlArray = extractImageUrls(jsonResponse);
                if (urlArray == null || urlArray.length() == 0) {
                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("error", "响应中找不到图片 URL（既没有 image_urls 也没有 data.url）");
                    errorResult.put("raw_response", responseBody);
                    callback.onResult(errorResult);
                    return;
                }

                FileLogger.i(TAG, "[7/10] 解析到 " + urlArray.length() + " 张图片 URL");

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
                FileLogger.i(TAG, "[8/10] 保存目录: " + targetDir.getAbsolutePath());

                JSONArray savedPaths = new JSONArray();
                JSONArray originalUrls = new JSONArray();
                long timestamp = System.currentTimeMillis();

                for (int i = 0; i < urlArray.length(); i++) {
                    FileLogger.i(TAG, "[9/10] 处理第 " + (i + 1) + "/" + urlArray.length() + " 张图片");
                    String url = urlArray.getString(i);
                    originalUrls.put(url);
                    String savedPath = downloadImageFromUrl(url, targetDir, timestamp, i);
                    savedPaths.put(savedPath);
                }

                long totalDurationMs = System.currentTimeMillis() - totalStartTime;
                FileLogger.i(TAG, "✅ 成功生成 " + savedPaths.length() + " 张图片");
                FileLogger.i(TAG, "工具总耗时: " + totalDurationMs + "ms");

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("image_count", savedPaths.length());
                result.put("saved_paths", savedPaths);
                result.put("original_urls", originalUrls);
                result.put("save_dir", targetDir.getAbsolutePath());
                result.put("model", MODEL_NAME);
                result.put("width", width);
                result.put("height", height);
                result.put("duration_ms", requestDurationMs);
                result.put("total_duration_ms", totalDurationMs);
                result.put("timestamp", timestamp);

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
     * 从 JSON 响应中提取图片 URL 数组
     * 兼容两种格式：
     * - 新格式：{"image_urls": ["url1", "url2"]}
     * - 旧格式：{"data": [{"url": "url1"}, {"url": "url2"}]}
     */
    private JSONArray extractImageUrls(JSONObject jsonResponse) {
        // 优先尝试新格式：image_urls
        if (jsonResponse.has("image_urls") && !jsonResponse.isNull("image_urls")) {
            try {
                Object value = jsonResponse.get("image_urls");
                if (value instanceof JSONArray) {
                    FileLogger.i(TAG, "  检测到新格式: image_urls 数组");
                    return (JSONArray) value;
                }
            } catch (Exception e) {
                FileLogger.w(TAG, "解析 image_urls 失败: " + e.getMessage());
            }
        }

        // 兼容旧格式：data[].url 或 data[].b64_json
        if (jsonResponse.has("data") && !jsonResponse.isNull("data")) {
            try {
                Object dataValue = jsonResponse.get("data");
                if (dataValue instanceof JSONArray) {
                    FileLogger.i(TAG, "  检测到旧格式: data 数组，转换为 URL 数组");
                    JSONArray dataArray = (JSONArray) dataValue;
                    JSONArray urlArray = new JSONArray();
                    for (int i = 0; i < dataArray.length(); i++) {
                        Object item = dataArray.get(i);
                        if (item instanceof JSONObject) {
                            JSONObject itemObj = (JSONObject) item;
                            if (itemObj.has("url")) {
                                urlArray.put(itemObj.getString("url"));
                            }
                        } else if (item instanceof String) {
                            // data 数组直接就是字符串数组的情况
                            urlArray.put((String) item);
                        }
                    }
                    if (urlArray.length() > 0) {
                        return urlArray;
                    }
                }
            } catch (Exception e) {
                FileLogger.w(TAG, "解析 data 失败: " + e.getMessage());
            }
        }

        return null;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

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

    private String downloadImageFromUrl(String imageUrl, File targetDir, long timestamp, int index) throws IOException {
        String filename = String.format("minimax_image_%d_%d.jpg", timestamp, index);
        File targetFile = new File(targetDir, filename);

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

    private void handleHttpError(int code, String body, OnResultCallback callback) {
        String errorDetail = "";
        try {
            if (!body.isEmpty()) {
                JSONObject errorJson = new JSONObject(body);
                if (errorJson.has("error")) {
                    errorDetail = errorJson.getJSONObject("error").optString("message", "");
                } else if (errorJson.has("base_resp")) {
                    JSONObject baseResp = errorJson.getJSONObject("base_resp");
                    errorDetail = baseResp.optString("status_msg", "");
                }
            }
        } catch (Exception ignore) {}

        JSONObject errorResult = new JSONObject();
        try {
            errorResult.put("status", "error");
            errorResult.put("status_code", code);

            if (code == 401 || code == 403) {
                errorResult.put("error_type", "invalid_api_key");
                errorResult.put("error", "API Key 无效或已过期: " + errorDetail);
            } else if (code == 429) {
                errorResult.put("error_type", "rate_limited");
                errorResult.put("error", "触发限流，请稍后再试: " + errorDetail);
            } else if (code >= 500) {
                errorResult.put("error_type", "server_error");
                errorResult.put("error", "服务器错误 HTTP " + code + ": " + errorDetail);
            } else {
                errorResult.put("error_type", "http_error");
                errorResult.put("error", "HTTP " + code + ": " + errorDetail);
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to build error result", e);
        }

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