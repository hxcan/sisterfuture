package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.model.GeneratePresignedUrlRequest;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 签名 URL 生成工具
 *
 * 不直接依赖 OssUploadTool，独立管理 OSS 客户端。
 *
 * @author 未来姐姐
 * @date 2026-08-13
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
            functionDef.put("description", "生成阿里云 OSS 对象的临时签名 URL（用于跨设备访问）。\n"
                    + "凭证：优先从参数传入，其次从工具备注读取。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject objectKeyParam = new JSONObject();
            objectKeyParam.put("type", "string");
            objectKeyParam.put("description", "OSS 对象 key");
            properties.put("objectKey", objectKeyParam);

            JSONObject expiresInSecondsParam = new JSONObject();
            expiresInSecondsParam.put("type", "integer");
            expiresInSecondsParam.put("description", "URL 有效期（秒），默认 3600（1 小时）");
            properties.put("expiresInSeconds", expiresInSecondsParam);

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

                String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null) {
                    throw new IllegalArgumentException("凭证不完整");
                }

                ClientConfiguration conf = new ClientConfiguration();
                conf.setConnectionTimeout(15 * 1000);
                conf.setSocketTimeout(15 * 1000);

                OSSCredentialProvider provider = new OSSPlainTextAKSKCredentialProvider(accessKeyId, accessKeySecret);
                OSS oss = new OSSClient(context.getApplicationContext(), endpoint, provider, conf);
String signedUrl = oss.presignConstrainedObjectURL(bucketName, objectKey, expiresInSeconds).toString();

                FileLogger.i(TAG, "✅ 生成签名 URL: " + signedUrl);

                JSONObject output = new JSONObject();
                output.put("status", "success");
                output.put("objectKey", objectKey);
                output.put("bucketName", bucketName);
                output.put("endpoint", endpoint);
                output.put("signedUrl", signedUrl);
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
        String[] lines = note.split("\n");
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
            + "2. 可选参数：expiresInSeconds（默认 3600）、bucketName、accessKeyId、accessKeySecret、endpoint\n"
            + "3. 凭证优先级：参数传入 > 工具备注\n"
            + "4. 默认有效期：3600 秒（1 小时）\n"
            + "5. 返回：signedUrl、expiresInSeconds\n"
            + "6. 典型场景：手机上传文件后，调用此工具生成临时 URL 给其他设备下载";
    }
}