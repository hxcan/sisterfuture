package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.stupidbeauty.sisterfuture.manager.OssManager;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OssUploadTool implements Tool {
    private static final String TAG = "OssUploadTool";

    private final OssManager ossManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OssUploadTool(Context context) {
        this.ossManager = new OssManager(context);
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

                String objectKey = arguments.optString("objectKey", null);
                if (objectKey == null || objectKey.trim().isEmpty()) {
                    long timestamp = System.currentTimeMillis();
                    String originalName = localFile.getName();
                    objectKey = "sisterfuture/" + timestamp + "_" + originalName;
                }

                boolean publicRead = arguments.optBoolean("publicRead", false);
                JSONObject output = ossManager.uploadFile(localFile, objectKey, publicRead,
                    OssManager.DEFAULT_URL_EXPIRY_SECONDS, arguments);
                long totalDuration = System.currentTimeMillis() - totalStartTime;
                output.put("totalDurationMs", totalDuration);

                JSONObject attachment = new JSONObject();
                attachment.put("type", "oss");
                attachment.put("objectKey", output.getString("objectKey"));
                attachment.put("url", output.getString("signedUrl"));
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

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "ossUploadFile 工具说明：\n"
            + "1. 必传参数：localPath\n"
            + "2. 可选参数：objectKey、bucketName、accessKeyId、accessKeySecret、endpoint、publicRead\n"
            + "3. 凭证优先级：参数传入 > 工具备注\n"
            + "4. 默认 objectKey：sisterfuture/<时间戳>_<原始文件名>";
    }
}
