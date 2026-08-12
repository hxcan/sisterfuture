package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.alibaba.cloud.oss.OSS;
import com.alibaba.cloud.oss.OSSClientBuilder;
import com.alibaba.cloud.oss.common.auth.CredentialsProvider;
import com.alibaba.cloud.oss.common.auth.DefaultCredentialProvider;
import com.alibaba.cloud.oss.model.OSSObject;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 下载工具
 *
 * 从 OSS 下载文件到手机本地存储。
 *
 * 凭证配置：同 OssUploadTool，从工具备注自动读取
 *
 * @author 未来姐姐
 * @date 2026-08-12
 */
public class OssDownloadTool implements Tool {
    private static final String TAG = "OssDownloadTool";

    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OssDownloadTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ossDownloadFile";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ossDownloadFile");
            functionDef.put("description", "从阿里云 OSS 下载文件到手机本地存储。支持两种模式：\n"
                    + "模式 1（直接下载）：通过 objectKey + bucketName 下载（需要 AccessKey）\n"
                    + "模式 2（通过签名 URL）：通过 signedUrl 下载（无需 AccessKey，用于公开访问或临时签名）\n"
                    + "凭证：优先从参数传入，其次从工具备注读取。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject objectKeyParam = new JSONObject();
            objectKeyParam.put("type", "string");
            objectKeyParam.put("description", "OSS 对象 key。如果使用签名 URL 模式，可省略");
            properties.put("objectKey", objectKeyParam);

            JSONObject signedUrlParam = new JSONObject();
            signedUrlParam.put("type", "string");
            signedUrlParam.put("description", "签名 URL（用于直接下载公开访问的文件，无需 AccessKey）");
            properties.put("signedUrl", signedUrlParam);

            JSONObject savePathParam = new JSONObject();
            savePathParam.put("type", "string");
            savePathParam.put("description", "本地保存路径（默认 /sdcard/Download/<objectKey 文件名>）");
            properties.put("savePath", savePathParam);

            JSONObject bucketNameParam = new JSONObject();
            bucketNameParam.put("type", "string");
            bucketNameParam.put("description", "Bucket 名称（objectKey 模式必填）");
            properties.put("bucketName", bucketNameParam);

            JSONObject accessKeyIdParam = new JSONObject();
            accessKeyIdParam.put("type", "string");
            accessKeyIdParam.put("description", "AccessKey ID（objectKey 模式必填）");
            properties.put("accessKeyId", accessKeyIdParam);

            JSONObject accessKeySecretParam = new JSONObject();
            accessKeySecretParam.put("type", "string");
            accessKeySecretParam.put("description", "AccessKey Secret（objectKey 模式必填）");
            properties.put("accessKeySecret", accessKeySecretParam);

            JSONObject endpointParam = new JSONObject();
            endpointParam.put("type", "string");
            endpointParam.put("description", "OSS Endpoint（objectKey 模式必填）");
            properties.put("endpoint", endpointParam);

            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
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
                FileLogger.i(TAG, "========== ossDownloadFile 开始 ==========");

                long totalStartTime = System.currentTimeMillis();

                String signedUrl = arguments.optString("signedUrl", null);
                String objectKey = arguments.optString("objectKey", null);
                String savePath = arguments.optString("savePath", null);

                FileLogger.i(TAG, "[1/5] 解析参数 - 模式: " + (signedUrl != null ? "URL" : "objectKey"));

                // 确定保存路径
                if (savePath == null || savePath.trim().isEmpty()) {
                    if (objectKey != null) {
                        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
                        savePath = "/sdcard/Download/" + fileName;
                    } else if (signedUrl != null) {
                        // 从 URL 中提取文件名
                        String path = signedUrl.split("\\?")[0];
                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                        savePath = "/sdcard/Download/" + fileName;
                    } else {
                        throw new IllegalArgumentException("必须提供 objectKey 或 signedUrl 之一");
                    }
                }

                File targetFile = new File(savePath);
                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                long downloadStart = System.currentTimeMillis();
                long size;

                if (signedUrl != null && !signedUrl.trim().isEmpty()) {
                    // 模式 2：通过签名 URL 下载
                    FileLogger.i(TAG, "[2/5] 通过签名 URL 下载: " + signedUrl);
                    size = downloadViaUrl(signedUrl, targetFile);
                } else {
                    // 模式 1：通过 objectKey + AccessKey 下载
                    String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                    String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                    String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                    String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                    if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null || objectKey == null) {
                        throw new IllegalArgumentException(
                            "objectKey 模式需要：objectKey, bucketName, accessKeyId, accessKeySecret, endpoint\n"
                            + "或者使用 signedUrl 模式直接下载"
                        );
                    }

                    FileLogger.i(TAG, "[2/5] 通过 objectKey 下载 - bucket: " + bucketName + ", key: " + objectKey);
                    size = downloadViaOss(endpoint, accessKeyId, accessKeySecret, bucketName, objectKey, targetFile);
                }

                long downloadDuration = System.currentTimeMillis() - downloadStart;
                FileLogger.i(TAG, "[3/5] 下载完成 - 大小: " + size + " bytes, 耗时: " + downloadDuration + "ms");
                FileLogger.i(TAG, "[4/5] 保存到: " + targetFile.getAbsolutePath());

                // 扫描到相册/媒体库
                scanToMediaStore(targetFile);

                long totalDuration = System.currentTimeMillis() - totalStartTime;

                JSONObject output = new JSONObject();
                output.put("status", "success");
                output.put("savedPath", targetFile.getAbsolutePath());
                output.put("size", size);
                output.put("durationMs", downloadDuration);
                output.put("totalDurationMs", totalDuration);
                if (objectKey != null) output.put("objectKey", objectKey);

                FileLogger.i(TAG, "✅ ossDownloadFile 完成 - 总耗时: " + totalDuration + "ms");
                callback.onResult(output);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ ossDownloadFile 出错", e);
                callback.onError(e);
            }
        });
    }

    private long downloadViaUrl(String url, File targetFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("响应体为空");
            return writeToFile(body.byteStream(), targetFile);
        }
    }

    private long downloadViaOss(String endpoint, String accessKeyId, String accessKeySecret,
                                 String bucketName, String objectKey, File targetFile) throws IOException {
        CredentialsProvider provider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
        OSS oss = new OSSClientBuilder().build(endpoint, provider);

        OSSObject ossObject = oss.getObject(bucketName, objectKey);
        try (InputStream in = ossObject.getObjectContent()) {
            return writeToFile(in, targetFile);
        }
    }

    private long writeToFile(InputStream in, File targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            return totalBytes;
        }
    }

    private void scanToMediaStore(File file) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                null,
                null
            );
            FileLogger.d(TAG, "[5/5] 已扫描到媒体库");
        } catch (Exception e) {
            FileLogger.w(TAG, "扫描到媒体库失败: " + e.getMessage());
        }
    }

    private static OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private String getOrFromNote(JSONObject arguments, String paramName, String noteKey) {
        String value = arguments.optString(paramName, null);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return getValueFromNote(noteKey);
    }

    private String getValueFromNote(String key) {
        String note = getNote(context);
        if (note == null || note.isEmpty()) return null;
        String[] lines = note.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(key + "=")) {
                String value = line.substring((key + "=").length()).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "ossDownloadFile 工具说明：\n"
            + "1. 必传参数：objectKey 或 signedUrl（二选一）\n"
            + "2. 可选参数：savePath（默认 /sdcard/Download/<文件名>）\n"
            + "3. objectKey 模式：需要 bucketName、accessKeyId、accessKeySecret、endpoint\n"
            + "4. signedUrl 模式：直接下载，无需凭证（适合跨设备传输）\n"
            + "5. 下载完成后自动扫描到系统媒体库\n"
            + "6. 返回：savedPath、size、durationMs\n"
            + "7. 典型场景：家里电脑上传 → 拿到 signedUrl → 手机下载";
    }
}