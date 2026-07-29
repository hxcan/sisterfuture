package com.stupidbeauty.sisterfuture.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MiniMax 图像生成工具
 *
 * 调用 MiniMax image-01 模型生成图片，支持中英文 prompt、批量生成、自动下载到本地。
 *
 * API 信息：
 * - 端点：https://api.minimaxi.com/v1/image_generation
 * - 模型：image-01
 * - 认证：Bearer Token（sk-cp- 前缀）
 *
 * @author 未来姐姐
 * @date 2026-07-29
 */
public class MiniMaxImageGenerator {

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

    /** 限流：每分钟最大请求数 */
    private static final int RATE_LIMIT_PER_MINUTE = 10;

    /** 限流控制 */
    private final RateLimiter rateLimiter = new RateLimiter(RATE_LIMIT_PER_MINUTE, TimeUnit.MINUTES);

    /**
     * 图像生成结果
     */
    public static class ImageGenerationResult {
        public final boolean success;
        public final List<String> imagePaths;
        public final List<String> originalUrls;
        public final String promptUsed;
        public final String model;
        public final int width;
        public final int height;
        public final int n;
        public final long generationTimeMs;
        public final String errorMessage;

        private ImageGenerationResult(Builder builder) {
            this.success = builder.success;
            this.imagePaths = builder.imagePaths;
            this.originalUrls = builder.originalUrls;
            this.promptUsed = builder.promptUsed;
            this.model = builder.model;
            this.width = builder.width;
            this.height = builder.height;
            this.n = builder.n;
            this.generationTimeMs = builder.generationTimeMs;
            this.errorMessage = builder.errorMessage;
        }

        public static class Builder {
            private boolean success;
            private List<String> imagePaths = new ArrayList<>();
            private List<String> originalUrls = new ArrayList<>();
            private String promptUsed;
            private String model;
            private int width;
            private int height;
            private int n;
            private long generationTimeMs;
            private String errorMessage;

            public Builder success(boolean success) { this.success = success; return this; }
            public Builder imagePaths(List<String> v) { this.imagePaths = v; return this; }
            public Builder originalUrls(List<String> v) { this.originalUrls = v; return this; }
            public Builder promptUsed(String v) { this.promptUsed = v; return this; }
            public Builder model(String v) { this.model = v; return this; }
            public Builder width(int v) { this.width = v; return this; }
            public Builder height(int v) { this.height = v; return this; }
            public Builder n(int v) { this.n = v; return this; }
            public Builder generationTimeMs(long v) { this.generationTimeMs = v; return this; }
            public Builder errorMessage(String v) { this.errorMessage = v; return this; }

            public ImageGenerationResult build() {
                return new ImageGenerationResult(this);
            }
        }

        @Override
        public String toString() {
            if (!success) {
                return "ImageGenerationResult{失败: " + errorMessage + "}";
            }
            return String.format(
                "ImageGenerationResult{成功, %d 张图片, 尺寸=%dx%d, 耗时=%dms, 路径=%s}",
                imagePaths.size(), width, height, generationTimeMs, imagePaths
            );
        }
    }

    /**
     * 异常类型
     */
    public static class ImageGenerationException extends Exception {
        public enum ErrorType {
            INVALID_API_KEY,    // API Key 无效
            RATE_LIMITED,        // 触发限流
            INVALID_SIZE,        // 尺寸不合法
            NETWORK_ERROR,       // 网络错误
            SERVER_ERROR,        // 服务器错误
            INVALID_RESPONSE     // 响应格式错误
        }

        public final ErrorType errorType;
        public final int httpCode;

        public ImageGenerationException(ErrorType errorType, String message) {
            this(errorType, message, -1);
        }

        public ImageGenerationException(ErrorType errorType, String message, int httpCode) {
            super(message);
            this.errorType = errorType;
            this.httpCode = httpCode;
        }
    }

