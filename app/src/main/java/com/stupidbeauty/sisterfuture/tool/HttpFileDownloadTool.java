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
import java.util.List;
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
          + "不会把内容或 Base64 返回到对话中。仅在用户明确要求下载文件时使用。");

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

        DownloadResult downloadResult = download(requestedUrl, explicitTarget, timeoutSec);
        scanDownloadedFile(downloadResult.file, downloadResult.contentType);

        result = new JSONObject();
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

  DownloadResult download(HttpUrl requestedUrl, File explicitTarget,
                          int timeoutSec) throws IOException
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

    Request request = new Request.Builder()
      .url(requestedUrl)
      .header("User-Agent", "SisterFuture/1.0")
      .get()
      .build();

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
      + "大文件可适当提高 timeout_sec。工具只返回实际保存路径和元数据，不返回文件内容。";
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
