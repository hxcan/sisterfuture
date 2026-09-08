package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.model.ObjectMetadata;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared OSS upload and signed-URL support for tools and chat media. */
public class OssManager {
    public static final int DEFAULT_URL_EXPIRY_SECONDS = 24 * 60 * 60;
    private static final long REFRESH_MARGIN_MS = 5 * 60 * 1000L;
    private static final String PREFS_NAME = "tool_enhancements";
    private static final String UPLOAD_TOOL_NOTE = "note_ossUploadFile";
    private static final String SIGNED_URL_TOOL_NOTE = "note_ossGetSignedUrl";
    private static final String NOTE_KEY_ACCESS_KEY_ID = "aliyun_oss_access_key_id";
    private static final String NOTE_KEY_ACCESS_KEY_SECRET = "aliyun_oss_access_key_secret";
    private static final String NOTE_KEY_BUCKET_NAME = "aliyun_oss_bucket_name";
    private static final String NOTE_KEY_ENDPOINT = "aliyun_oss_endpoint";

    private final Context context;

    public OssManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public JSONObject uploadFile(File localFile, String objectKey, boolean publicRead,
                                 int expiresInSeconds, JSONObject overrides) throws Exception {
        if (localFile == null || !localFile.isFile()) {
            throw new IOException("本地文件不存在: " + (localFile == null ? "null" : localFile.getAbsolutePath()));
        }

        Config config = loadConfig(overrides);
        OSS oss = createClient(config);
        String resolvedObjectKey = objectKey;
        if (resolvedObjectKey == null || resolvedObjectKey.trim().isEmpty()) {
            resolvedObjectKey = "sisterfuture/" + System.currentTimeMillis() + "_" + localFile.getName();
        }

        ObjectMetadata metadata = new ObjectMetadata();
        String contentType = URLConnection.guessContentTypeFromName(localFile.getName());
        if (contentType != null) metadata.setContentType(contentType);
        if (publicRead) metadata.setHeader("x-oss-object-acl", "public-read");

        PutObjectRequest request = new PutObjectRequest(config.bucketName, resolvedObjectKey,
            localFile.getAbsolutePath());
        request.setMetadata(metadata);
        long uploadStart = System.currentTimeMillis();
        PutObjectResult uploadResult = oss.putObject(request);
        long uploadDuration = System.currentTimeMillis() - uploadStart;

        int safeExpiry = expiresInSeconds > 0 ? expiresInSeconds : DEFAULT_URL_EXPIRY_SECONDS;
        String signedUrl = oss.presignConstrainedObjectURL(
            config.bucketName, resolvedObjectKey, safeExpiry).toString();
        long expiresAt = System.currentTimeMillis() + safeExpiry * 1000L;

        JSONObject output = new JSONObject();
        output.put("status", "success");
        output.put("objectKey", resolvedObjectKey);
        output.put("bucketName", config.bucketName);
        output.put("endpoint", config.endpoint);
        output.put("publicUrl", publicRead ? buildPublicUrl(config, resolvedObjectKey) : JSONObject.NULL);
        output.put("signedUrl", signedUrl);
        output.put("expiresInSeconds", safeExpiry);
        output.put("expiresAt", expiresAt);
        output.put("size", localFile.length());
        output.put("etag", uploadResult.getETag());
        output.put("publicRead", publicRead);
        output.put("durationMs", uploadDuration);
        return output;
    }

    public String createSignedUrl(String objectKey, int expiresInSeconds, JSONObject overrides) throws Exception {
        return createSignedUrlResult(objectKey, expiresInSeconds, overrides).getString("signedUrl");
    }

