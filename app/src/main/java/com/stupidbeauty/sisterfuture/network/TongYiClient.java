package com.stupidbeauty.sisterfuture.network;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.utils.ContextLengthUtils;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;

import com.stupidbeauty.sisterfuture.tool.ToolManager;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class TongYiClient
{
  private static final String TAG = "TongYiClient";
  private ModelAccessPointManager accessPointManager;
  private NetworkRequester networkRequester;
  private ToolManager toolManager;

  // === 🔒 #5028 新增：串行请求队列 ===
  private final LinkedBlockingQueue<Runnable> requestQueue = new LinkedBlockingQueue<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "TongYiClient-Queue-Worker");
    t.setDaemon(true);
    return t;
  });
  
  // 队列统计
  private final AtomicInteger totalRequestsSubmitted = new AtomicInteger(0);
  private final AtomicLong totalWaitTimeMs = new AtomicLong(0);
  private final AtomicInteger queueSizeHighWaterMark = new AtomicInteger(0);

  public TongYiClient(ModelAccessPointManager accessPointManager, ToolManager toolManager)
  {
    this.accessPointManager = accessPointManager;
    this.toolManager = toolManager;
    this.networkRequester = new OkHttpNetworkRequester(this.accessPointManager, this.toolManager);
    
    // === 🔒 #5028 启动队列处理器 ===
    startQueueProcessor();
    FileLogger.i(TAG, "🔒 [QUEUE_INIT] TongYiClient 初始化完成，串行请求队列已启动");
  }
  
  // === 🔒 #5028 队列处理器 ===
  private void startQueueProcessor() {
    executor.submit(() -> {
      FileLogger.i(TAG, "🔒 [QUEUE_WORKER] 队列工作线程启动：" + Thread.currentThread().getName());
      
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Runnable request = requestQueue.take(); // 阻塞等待下一个请求
          request.run();
        } catch (InterruptedException e) {
          FileLogger.w(TAG, "🔒 [QUEUE_WORKER] 队列工作线程被中断，退出循环");
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          FileLogger.e(TAG, "🔒 [QUEUE_ERROR] 队列执行异常", e);
          // 继续处理下一个请求，不退出循环
        }
      }
      
      FileLogger.w(TAG, "🔒 [QUEUE_WORKER] 队列工作线程已退出");
    });
  }

  public void sendChatRequest(JSONArray messages, boolean includeTools , OnResponseListener listener, Runnable onStreamComplete)
  {
    final long submitTime = System.currentTimeMillis();
    final int queueSizeBefore = requestQueue.size();
    final int totalRequests = totalRequestsSubmitted.incrementAndGet();
    
    // === 🔒 #5028 将请求提交到队列 ===
    FileLogger.d(TAG, "🔒 [QUEUE_SUBMIT] 请求 #" + totalRequests + " 提交到队列 | 当前队列长度：" + queueSizeBefore + " | 线程：" + Thread.currentThread().getName());
    
    requestQueue.submit(() -> {
      final long startTime = System.currentTimeMillis();
      final long waitTime = startTime - submitTime;
      final int queueSizeNow = requestQueue.size();
      
      totalWaitTimeMs.addAndGet(waitTime);
      
      // 更新高水位标记
      int currentQueueSize = queueSizeBefore;
      int oldHighWaterMark = queueSizeHighWaterMark.get();
      while (currentQueueSize > oldHighWaterMark) {
        if (queueSizeHighWaterMark.compareAndSet(oldHighWaterMark, currentQueueSize)) {
          break;
        }
        oldHighWaterMark = queueSizeHighWaterMark.get();
        currentQueueSize = queueSizeBefore;
      }
      
      FileLogger.d(TAG, "🔒 [QUEUE_EXEC] 请求 #" + totalRequests + " 开始执行 | 等待时间：" + waitTime + "ms | 当前队列长度：" + queueSizeNow + " | 线程：" + Thread.currentThread().getName());
      
      try {
        // 执行实际的网络请求
        networkRequester.sendRequest(messages, includeTools, listener, onStreamComplete);
        
        final long endTime = System.currentTimeMillis();
        final long executionTime = endTime - startTime;
        
        FileLogger.d(TAG, "🔒 [QUEUE_DONE] 请求 #" + totalRequests + " 完成 | 执行时间：" + executionTime + "ms | 总耗时：" + (waitTime + executionTime) + "ms");
        
        // 每 10 个请求输出一次统计
        if (totalRequests % 10 == 0) {
          long avgWaitTime = totalWaitTimeMs.get() / totalRequests;
          int highWaterMark = queueSizeHighWaterMark.get();
          FileLogger.i(TAG, "🔒 [QUEUE_STATS] 队列统计 | 总请求数：" + totalRequests + " | 平均等待时间：" + avgWaitTime + "ms | 队列最大长度：" + highWaterMark);
        }
      } catch (Exception e) {
        FileLogger.e(TAG, "🔒 [QUEUE_ERROR] 请求 #" + totalRequests + " 执行失败", e);
        throw e; // 重新抛出异常，让调用者处理
      }
    });
    
    FileLogger.d(TAG, "🔒 [QUEUE_ENQUEUED] 请求 #" + totalRequests + " 已加入队列 | 提交后队列长度：" + requestQueue.size());
  }

  public interface OnResponseListener
  {
    void onResponse(String response);
    void onError(Exception error);
  }

  interface NetworkRequester
  {
    void sendRequest(JSONArray messages, boolean includeTools , OnResponseListener listener, Runnable onStreamComplete);
  }

  private static class OkHttpNetworkRequester implements NetworkRequester
  {
    private static final String NETWORK_TAG = "TongYiClient.Network";
    private final OkHttpClient client;
    private final ModelAccessPointManager accessPointManager;
    private final ToolManager toolManager;

    public OkHttpNetworkRequester(ModelAccessPointManager accessPointManager, ToolManager toolManager)
    {
      this.client = new OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(160, TimeUnit.SECONDS)
        .build();
      this.accessPointManager = accessPointManager;
      this.toolManager = toolManager;
    }

    @Override
    public void sendRequest(JSONArray messages, boolean includeTools, OnResponseListener listener, Runnable onStreamComplete)
    {
      FileLogger.d(NETWORK_TAG, "🌐 [HTTP_START] 开始发起 HTTP 请求 | 线程：" + Thread.currentThread().getName());
      
      ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
      String apiKey = null;
      
      if (currentAccessPoint != null) {
          apiKey = currentAccessPoint.getApiKey();
          FileLogger.d(NETWORK_TAG, "[AP Info] 当前接入点名称：" + currentAccessPoint.getName());
          FileLogger.d(NETWORK_TAG, "[AP Info] 当前模型名称：" + currentAccessPoint.getModelName());
          FileLogger.d(NETWORK_TAG, "[AP Info] Base URL: " + currentAccessPoint.getBaseUrl());
      }
      
      String effectiveApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
          
      // 🔍 [调试] 添加 API Key 脱敏输出（前 8 位 + ... + 后 4 位）
      String apiKeyMasked = (apiKey != null && apiKey.length() > 12) 
          ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
          : (apiKey != null ? "***" : "null");
      FileLogger.d(NETWORK_TAG, "[API Key] 接入点=\"" + (currentAccessPoint != null ? currentAccessPoint.getName() : "null") + "\", Key=\"" + apiKeyMasked + "\" (长度：" + (apiKey != null ? apiKey.length() : 0) + ")");

      try
      {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", accessPointManager.getCurrentModelName());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        // #4775 禁用思考功能，避免空回复问题
        requestBody.put("enable_thinking", false);

        if (includeTools)
        {
            JSONArray toolsArray = new JSONArray();
            for (Tool tool : toolManager.getRegisteredTools())
            {
              if (tool.shouldInclude())
              {
                JSONObject toolDef = tool.getDefinition();
                if (toolDef != null && !toolDef.toString().isEmpty())
                {
                  toolsArray.put(toolDef);
                }
              }
            }

            if (toolsArray.length() > 0)
            {
              requestBody.put("tools", toolsArray);
              requestBody.put("tool_choice", "auto");
            }
        }

        RequestBody body = RequestBody.create
        (
          MediaType.parse("application/json; charset=utf-8"),
          requestBody.toString()
        );

        String baseUrl = accessPointManager.getCurrentBaseUrl();
        String endpoint = accessPointManager.getCurrentChatEndpoint();
        String fullUrl = baseUrl + endpoint;
        
        FileLogger.d(NETWORK_TAG, "URL: " + fullUrl);
        FileLogger.d(NETWORK_TAG, "Body length: " + requestBody.toString().length());
        
        // #4833 优化：截断 Body preview 至 200 字符
        String bodyPreview = requestBody.toString().length() > 200 
            ? requestBody.toString().substring(0, 200) + "..." 
            : requestBody.toString();
        FileLogger.d(NETWORK_TAG, "Body preview: " + bodyPreview);

        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            FileLogger.w(NETWORK_TAG, "⚠️ Double slash in URL!");
        }

        Request request = new Request.Builder()
          .url(fullUrl)
          .addHeader("Authorization", "Bearer " + effectiveApiKey)
          .addHeader("Content-Type", "application/json")
          .post(body)
          .build();

        FileLogger.d(NETWORK_TAG, "🌐 [HTTP_ENQUEUE] OkHttp 请求已加入网络队列 | 线程：" + Thread.currentThread().getName());

        client.newCall(request).enqueue(new Callback()
        {
          @Override
          public void onFailure(Call call, IOException e)
          {
            FileLogger.e(NETWORK_TAG, "🌐 [HTTP_FAILURE] 请求失败：" + e.getMessage() + " | 线程：" + Thread.currentThread().getName());
            accessPointManager.reportCurrentAccessPointUnavailable();
            listener.onError(new AccessPointUnavailableException("Current access point is unavailable", e));
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            int statusCode = response.code();
            FileLogger.d(NETWORK_TAG, "🌐 [HTTP_RESPONSE] HTTP Response Status: " + statusCode + " | 线程：" + Thread.currentThread().getName());
            
            if (!response.isSuccessful())
            {
              String errorBody = "";
              try {
                errorBody = response.body().string();
                FileLogger.e(NETWORK_TAG, "HTTP " + statusCode + " Error Body: " + errorBody);
                
                // #4833 优化：截断错误体预览
                String errorPreview = errorBody.length() > 2000 
                    ? errorBody.substring(0, 2000) + "..." 
                    : errorBody;
                FileLogger.e(NETWORK_TAG, "Error Body Preview: " + errorPreview);
                
                // ✅ #4823 新增：HTTP 400 且是上下文超长 → 不标记为接入点不可用
                if (statusCode == 400 && ContextLengthUtils.isContextLengthError(errorBody)) {
                  FileLogger.w(NETWORK_TAG, "🔍 检测到上下文超长错误（HTTP 400），不切换接入点");
                  FileLogger.d(NETWORK_TAG, "[ContextLength] 错误消息：" + errorBody);
                  listener.onError(new ResponseException(response, errorBody));
                  return; // 直接返回，不标记为不可用
                }
                
                // ✅ #4824 新增：HTTP 429 限流错误 → 不标记为接入点不可用
                if (statusCode == 429) {
                  FileLogger.w(NETWORK_TAG, "⚠️ 检测到 HTTP 429 限流错误，不切换接入点");
                  listener.onError(new RateLimitException(response, errorBody));
                  return; // 直接返回，不标记为不可用
                }
              } catch (Exception e) {
                FileLogger.e(NETWORK_TAG, "Failed to read error body: " + e.getMessage());
              }
              
              // 其他错误 → 标记为接入点不可用
              accessPointManager.reportCurrentAccessPointUnavailable();
              listener.onError(new AccessPointUnavailableException("Error: " + errorBody));
              listener.onError(new ResponseException(response, errorBody));
            }
            else
            {
              ResponseBody responseBody = response.body();
              if (responseBody != null)
              {
                FileLogger.d(NETWORK_TAG, "🌐 [HTTP_STREAM_START] 开始处理 SSE 流式响应 | 线程：" + Thread.currentThread().getName());
                processSSEStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete);
              }
            }
          }
        });
      }
      catch (Exception e)
      {
        FileLogger.e(NETWORK_TAG, "🌐 [HTTP_ERROR] 请求构建失败", e);
        e.printStackTrace();
        listener.onError(e);
      }
    }
  }

  private static boolean isHtmlResponse(String content)
  {
    if (content == null || content.isEmpty())
    {
      return false;
    }
    
    String trimmedContent = content.trim();
    return trimmedContent.startsWith("<!DOCTYPE html") ||
           trimmedContent.startsWith("<html") ||
           trimmedContent.startsWith("<HTML") ||
           trimmedContent.contains("<title") ||
           trimmedContent.contains("<TITLE");
  }

