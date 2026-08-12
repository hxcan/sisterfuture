package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.alibaba.cloud.oss.OSS;
import com.alibaba.cloud.oss.OSSClientBuilder;
import com.alibaba.cloud.oss.common.auth.CredentialsProvider;
import com.alibaba.cloud.oss.common.auth.DefaultCredentialProvider;
import com.alibaba.cloud.oss.model.OSSObject;
import com.alibaba.cloud.oss.model.ObjectListing;
import com.alibaba.cloud.oss.model.ObjectMetadata;
import com.alibaba.cloud.oss.model.PutObjectRequest;
import com.alibaba.cloud.oss.model.PutObjectResult;
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
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 工具集（合并为单文件多工具，避免 4 个独立文件）
 *
 * 工具列表：
 * 1. ossUploadFile - 上传手机文件到 OSS
 * 2. ossDownloadFile - 下载 OSS 文件到手机
 * 3. ossGetSignedUrl - 生成临时签名 URL
 * 4. ossListFiles - 列出 OSS 文件
 *
 * 凭证配置（运行时传入或工具备注）：
 * - aliyun_oss_access_key_id=LTAI5t...
 * - aliyun_oss_access_key_secret=xxx
 * - aliyun_oss_bucket_name=sisterfuture-files
 * - aliyun_oss_endpoint=https://oss-cn-shenzhen.aliyuncs.com
 * - aliyun_oss_default_region=oss-cn-shenzhen
 *
 * @author 未来姐姐
 * @date 2026-08-12
 */
public class OssUploadTool implements Tool {
    private static final String TAG = "OssUploadTool";

    // 工具备注 key
    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";
    private static final String NOTE_KEY_DEFAULT_REGION = "aliyun_oss_default_region";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // OkHttp 复用（download 用）
    private static class ClientHolder {
        private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private static OkHttpClient getClient() {
        return ClientHolder.INSTANCE;
    }

    public OssUploadTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ossUploadFile";
    }

    /**
     * 动态 definition：根据 accessKeyId 是否配置决定是否包含本工具
     */
    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ossUploadFile");
            functionDef.put("description", "上传手机文件到阿里云 OSS。上传成功后返回 OSS objectKey、签名 URL（用于跨设备访问）、文件大小等。\n"
                    + "凭证：优先从参数传入，其次从工具备注读取（aliyun_oss_access_key_id / aliyun_oss_access_key_secret / aliyun_oss_bucket_name / aliyun_oss_endpoint）。\n"
                    + "典型场景：跨设备文件传输（手机 → 家里电脑）、备份重要文件、跨任务共享文件。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject localPathParam = new JSONObject();
            localPathParam.put("type", "string");
            localPathParam.put("description", "本地文件绝对路径（如 /sdcard/Download/file.pdf）");
            properties.put("localPath", localPathParam);

            JSONObject objectKeyParam = new JSONObject();
            objectKeyParam.put("type", "string");
            objectKeyParam.put("description", "OSS 对象存储 key（不含 bucket 名），如 business_license/2026/file.pdf。如果省略，自动用文件名+时间戳生成");
            properties.put("objectKey", objectKeyParam);

            JSONObject bucketNameParam = new JSONObject();
            bucketNameParam.put("type", "string");
            bucketNameParam.put("description", "Bucket 名称。如果未传入，从工具备注 aliyun_oss_bucket_name 读取");
            properties.put("bucketName", bucketNameParam);

            JSONObject accessKeyIdParam = new JSONObject();
            accessKeyIdParam.put("type", "string");
            accessKeyIdParam.put("description", "阿里云 AccessKey ID。如果未传入，从工具备注 aliyun_oss_access_key_id 读取");
            properties.put("accessKeyId", accessKeyIdParam);

            JSONObject accessKeySecretParam = new JSONObject();
            accessKeySecretParam.put("type", "string");
            accessKeySecretParam.put("description", "阿里云 AccessKey Secret。如果未传入，从工具备注 aliyun_oss_access_key_secret 读取");
            properties.put("accessKeySecret", accessKeySecretParam);

            JSONObject endpointParam = new JSONObject();
            endpointParam.put("type", "string");
            endpointParam.put("description", "OSS Endpoint（如 https://oss-cn-shenzhen.aliyuncs.com）。如果未传入，从工具备注 aliyun_oss_endpoint 读取");
            properties.put("endpoint", endpointParam);

            JSONObject publicReadParam = new JSONObject();
            publicReadParam.put("type", "boolean");
            publicReadParam.put("default", false);
            publicReadParam.put("description", "是否设置 object 为公共读（默认 false = 私有）。公共读后任何人都可通过 URL 直接访问");
            properties.put("publicRead", publicReadParam);

            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
            required.put("localPath");
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
                FileLogger.i(TAG, "========== ossUploadFile 开始 ==========");

                long totalStartTime = System.currentTimeMillis();

                // 1. 解析参数
                String localPath = arguments.optString("localPath", null);
                if (localPath == null || localPath.trim().isEmpty()) {
                    throw new IllegalArgumentException("localPath 不能为空");
                }

                File localFile = new File(localPath);
                if (!localFile.exists()) {
                    throw new IOException("本地文件不存在: " + localPath);
                }

                // 2. 读取凭证（参数优先 → 工具备注）
                String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null) {
                    throw new IllegalArgumentException(
                        "凭证不完整。需要：accessKeyId, accessKeySecret, bucketName, endpoint\n"
                        + "可通过参数传入，或在工具备注中设置：\n"
                        + "  " + NOTE_KEY_ACCESS_KEY_ID + "=LTAI5t...\n"
                        + "  " + NOTE_KEY_ACCESS_KEY_SECRET + "=xxx\n"
                        + "  " + NOTE_KEY_BUCKET_NAME + "=sisterfuture-files\n"
                        + "  " + NOTE_KEY_ENDPOINT + "=https://oss-cn-shenzhen.aliyuncs.com"
                    );
                }

