package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.model.ObjectMetadata;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OssUploadTool implements Tool {
    private static final String TAG = "OssUploadTool";

    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OssUploadTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "ossUploadFile";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ossUploadFile");
            functionDef.put("description", "上传手机文件到阿里云 OSS。");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject localPathParam = new JSONObject();
            localPathParam.put("type", "string");
            localPathParam.put("description", "本地文件绝对路径");
            properties.put("localPath", localPathParam);

            JSONObject objectKeyParam = new JSONObject();
            objectKeyParam.put("type", "string");
            objectKeyParam.put("description", "OSS 对象 key");
            properties.put("objectKey", objectKeyParam);

            JSONObject bucketNameParam = new JSONObject();
            bucketNameParam.put("type", "string");
            bucketNameParam.put("description", "Bucket 名称");
            properties.put("bucketName", bucketNameParam);

            JSONObject accessKeyIdParam = new JSONObject();
            accessKeyIdParam.put("type", "string");
            accessKeyIdParam.put("description", "阿里云 AccessKey ID");
            properties.put("accessKeyId", accessKeyIdParam);

            JSONObject accessKeySecretParam = new JSONObject();
            accessKeySecretParam.put("type", "string");
            accessKeySecretParam.put("description", "阿里云 AccessKey Secret");
            properties.put("accessKeySecret", accessKeySecretParam);

            JSONObject endpointParam = new JSONObject();
            endpointParam.put("type", "string");
            endpointParam.put("description", "OSS Endpoint");
            properties.put("endpoint", endpointParam);

            JSONObject publicReadParam = new JSONObject();
            publicReadParam.put("type", "boolean");
            publicReadParam.put("default", false);
            publicReadParam.put("description", "是否公共读");
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

                String localPath = arguments.optString("localPath", null);
                if (localPath == null || localPath.trim().isEmpty()) {
                    throw new IllegalArgumentException("localPath 不能为空");
                }

                File localFile = new File(localPath);
                if (!localFile.exists()) {
                    throw new IOException("本地文件不存在: " + localPath);
                }

                String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null) {
                    throw new IllegalArgumentException("凭证不完整");
                }

                String objectKey = arguments.optString("objectKey", null);
                if (objectKey == null || objectKey.trim().isEmpty()) {
                    long timestamp = System.currentTimeMillis();
                    String originalName = localFile.getName();
                    objectKey = "sisterfuture/" + timestamp + "_" + originalName;
                }

                ClientConfiguration conf = new ClientConfiguration();
                conf.setConnectionTimeout(15 * 1000);
                conf.setSocketTimeout(15 * 1000);
                conf.setMaxConcurrentRequest(5);
                conf.setMaxErrorRetry(2);

                OSSCredentialProvider credentialProvider = new OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret);
                OSS oss = new OSSClient(context.getApplicationContext(), endpoint, credentialProvider, conf);

                boolean publicRead = arguments.optBoolean("publicRead", false);
                ObjectMetadata metadata = new ObjectMetadata();
                if (publicRead) {
                    metadata.setHeader("x-oss-object-acl", "public-read");
                }

                PutObjectRequest put = new PutObjectRequest(bucketName, objectKey, localPath);
                put.setMetadata(metadata);

                long uploadStart = System.currentTimeMillis();
                PutObjectResult result = oss.putObject(put);
                long uploadDuration = System.currentTimeMillis() - uploadStart;

                long oneHour = 3600;
                Date expiration = new Date(System.currentTimeMillis() + oneHour * 1000);
                String signedUrl = oss.presignConstrainedObjectURL(bucketName, objectKey, expiration).toString();

                long totalDuration = System.currentTimeMillis() - totalStartTime;

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

    private String getOrFromNote(JSONObject arguments, String paramName, String noteKey) {
        String value = arguments.optString(paramName, null);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return getValueFromNote(noteKey);
    }

    private String getValueFromNote(String key) {
        String note = getNote(context);
        if (note == null || note.isEmpty()) {
            return null;
        }
        String[] lines = note.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith(key + "=")) {
                String value = line.substring((key + "=").length()).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "ossUploadFile 工具说明：\n"
            + "1. 必传参数：localPath\n"
            + "2. 可选参数：objectKey、bucketName、accessKeyId、accessKeySecret、endpoint、publicRead\n"
            + "3. 凭证优先级：参数传入 > 工具备注\n"
            + "4. 默认 objectKey：sisterfuture/<时间戳>_<原始文件名>";
    }
}