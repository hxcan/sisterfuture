package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 阿里云 OSS 文件列表工具
 *
 * 列出 OSS bucket 中的文件。
 *
 * 凭证配置：同 OssUploadTool，从工具备注自动读取
 *
 * @author 未来姐姐
 * @date 2026-08-12
 */
public class OssListFilesTool implements Tool {
    private static final String TAG = "OssListFilesTool";

    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat;

    public OssListFilesTool(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public String getName() {
        return "ossListFiles";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "ossListFiles");
            functionDef.put("description", "列出 OSS bucket 中的文件。支持按 prefix 前缀过滤，分页查询。\n"
                    + "返回：文件列表（包含 objectKey, size, lastModified, etag）。\n"
                    + "凭证：优先从参数传入，其次从工具备注读取。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();

            JSONObject prefixParam = new JSONObject();
            prefixParam.put("type", "string");
            prefixParam.put("description", "文件名前缀过滤（如 sisterfuture/）。省略则列出所有文件");
            properties.put("prefix", prefixParam);

            JSONObject maxKeysParam = new JSONObject();
            maxKeysParam.put("type", "integer");
            maxKeysParam.put("default", 100);
            maxKeysParam.put("description", "最多返回文件数（默认 100，最大 1000）");
            properties.put("maxKeys", maxKeysParam);

            JSONObject markerParam = new JSONObject();
            markerParam.put("type", "string");
            markerParam.put("description", "分页标记（下一页的起始位置，从上一次返回的 nextMarker 获取）");
            properties.put("marker", markerParam);

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
                FileLogger.i(TAG, "========== ossListFiles 开始 ==========");

                String accessKeyId = getOrFromNote(arguments, "accessKeyId", NOTE_KEY_ACCESS_KEY_ID);
                String accessKeySecret = getOrFromNote(arguments, "accessKeySecret", NOTE_KEY_ACCESS_KEY_SECRET);
                String bucketName = getOrFromNote(arguments, "bucketName", NOTE_KEY_BUCKET_NAME);
                String endpoint = getOrFromNote(arguments, "endpoint", NOTE_KEY_ENDPOINT);

                if (accessKeyId == null || accessKeySecret == null || bucketName == null || endpoint == null) {
                    throw new IllegalArgumentException("凭证不完整。可通过参数传入或在工具备注中设置");
                }

                String prefix = arguments.optString("prefix", null);
                int maxKeys = Math.min(arguments.optInt("maxKeys", 100), 1000);
                String marker = arguments.optString("marker", null);

                CredentialsProvider provider = new DefaultCredentialProvider(accessKeyId, accessKeySecret);
                OSS oss = new OSSClientBuilder().build(endpoint, provider);

                ListObjectsRequest request = new ListObjectsRequest(bucketName);
                if (prefix != null && !prefix.isEmpty()) {
                    request.setPrefix(prefix);
                }
                request.setMaxKeys(maxKeys);
                if (marker != null && !marker.isEmpty()) {
                    request.setMarker(marker);
                }

                ObjectListing listing = oss.listObjects(request);

                JSONArray filesArray = new JSONArray();
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    JSONObject file = new JSONObject();
                    file.put("objectKey", summary.getKey());
                    file.put("size", summary.getSize());
                    file.put("etag", summary.getETag());
                    file.put("lastModified", dateFormat.format(summary.getLastModified()));
                    file.put("lastModifiedTimestamp", summary.getLastModified().getTime());
                    file.put("storageClass", summary.getStorageClass());
                    filesArray.put(file);
                }

                JSONObject output = new JSONObject();
                output.put("status", "success");
                output.put("bucketName", bucketName);
                output.put("prefix", prefix != null ? prefix : "");
                output.put("fileCount", filesArray.length());
                output.put("files", filesArray);
                output.put("isTruncated", listing.isTruncated());
                output.put("nextMarker", listing.getNextMarker());

                FileLogger.i(TAG, "✅ 列出 " + filesArray.length() + " 个文件 (truncated=" + listing.isTruncated() + ")");
                callback.onResult(output);

            } catch (Exception e) {
                FileLogger.e(TAG, "❌ ossListFiles 出错", e);
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
        return "ossListFiles 工具说明：\n"
            + "1. 可选参数：prefix（按前缀过滤）、maxKeys（默认 100）、marker（分页）\n"
            + "2. 返回：files 数组（含 objectKey、size、lastModified、etag）、isTruncated、nextMarker\n"
            + "3. 典型用法：手机上传后用 ossListFiles 查看有哪些文件 → 找到要下载的 objectKey → ossDownloadFile";
    }
}