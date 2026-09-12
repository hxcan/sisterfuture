package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.stupidbeauty.sisterfuture.utils.FileLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads an HTTP(S) response body directly to phone storage. */
public class HttpFileDownloadTool implements Tool
{
  private static final String TAG = "HttpFileDownloadTool";
  private static final int DEFAULT_TIMEOUT_SEC = 300;
  private static final int MIN_TIMEOUT_SEC = 5;
  private static final int MAX_TIMEOUT_SEC = 3600;
  static final long MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024;

  private static final OkHttpClient BASE_CLIENT = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .retryOnConnectionFailure(true)
    .build();

  private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

  private final Context context;

  public HttpFileDownloadTool(Context context)
  {
    Context applicationContext = context != null ? context.getApplicationContext() : null;
    this.context = applicationContext != null ? applicationContext : context;
  }

  @Override
  public String getName()
  {
    return "downloadHttpFile";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDefinition = new JSONObject();
      functionDefinition.put("name", getName());
      functionDefinition.put("description",
        "从任意 HTTP 或 HTTPS URL 下载文件到手机。文件以二进制流直接保存，"
          + "不会把内容或 Base64 返回到对话中；图片和视频会作为本地附件返回，"
          + "以便直接预览或播放。仅在用户明确要求下载文件时使用。");

      JSONObject properties = new JSONObject();
      properties.put("url", new JSONObject()
        .put("type", "string")
        .put("description", "文件的 HTTP 或 HTTPS URL"));
      properties.put("phone_path", new JSONObject()
        .put("type", "string")
        .put("description", "可选的手机完整保存路径；省略时保存到公共 Download 目录，且不会覆盖同名文件"));
      properties.put("timeout_sec", new JSONObject()
        .put("type", "integer")
        .put("default", DEFAULT_TIMEOUT_SEC)
        .put("minimum", MIN_TIMEOUT_SEC)
        .put("maximum", MAX_TIMEOUT_SEC)
        .put("description", "整个下载的超时时间（秒），默认 300，允许 5 到 3600"));
      properties.put("headers", new JSONObject()
        .put("type", "object")
        .put("description", "可选的自定义 HTTP 请求头，键值对形式。常用场景：Cookie（Redmine 附件下载）、Authorization（Bearer / API Key）、Referer、User-Agent 等。")
        .put("additionalProperties", new JSONObject().put("type", "string")));

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", properties);
      parameters.put("required", new JSONArray(new String[]{"url"}));

      functionDefinition.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDefinition);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "构建工具定义失败", e);
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public boolean isAsync()
  {
    return true;
  }

  @Override
  public boolean shouldRecordParameterHistory()
  {
    // 下载地址经常包含短期签名或令牌，不能进入历史参数推荐。
    return false;
  }

  @Override
  public void executeAsync(@NonNull JSONObject arguments,
                           @NonNull OnResultCallback callback)
  {
    DOWNLOAD_EXECUTOR.execute(() ->
    {
      JSONObject result;
      try
      {
        String rawUrl = arguments.getString("url").trim();
        if (rawUrl.isEmpty())
        {
          throw new IllegalArgumentException("URL 不能为空");
        }

        HttpUrl requestedUrl = HttpUrl.parse(rawUrl);
        if (requestedUrl == null
          || !("http".equals(requestedUrl.scheme()) || "https".equals(requestedUrl.scheme())))
        {
          throw new IllegalArgumentException("仅支持有效的 HTTP 或 HTTPS URL");
        }

        int timeoutSec = arguments.optInt("timeout_sec", DEFAULT_TIMEOUT_SEC);
        if (timeoutSec < MIN_TIMEOUT_SEC || timeoutSec > MAX_TIMEOUT_SEC)
        {
          throw new IllegalArgumentException(
            "timeout_sec 必须在 " + MIN_TIMEOUT_SEC + " 到 " + MAX_TIMEOUT_SEC + " 之间");
        }

        String phonePath = arguments.optString("phone_path", "").trim();
        File explicitTarget = null;
        if (!phonePath.isEmpty())
        {
          explicitTarget = new File(phonePath);
          if (!explicitTarget.isAbsolute())
          {
            throw new IllegalArgumentException("phone_path 必须是完整的绝对路径");
          }
        }

        // 解析自定义请求头（如 Cookie、Authorization 等），用于需要认证的下载场景（Redmine 附件、GitHub 私有仓库等）
        Map<String, String> customHeaders = parseCustomHeaders(arguments.optJSONObject("headers"));

        DownloadResult downloadResult = download(requestedUrl, explicitTarget, timeoutSec, customHeaders);
        scanDownloadedFile(downloadResult.file, downloadResult.contentType);

        result = buildSuccessResult(downloadResult);
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "HTTP 文件下载失败：" + e.getMessage(), e);
        callback.onError(e);
        return;
      }

      callback.onResult(result);
    });
  }

  /**
   * 从工具入参的 headers 字段中解析出 Map<String, String>。
   * 为空、为 null、或字段不是 JSONObject 时返回 null（视为不传 headers）。
   */
  private Map<String, String> parseCustomHeaders(JSONObject headersObject)
  {
    if (headersObject == null || headersObject.length() == 0)
    {
      return null;
    }

    Map<String, String> result = new java.util.HashMap<>();
    Iterator<String> keys = headersObject.keys();
    while (keys.hasNext())
    {
      String key = keys.next();
      Object value = headersObject.opt(key);
      if (value != null)
      {
        result.put(key, value.toString());
      }
    }
    return result.isEmpty() ? null : result;
  }

  JSONObject buildSuccessResult(DownloadResult downloadResult) throws Exception
  {
    JSONObject result = new JSONObject();
    result.put("status", "success");
    result.put("success", true);
    result.put("file_saved", true);
    result.put("phone_path", downloadResult.file.getAbsolutePath());
    result.put("file_name", downloadResult.file.getName());
    result.put("size_bytes", downloadResult.sizeBytes);
    result.put("content_type", downloadResult.contentType);
    result.put("status_code", downloadResult.statusCode);
    result.put("redirect_count", downloadResult.redirectCount);
    result.put("duration_ms", downloadResult.durationMs);
    result.put("processed_at", System.currentTimeMillis());

    JSONObject mediaAttachment = buildMediaAttachment(downloadResult);
    if (mediaAttachment != null)
    {
      result.put("attachments", new JSONArray().put(mediaAttachment));
    }
    return result;
  }

  private JSONObject buildMediaAttachment(DownloadResult downloadResult) throws Exception
  {
    String mimeType = resolveMediaMimeType(
      downloadResult.contentType, downloadResult.file.getName());
    if (mimeType == null)
    {
      return null;
    }

    String mediaType = attachmentTypeForMimeType(mimeType);

    JSONObject metadata = new JSONObject();
    metadata.put("size", downloadResult.sizeBytes);
    metadata.put("mimeType", mimeType);

    JSONObject attachment = new JSONObject();
    attachment.put("type", mediaType);
    attachment.put("url", "file://" + downloadResult.file.getAbsolutePath());
    attachment.put("metadata", metadata);
    return attachment;
  }

  private String resolveMediaMimeType(String contentType, String fileName)
  {
    String declaredMimeType = normalizeContentType(contentType);
    String supportedMimeType = supportedMediaMimeType(declaredMimeType);
    if (supportedMimeType != null)
    {
      return supportedMimeType;
    }
    if (!declaredMimeType.isEmpty()
      && !"application/octet-stream".equals(declaredMimeType)
      && !"binary/octet-stream".equals(declaredMimeType)
      && !"application/download".equals(declaredMimeType)
      && !"application/x-download".equals(declaredMimeType))
    {
      return null;
    }

    String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
    if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
      || lowerName.endsWith(".jpe"))
    {
      return "image/jpeg";
    }
    if (lowerName.endsWith(".png")) return "image/png";
    if (lowerName.endsWith(".gif")) return "image/gif";
    if (lowerName.endsWith(".webp")) return "image/webp";
    if (lowerName.endsWith(".bmp")) return "image/bmp";
    if (lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v")) return "video/mp4";
    if (lowerName.endsWith(".mov")) return "video/quicktime";
    if (lowerName.endsWith(".webm")) return "video/webm";
    if (lowerName.endsWith(".mkv")) return "video/x-matroska";
    if (lowerName.endsWith(".3gp") || lowerName.endsWith(".3gpp")) return "video/3gpp";
    return null;
  }

  private String normalizeContentType(String contentType)
  {
    if (contentType == null)
    {
      return "";
    }

    int parameterStart = contentType.indexOf(';');
    String mimeType = parameterStart >= 0
      ? contentType.substring(0, parameterStart)
      : contentType;
    return mimeType.trim().toLowerCase(Locale.ROOT);
  }

  private String supportedMediaMimeType(String mimeType)
  {
    switch (mimeType)
    {
      case "image/jpeg":
      case "image/png":
      case "image/gif":
      case "image/webp":
      case "image/bmp":
      case "video/mp4":
      case "video/webm":
      case "video/3gpp":
      case "video/quicktime":
      case "video/x-matroska":
        return mimeType;
      case "image/jpg":
      case "image/pjpeg":
        return "image/jpeg";
      case "image/x-png":
        return "image/png";
      case "image/x-ms-bmp":
        return "image/bmp";
      default:
        return null;
    }
  }

  private String attachmentTypeForMimeType(String mimeType)
  {
    if (mimeType != null && mimeType.startsWith("image/"))
    {
      return "image";
    }
    if (mimeType != null && mimeType.startsWith("video/"))
    {
      return "video";
    }
    return null;
  }

  DownloadResult download(HttpUrl requestedUrl, File explicitTarget,
                          int timeoutSec, Map<String, String> customHeaders) throws IOException
  {
    OkHttpClient client = BASE_CLIENT.newBuilder()
      .connectTimeout(Math.min(30, timeoutSec), TimeUnit.SECONDS)
      .readTimeout(timeoutSec, TimeUnit.SECONDS)
      .callTimeout(timeoutSec, TimeUnit.SECONDS)
      .addNetworkInterceptor(chain ->
      {
        enforceNoHttpsDowngrade(requestedUrl, chain.request().url());
        return chain.proceed(chain.request());
      })
      .build();

    Request.Builder requestBuilder = new Request.Builder()
      .url(requestedUrl)
      .header("User-Agent", "SisterFuture/1.0")
      .get();

    // 透传调用方传入的自定义请求头。OkHttp 的 header() 同名将覆盖上面的同名值。
    if (customHeaders != null)
    {
      for (Map.Entry<String, String> entry : customHeaders.entrySet())
      {
        String name = entry.getKey();
        String value = entry.getValue();
        if (name != null && !name.isEmpty() && value != null)
        {
          requestBuilder.header(name, value);
        }
      }
    }

    Request request = requestBuilder.build();

    long startedAt = System.currentTimeMillis();
    try (Response response = client.newCall(request).execute())
    {
      if (!response.isSuccessful())
      {
        throw new IOException("HTTP 下载失败：" + response.code() + " " + response.message());
      }

      ResponseBody body = response.body();
      if (body == null)
      {
        throw new IOException("HTTP 响应体为空");
      }

      HttpUrl finalUrl = response.request().url();
      enforceNoHttpsDowngrade(requestedUrl, finalUrl);

      File target = explicitTarget != null
        ? explicitTarget
        : createDefaultTarget(finalUrl);

      long sizeBytes;
      try (InputStream input = body.byteStream())
      {
        sizeBytes = HttpFileDownloadSupport.writeAtomically(
          input, body.contentLength(), target, MAX_DOWNLOAD_BYTES);
      }

      String contentType = body.contentType() != null
        ? body.contentType().toString()
        : "application/octet-stream";

      return new DownloadResult(
        target.getCanonicalFile(),
        finalUrl,
        sizeBytes,
        contentType,
        response.code(),
        countRedirects(response),
        System.currentTimeMillis() - startedAt
      );
    }
  }

  static void enforceNoHttpsDowngrade(HttpUrl requestedUrl, HttpUrl currentUrl)
    throws IOException
  {
    if ("https".equals(requestedUrl.scheme()) && !"https".equals(currentUrl.scheme()))
    {
      throw new IOException("拒绝从 HTTPS 重定向到不安全的 HTTP 地址");
    }
  }

  private File createDefaultTarget(HttpUrl finalUrl) throws IOException
  {
    List<String> pathSegments = finalUrl.pathSegments();
    String candidate = pathSegments.isEmpty() ? "" : pathSegments.get(pathSegments.size() - 1);
    String fileName = HttpFileDownloadSupport.sanitizeFileName(candidate);
    if (fileName.isEmpty())
    {
      fileName = "http_download_" + System.currentTimeMillis();
    }

    File downloadDirectory = Environment.getExternalStoragePublicDirectory(
      Environment.DIRECTORY_DOWNLOADS);
    return HttpFileDownloadSupport.findAvailableTarget(downloadDirectory, fileName);
  }

  private int countRedirects(Response response)
  {
    int count = 0;
    Response previous = response.priorResponse();
    while (previous != null)
    {
      count++;
      previous = previous.priorResponse();
    }
    return count;
  }

  private void scanDownloadedFile(File file, String contentType)
  {
    try
    {
      MediaScannerConnection.scanFile(
        context,
        new String[]{file.getAbsolutePath()},
        new String[]{contentType},
        null
      );
    }
    catch (Exception e)
    {
      FileLogger.w(TAG, "文件已保存，但媒体扫描失败：" + e.getMessage());
    }
  }

  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    return "只有在用户明确要求把 HTTP/HTTPS 文件下载到手机时，才调用 downloadHttpFile。"
      + "url 必填；phone_path 可指定完整绝对路径，省略时保存到公共 Download 目录；"
      + "大文件可适当提高 timeout_sec；headers 可选，键值对形式，用于 Cookie / Authorization / Referer / User-Agent 等需要认证或自定义头的下载场景（如 Redmine 附件、GitHub 私有仓库）。"
      + "工具只返回实际保存路径和元数据，不返回文件内容；"
      + "下载的图片和视频会作为本地附件返回，以便直接预览或播放。";
  }

  static class DownloadResult
  {
    final File file;
    final HttpUrl finalUrl;
    final long sizeBytes;
    final String contentType;
    final int statusCode;
    final int redirectCount;
    final long durationMs;

    DownloadResult(File file, HttpUrl finalUrl, long sizeBytes, String contentType,
                   int statusCode, int redirectCount, long durationMs)
    {
      this.file = file;
      this.finalUrl = finalUrl;
      this.sizeBytes = sizeBytes;
      this.contentType = contentType;
      this.statusCode = statusCode;
      this.redirectCount = redirectCount;
      this.durationMs = durationMs;
    }
  }
}