    public JSONObject createSignedUrlResult(String objectKey, int expiresInSeconds,
                                            JSONObject overrides) throws Exception {
        Config config = loadConfig(overrides);
        int safeExpiry = expiresInSeconds > 0 ? expiresInSeconds : DEFAULT_URL_EXPIRY_SECONDS;
        String signedUrl = createClient(config).presignConstrainedObjectURL(
            config.bucketName, objectKey, safeExpiry).toString();
        JSONObject result = new JSONObject();
        result.put("objectKey", objectKey);
        result.put("bucketName", config.bucketName);
        result.put("endpoint", config.endpoint);
        result.put("signedUrl", signedUrl);
        result.put("expiresInSeconds", safeExpiry);
        result.put("expiresAt", System.currentTimeMillis() + safeExpiry * 1000L);
        return result;
    }

    /** Refresh local OSS-backed video URLs immediately before sending history to a model. */
    public void refreshVideoMessageUrls(JSONArray messages) throws Exception {
        if (messages == null) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            JSONArray localAttachments = message.optJSONArray("local_attachments");
            if (localAttachments == null) continue;

            String objectKey = null;
            long expiresAt = 0L;
            for (int j = 0; j < localAttachments.length(); j++) {
                JSONObject attachment = localAttachments.optJSONObject(j);
                if (attachment != null && "video".equals(attachment.optString("type"))) {
                    objectKey = attachment.optString("ossObjectKey", null);
                    expiresAt = attachment.optLong("ossUrlExpiresAt", 0L);
                    break;
                }
            }
            if (objectKey == null || (expiresAt > now + REFRESH_MARGIN_MS)) continue;

            String refreshedUrl = createSignedUrl(objectKey, DEFAULT_URL_EXPIRY_SECONDS, null);
            JSONArray content = message.optJSONArray("content");
            if (content != null) {
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part != null && "video_url".equals(part.optString("type"))) {
                        JSONObject videoUrl = part.optJSONObject("video_url");
                        if (videoUrl != null) videoUrl.put("url", refreshedUrl);
                    }
                }
            }
        }
    }

    private Config loadConfig(JSONObject overrides) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String note = preferences.getString(UPLOAD_TOOL_NOTE, "");
        if (note == null || note.trim().isEmpty()) {
            note = preferences.getString(SIGNED_URL_TOOL_NOTE, "");
        }
        Config config = new Config();
        config.accessKeyId = value(overrides, "accessKeyId", note, NOTE_KEY_ACCESS_KEY_ID);
        config.accessKeySecret = value(overrides, "accessKeySecret", note, NOTE_KEY_ACCESS_KEY_SECRET);
        config.bucketName = value(overrides, "bucketName", note, NOTE_KEY_BUCKET_NAME);
        config.endpoint = value(overrides, "endpoint", note, NOTE_KEY_ENDPOINT);
        if (config.accessKeyId == null || config.accessKeySecret == null
            || config.bucketName == null || config.endpoint == null) {
            throw new IllegalArgumentException("OSS 凭证不完整，请配置 ossUploadFile 工具备注");
        }
        return config;
    }

    private static String value(JSONObject overrides, String argumentName, String note, String noteKey) {
        String direct = overrides == null ? null : overrides.optString(argumentName, null);
        if (direct != null && !direct.trim().isEmpty()) return direct.trim();
        if (note == null) return null;
        for (String line : note.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(noteKey + "=")) {
                String result = trimmed.substring(noteKey.length() + 1).trim();
                if (!result.isEmpty()) return result;
            }
        }
        return null;
    }

    private OSS createClient(Config config) {
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setConnectionTimeout(15 * 1000);
        clientConfig.setSocketTimeout(60 * 1000);
        clientConfig.setMaxConcurrentRequest(5);
        clientConfig.setMaxErrorRetry(2);
        OSSCredentialProvider provider = new OSSPlainTextAKSKCredentialProvider(
            config.accessKeyId, config.accessKeySecret);
        return new OSSClient(context, config.endpoint, provider, clientConfig);
    }

    private static String buildPublicUrl(Config config, String objectKey) {
        String host = config.endpoint.replace("https://", "").replace("http://", "");
        return "https://" + config.bucketName + "." + host + "/" + objectKey;
    }

    private static class Config {
        String accessKeyId;
        String accessKeySecret;
        String bucketName;
        String endpoint;
    }
}