private static void processSSEStream(java.io.Reader reader, OnResponseListener listener, ModelAccessPointManager accessPointManager, Runnable onStreamComplete)
{
  try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader))
  {
    String line;
    boolean isDone = false;
    boolean htmlChecked = false;
    StringBuilder firstLineBuffer = new StringBuilder();
    
    // #4833 新增：记录接收到的总行数和 content 内容
    int lineCount = 0;
    int contentLineCount = 0;
    StringBuilder allContentBuilder = new StringBuilder();

    while ((line = bufferedReader.readLine()) != null)
    {
      lineCount++;
      
      // #4833 记录每一行 SSE 数据（前 500 字符）
      String linePreview = line.length() > 500 ? line.substring(0, 500) + "..." : line;
      FileLogger.d(TAG, "[SSE Line " + lineCount + "] " + linePreview);
      
      if (!htmlChecked)
      {
        htmlChecked = true;
        firstLineBuffer.append(line);
        
        String preview = firstLineBuffer.length() > 500 ? firstLineBuffer.substring(0, 500) : firstLineBuffer.toString();
        if (isHtmlResponse(preview))
        {
          FileLogger.e(TAG, "API returned HTML page");
          accessPointManager.reportCurrentAccessPointUnavailable();
          listener.onError(new ResponseException(null, "API returned HTML page"));
          return;
        }
      }

      if (line.startsWith("data:"))
      {
        String dataPart = line.substring(5).trim();
        
        // #4833 记录 data 行内容
        FileLogger.d(TAG, "[SSE Data] " + (dataPart.length() > 500 ? dataPart.substring(0, 500) + "..." : dataPart));

        if (!dataPart.isEmpty())
        {
          if (!dataPart.equals("[DONE]"))
          {
            try {
              // #4833 解析 JSON 并记录 delta 内容
              JSONObject json = new JSONObject(dataPart);
              if (json.has("choices") && json.getJSONArray("choices").length() > 0) {
                JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                if (choice.has("delta")) {
                  JSONObject delta = choice.getJSONObject("delta");
                  String content = delta.optString("content", "");
                  
                  if (!content.isEmpty()) {
                    contentLineCount++;
                    allContentBuilder.append(content);
                    FileLogger.d(TAG, "[SSE Content #" + contentLineCount + "] " + (content.length() > 200 ? content.substring(0, 200) + "..." : content));
                  } else {
                    FileLogger.d(TAG, "[SSE Content] delta.content is empty");
                  }
                  
                  // 记录 tool_calls 信息
                  if (delta.has("tool_calls")) {
                    FileLogger.d(TAG, "[SSE Tool Calls] delta contains tool_calls");
                  }
                }
              }
            } catch (Exception e) {
              FileLogger.e(TAG, "[SSE Parse Error] Failed to parse JSON: " + e.getMessage());
            }
            
            listener.onResponse(dataPart);
          }
          else
          {
            isDone = true;
            FileLogger.d(TAG, "SSE 流处理完成 [DONE]");
            
            // #4833 新增：记录最终统计信息
            FileLogger.d(TAG, "[SSE Summary] 总行数：" + lineCount);
            FileLogger.d(TAG, "[SSE Summary] content 行数：" + contentLineCount);
            FileLogger.d(TAG, "[SSE Summary] 总 content 长度：" + allContentBuilder.length());
            
            String finalContent = allContentBuilder.toString();
            if (finalContent.isEmpty()) {
              FileLogger.w(TAG, "[SSE Summary] ⚠️ 警告：模型返回空响应！");
              FileLogger.w(TAG, "[SSE Summary] 可能原因：1.上下文超长 2.模型错误 3.其他 API 错误");
            } else {
              FileLogger.d(TAG, "[SSE Summary] ✓ 模型响应正常，长度：" + finalContent.length());
            }
          }
        }
      }
    }

    if (isDone && onStreamComplete != null)
    {
      onStreamComplete.run();
      FileLogger.d(TAG, "流式响应处理完成，回调已执行");
    }
  }
  catch (IOException e)
  {
    FileLogger.e(TAG, "SSE 流处理失败", e);
    accessPointManager.reportCurrentAccessPointUnavailable();
    listener.onError(new AccessPointUnavailableException("Stream failed", e));
  }
}

  public static class AccessPointUnavailableException extends Exception
  {
    public AccessPointUnavailableException(String message)
    {
      super(message);
    }

    public AccessPointUnavailableException(String message, Throwable cause)
    {
      super(message, cause);
    }
  }

  /**
   * #4824 HTTP 429 限流异常
   * 表示 API 请求速率过快，需要延迟重试
   */
  public static class RateLimitException extends Exception
  {
    private final Response response;
    private final String customMessage;

    public RateLimitException(Response response, String customMessage)
    {
      super("HTTP " + response.code() + " - Rate Limit");
      this.response = response;
      this.customMessage = customMessage;
    }

    public Response getResponse()
    {
      return response;
    }

    public String getCustomMessage()
    {
      return customMessage;
    }
  }

  public static class ResponseException extends Exception
  {
    private final Response response;
    private final String customMessage;

    public ResponseException(Response response)
    {
      super("HTTP " + response.code());
      this.response = response;
      this.customMessage = null;
    }

    public ResponseException(Response response, String customMessage)
    {
      super("HTTP " + response.code() + " - " + customMessage);
      this.response = response;
      this.customMessage = customMessage;
    }

    public Response getResponse()
    {
      return response;
    }

    public String getCustomMessage()
    {
      return customMessage;
    }
  }
}