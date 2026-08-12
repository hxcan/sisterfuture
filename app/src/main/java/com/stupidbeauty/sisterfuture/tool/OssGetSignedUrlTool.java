package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.alibaba.cloud.oss.OSS;
import com.alibaba.cloud.oss.OSSClientBuilder;
import com.alibaba.cloud.oss.common.auth.CredentialsProvider;
import com.alibaba.cloud.oss.common.auth.DefaultCredentialProvider;
import com.alibaba.cloud.oss.model.GeneratePresignedUrlRequest;
import com.alibaba.cloud.oss.model.ObjectListing;
import com.alibaba.cloud.oss.model.OSSObjectSummary;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 签名 URL 工具
 *
 * 生成 OSS 对象的临时访问签名 URL（带过期时间）。
 * 可用于：
 * - 跨设备分享文件（家里电脑生成 URL → 手机下载）
 * - 临时给第三方访问私有文件
 * - 嵌入到 HTML 中展示图片
 *
 * 凭证配置：同 OssUploadTool，从工具备注自动读取
 *
 * @author 未来姐姐
 * @date 2026-08-12
 */
public class OssGetSignedUrlTool implements Tool {
    private static final String TAG = "OssGetSignedUrlTool";

    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OssGetSignedUrlTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ossGetSignedUrl";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ossGetSignedUrl");
            functionDef.put("description", "为 OSS 对象生成临时签名 URL。\n"
                    + "签名 URL 在指定过期时间内可直接访问，无需 AccessKey。\n"
                    + "典型场景：把家里电脑上传的文件 URL 发给手机，手机直接下载。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject objectKeyParam = new JSONObject();
            objectKeyParam.put("type", "string");
            objectKeyParam.put("description", "OSS 对象 key（必填）");
            properties.put("objectKey", objectKeyParam);

            JSONObject expiresInSecondsParam = new JSONObject();
            expiresInSecondsParam.put("type", "integer");
            expiresInSecondsParam.put("default", 3600);
            expiresInSecondsParam.put("description", "URL 有效期（秒），默认 1 小时，最大 7 天（604800 秒）");
            properties.put("expiresInSeconds", expiresInSecondsParam);

            JSONObject bucketNameParam = new JSONObject();
            bucketNameParam.put("type", "string");
            bucketNameParam.put("description", "Bucket 名称");
            properties.put("bucketName", bucketNameParam);

            JSONObject accessKeyIdParam = new JSONObject();
            accessKeyIdParam.put("type", "string");
            accessKeyIdParam.put("description", "AccessKey ID");
            properties.put("accessKeyId", accessKeyIdParam);

            JSONObject accessKeySecretParam = new JSONObject();
            accessKeySecretParam.put("type", "string");
            accessKeySecretParam.put("description", "AccessKey Secret");
            properties.put("accessKeySecret", accessKeySecretParam);

            JSONObject endpointParam = new JSONObject();
            endpointParam.put("type", "string");
            endpointParam.put("description", "OSS Endpoint");
            properties.put("endpoint", endpointParam);

            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
            required.put("objectKey");
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
                FileLogger.i(TAG, "========== ossGetSignedUrl 开始 ==========");

                String objectKey = arguments.optString("objectKey", null);
                if (objectKey == null || objectKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("objectKey 不能为空");
                }

                int expiresInSeconds = arguments.optInt("expiresInSeconds", 3600);
                if (expiresInSeconds <= 0 || expiresInSeconds > 604800) {
                    throw new IllegalArgumentException("expiresInSeconds 必须在 1-604800 之间（最大 7 天）");
                }

                String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null) {
                    throw new IllegalArgumentException("凭证不完整。可通过参数传入或在工具备注中设置");
                }

                CredentialsProvider provider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
                OSS oss = new OSSClientBuilder().build(endpoint, provider);

                Date expiration = new Date(System.currentTimeMillis() + expiresInSeconds * 1000L);

                GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey);
                request.setExpiration(expiration);
                request.setMethod(com.alibaba.cloud.oss.model.HttpMethod.GET);

                String signedUrl = oss.generatePresignedUrl(request).toString();

                FileLogger.i(TAG, "✅ 签名 URL 生成 - objectKey: " + objectKey + ", 有效期: " + expiresInSeconds + "秒");

                JSONObject output = new JSONObject();
                output.put("status", "success");
                output.put("objectKey", objectKey);
                output.put("signedUrl", signedUrl);
                output.put("expiresAt", expiration.getTime() / 1000); // Unix timestamp in seconds
                output.put("expiresInSeconds", expiresInSeconds);

                callback.onResult(output);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ ossGetSignedUrl 出错", e);
                callback.onError(e);
            }
        });
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
        return "ossGetSignedUrl 工具说明：\n"
            + "1. 必传参数：objectKey\n"
            + "2. 可选参数：expiresInSeconds（默认 3600 = 1 小时）\n"
            + "3. 返回：signedUrl（可直接下载/访问）、expiresAt（Unix 时间戳）\n"
            + "4. 典型用法：手机上传 PDF → 调用 ossGetSignedUrl 生成 URL → 把 URL 发给家里电脑 → 家里电脑用 wget/curl 下载";
    }
}