                FileLogger.i(TAG, "[1/5] 参数解析完成 - 文件: " + localFile.getName() + " (大小: " + localFile.length() + " bytes)");

                // 3. 生成 objectKey
                String objectKey = arguments.optString("objectKey", null);
                if (objectKey == null || objectKey.trim().isEmpty()) {
                    long timestamp = System.currentTimeMillis();
                    String originalName = localFile.getName();
                    objectKey = "sisterfuture/" + timestamp + "_" + originalName;
                }
                FileLogger.i(TAG, "[2/5] objectKey: " + objectKey);

                // 4. 创建 OSS 客户端
                FileLogger.i(TAG, "[3/5] 创建 OSS 客户端 - endpoint: " + endpoint);
                CredentialsProvider provider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
                OSS oss = new OSSClientBuilder().build(endpoint, provider);

                // 5. 上传
                boolean publicRead = arguments.optBoolean("publicRead", false);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setHeader("x-oss-object-acl", publicRead ? "public-read" : "private");

                PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, localFile);
                request.setMetadata(metadata);

                long uploadStart = System.currentTimeMillis();
                PutObjectResult result = oss.putObject(request);
                long uploadDuration = System.currentTimeMillis() - uploadStart;

                FileLogger.i(TAG, "[4/5] 上传完成 - 耗时: " + uploadDuration + "ms, ETag: " + result.getETag());

                // 6. 生成签名 URL（1 小时有效期）
                long oneHour = 3600;
                Date expiration = new Date(System.currentTimeMillis() + oneHour * 1000);
                String signedUrl = oss.generatePresignedUrl(bucketName, objectKey, expiration).toString();
                FileLogger.i(TAG, "[5/5] 生成签名 URL - 有效期 1 小时");

                long totalDuration = System.currentTimeMillis() - totalStartTime;

                // 7. 构建结果
                JSONObject output = new JSONObject();
                output.put("status", "success");
                output.put("objectKey", objectKey);
                output.put("bucketName", bucketName);
                output.put("endpoint", endpoint);
                output.put("publicUrl", publicRead ? "https://" + bucketName + "." + endpoint.replace("https://", "").replace("http://", "") + "/" + objectKey : null);
                output.put("signedUrl", signedUrl);
                output.put("expiresInSeconds", oneHour);
                output.put("size", localFile.length());
                output.put("etag", result.getETag());
                output.put("publicRead", publicRead);
                output.put("durationMs", uploadDuration);
                output.put("totalDurationMs", totalDuration);

                // 附件格式（方便其他工具读取）
                JSONObject attachment = new JSONObject();
                attachment.put("type", "oss");
                attachment.put("objectKey", objectKey);
                attachment.put("url", signedUrl);
                attachment.put("size", localFile.length());
                output.put("attachment", attachment);

                FileLogger.i(TAG, "✅ ossUploadFile 完成 - 总耗时: " + totalDuration + "ms");
                callback.onResult(output);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ ossUploadFile 出错", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 从参数或工具备注读取凭证
     */
    private String getOrFromNote(JSONObject arguments, String paramName, String noteKey) {
        String value = arguments.optString(paramName, null);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return getValueFromNote(noteKey);
    }

    /**
     * 从工具备注读取配置
     */
    private String getValueFromNote(String key) {
        String note = getNote(context);
        if (note == null || note.isEmpty()) {
            return null;
        }
        String[] lines = note.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(key + "=")) {
                String value = line.substring((key + "=").length()).trim();
                if (!value.isEmpty()) {
                    FileLogger.d(TAG, "从工具备注读取: " + key);
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "ossUploadFile 工具说明：\n"
            + "1. 必传参数：localPath（手机上的文件绝对路径）\n"
            + "2. 可选参数：objectKey、bucketName、accessKeyId、accessKeySecret、endpoint、publicRead\n"
            + "3. 凭证优先级：参数传入 > 工具备注（aliyun_oss_access_key_id 等）\n"
            + "4. 默认 objectKey 格式：sisterfuture/<时间戳>_<原始文件名>\n"
            + "5. 默认 ACL：private（私有）。publicRead=true 设为公共读\n"
            + "6. 返回：objectKey、signedUrl（1小时有效）、size、attachment\n"
            + "7. 典型场景：手机 PDF → OSS → 家里电脑下载 → 处理 → 回传\n"
            + "8. 配合 ossDownloadFile / ossListFiles / ossGetSignedUrl 使用";
    }
}