    /**
     * 生成参数
     */
    public static class GenerateParams {
        public String prompt;                  // 提示词（必填）
        public String apiKey;                  // API Key（必填）
        public int width = 1024;               // 宽度（默认 1024）
        public int height = 1024;              // 高度（默认 1024）
        public int n = 1;                       // 生成数量（默认 1）
        public boolean promptOptimizer = true; // 是否优化 prompt（默认 true）
        public String subjectReference;         // 参考图 base64（可选）
        public boolean saveToLocal = true;     // 是否保存到本地
        public String saveDir;                  // 保存目录（默认 ./）
        public int timeoutSec = 60;             // 超时秒数

        public GenerateParams(String prompt, String apiKey) {
            this.prompt = prompt;
            this.apiKey = apiKey;
        }
    }

    /**
     * 主入口：生成图片
     *
     * @param params 生成参数
     * @return 生成结果
     * @throws ImageGenerationException 生成失败
     */
    public ImageGenerationResult generate(GenerateParams params) throws ImageGenerationException {
        long startTime = System.currentTimeMillis();

        // 参数校验
        validateParams(params);

        // 限流等待
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.NETWORK_ERROR,
                "限流等待被中断: " + e.getMessage()
            );
        }

        // 构建请求体
        JSONObject requestBody = buildRequestBody(params);

        // 发送 HTTP 请求
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + params.apiKey);
            connection.setConnectTimeout(params.timeoutSec * 1000);
            connection.setReadTimeout(params.timeoutSec * 1000);
            connection.setDoOutput(true);

            // 写入请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();

            // 读取响应
            String responseBody = readResponse(connection, responseCode);

            // 处理 HTTP 错误
            if (responseCode != 200) {
                handleHttpError(responseCode, responseBody);
            }

            // 解析响应
            JSONObject jsonResponse = new JSONObject(responseBody);

            // 检查错误字段
            if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMsg = error.optString("message", "未知错误");
                throw new ImageGenerationException(
                    ImageGenerationException.ErrorType.SERVER_ERROR,
                    "API 返回错误: " + errorMsg,
                    responseCode
                );
            }

            // 解析图片 URL 列表
            JSONArray dataArray = jsonResponse.getJSONArray("data");
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.has("url")) {
                    urls.add(item.getString("url"));
                } else if (item.has("b64_json")) {
                    // 如果是 base64，转换为 URL 形式保存
                    urls.add("base64:" + item.getString("b64_json"));
                }
            }

            if (urls.isEmpty()) {
                throw new ImageGenerationException(
                    ImageGenerationException.ErrorType.INVALID_RESPONSE,
                    "响应中没有找到图片数据"
                );
            }

            // 下载图片到本地
            List<String> savedPaths = new ArrayList<>();
            if (params.saveToLocal) {
                for (int i = 0; i < urls.size(); i++) {
                    String urlOrB64 = urls.get(i);
                    String savedPath = saveImage(urlOrB64, params.saveDir, i);
                    savedPaths.add(savedPath);
                }
            }

            long elapsedMs = System.currentTimeMillis() - startTime;

            return new ImageGenerationResult.Builder()
                .success(true)
                .imagePaths(savedPaths)
                .originalUrls(urls)
                .promptUsed(params.prompt)
                .model(MODEL_NAME)
                .width(params.width)
                .height(params.height)
                .n(params.n)
                .generationTimeMs(elapsedMs)
                .build();

        } catch (IOException e) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.NETWORK_ERROR,
                "网络请求失败: " + e.getMessage(),
                e
            );
        } catch (ImageGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_RESPONSE,
                "解析响应失败: " + e.getMessage(),
                e
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 参数校验
     */
    private void validateParams(GenerateParams params) throws ImageGenerationException {
        if (params.prompt == null || params.prompt.trim().isEmpty()) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_RESPONSE,
                "prompt 不能为空"
            );
        }
        if (params.apiKey == null || params.apiKey.trim().isEmpty()) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_API_KEY,
                "apiKey 不能为空"
            );
        }
        if (params.width < MIN_SIZE || params.width > MAX_SIZE || params.width % SIZE_MULTIPLE != 0) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_SIZE,
                String.format("宽度必须在 %d-%d 之间且是 %d 的倍数，当前: %d",
                    MIN_SIZE, MAX_SIZE, SIZE_MULTIPLE, params.width)
            );
        }
        if (params.height < MIN_SIZE || params.height > MAX_SIZE || params.height % SIZE_MULTIPLE != 0) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_SIZE,
                String.format("高度必须在 %d-%d 之间且是 %d 的倍数，当前: %d",
                    MIN_SIZE, MAX_SIZE, SIZE_MULTIPLE, params.height)
            );
        }
        if (params.n < 1 || params.n > 9) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_RESPONSE,
                "n 必须在 1-9 之间，当前: " + params.n
            );
        }
    }

    /**
     * 构建请求体
     */
    private JSONObject buildRequestBody(GenerateParams params) {
        JSONObject body = new JSONObject();
        body.put("model", MODEL_NAME);
        body.put("prompt", params.prompt);
        body.put("n", params.n);
        body.put("width", params.width);
        body.put("height", params.height);
        body.put("prompt_optimizer", params.promptOptimizer);
        if (params.subjectReference != null && !params.subjectReference.isEmpty()) {
            body.put("subject_reference", params.subjectReference);
        }
        return body;
    }

    /**
     * 读取 HTTP 响应
     */
    private String readResponse(HttpURLConnection connection, int responseCode) throws IOException {
        InputStream inputStream;
        if (responseCode >= 200 && responseCode < 300) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    /**
     * 处理 HTTP 错误
     */
    private void handleHttpError(int code, String body) throws ImageGenerationException {
        String errorDetail = "";
        try {
            if (!body.isEmpty()) {
                JSONObject errorJson = new JSONObject(body);
                if (errorJson.has("error")) {
                    errorDetail = errorJson.getJSONObject("error").optString("message", "");
                }
            }
        } catch (Exception ignore) {}

        if (code == 401 || code == 403) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.INVALID_API_KEY,
                "API Key 无效或已过期: " + errorDetail,
                code
            );
        } else if (code == 429) {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.RATE_LIMITED,
                "触发限流，请稍后再试: " + errorDetail,
                code
            );
        } else {
            throw new ImageGenerationException(
                ImageGenerationException.ErrorType.SERVER_ERROR,
                "服务器错误 (HTTP " + code + "): " + errorDetail,
                code
            );
        }
    }

    /**
     * 保存图片到本地
     */
    private String saveImage(String urlOrB64, String saveDir, int index) throws IOException {
        String filename = String.format("minimax_image_%d_%d.png",
            System.currentTimeMillis(), index);
        String fullPath = (saveDir != null ? saveDir : "./") + filename;

        if (urlOrB64.startsWith("base64:")) {
            // base64 解码保存
            String b64Data = urlOrB64.substring("base64:".length());
            byte[] imageBytes = Base64.getDecoder().decode(b64Data);
            try (FileOutputStream fos = new FileOutputStream(fullPath)) {
                fos.write(imageBytes);
            }
        } else {
            // URL 下载
            URL imageUrl = new URL(urlOrB64);
            try (InputStream in = imageUrl.openStream();
                 FileOutputStream fos = new FileOutputStream(fullPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        return fullPath;
    }

    /**
     * 简单令牌桶限流器
     */
    private static class RateLimiter {
        private final int permitsPerPeriod;
        private final long periodNanos;
        private long nextResetTime;
        private int availablePermits;

        public RateLimiter(int permits, TimeUnit period) {
            this.permitsPerPeriod = permits;
            this.periodNanos = period.toNanos(1);
            this.availablePermits = permits;
            this.nextResetTime = System.nanoTime() + periodNanos;
        }

        public synchronized void acquire() throws InterruptedException {
            long now = System.nanoTime();
            if (now >= nextResetTime) {
                availablePermits = permitsPerPeriod;
                nextResetTime = now + periodNanos;
            }
            if (availablePermits <= 0) {
                long waitMs = (nextResetTime - now) / 1_000_000 + 100;
                Thread.sleep(waitMs);
                availablePermits = permitsPerPeriod;
                nextResetTime = System.nanoTime() + periodNanos;
            }
            availablePermits--;
        }
    }
}