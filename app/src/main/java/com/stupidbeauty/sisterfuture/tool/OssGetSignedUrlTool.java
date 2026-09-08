package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.manager.OssManager;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 签名 URL 生成工具
 *
 * 与 OssUploadTool 共用 OssManager，保持凭证与客户端配置一致。
 *
 * @author 未来姐姐
 * @date 2026-08-13
 */
public class OssGetSignedUrlTool implements Tool {
    private static final String TAG = "OssGetSignedUrlTool";

    private final OssManager ossManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OssGetSignedUrlTool(Context context) {
        this.ossManager = new OssManager(context);
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

                JSONObject output = ossManager.createSignedUrlResult(objectKey, expiresInSeconds, arguments);
                String signedUrl = output.getString("signedUrl");

                FileLogger.i(TAG, "✅ 生成签名 URL: " + signedUrl);

                output.put("status", "success");
                output.put("signedUrl", signedUrl);

                callback.onResult(output);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ ossGetSignedUrl 出错", e);
                callback.onError(e);
            }
        });
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
