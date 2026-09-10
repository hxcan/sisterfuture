package com.stupidbeauty.sisterfuture.network;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.content.Context;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.OssManager;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.utils.ContextLengthUtils;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import com.stupidbeauty.sisterfuture.bean.ModelUsage;

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
import okio.Buffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

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
  
  // 🔗 新增：requestId ↔ messageId 映射表（用于追踪请求与消息的关联）
  private final Map<Long, String> requestIdToMessageIdMap = new ConcurrentHashMap<>();
  private final AtomicLong requestIdCounter = new AtomicLong(0);

  /**
   * Small, Android-independent guard used by every asynchronous request path.
   * The first terminal path wins; later OkHttp/SSE callbacks are ignored.
   */
  static final class CompletionGate
  {
    private final AtomicBoolean completed = new AtomicBoolean(false);

    boolean tryComplete()
    {
      return completed.compareAndSet(false, true);
    }

    boolean isCompleted()
    {
      return completed.get();
    }
  }

  /** Aggregates every HTTP attempt made by one logical model request. */
  static final class RequestMetricsAccumulator
  {
    private long totalRequestBytes;
    private long peakRequestBytes;
    private int requestCount;
    private String modelName;
    private long promptTokens = -1L;
    private long completionTokens = -1L;
    private long totalTokens = -1L;
    private boolean hasExplicitTotalTokens;

    synchronized void recordAttempt(long requestBytes, String attemptedModelName)
    {
      long normalizedBytes = Math.max(0L, requestBytes);
      totalRequestBytes += normalizedBytes;
      peakRequestBytes = Math.max(peakRequestBytes, normalizedBytes);
      requestCount++;
      if (attemptedModelName != null && !attemptedModelName.isEmpty())
      {
        modelName = attemptedModelName;
      }
    }

    synchronized void recordProviderUsage(long latestPromptTokens,
                                          long latestCompletionTokens,
                                          long latestTotalTokens)
    {
      if (latestPromptTokens >= 0L) promptTokens = latestPromptTokens;
      if (latestCompletionTokens >= 0L) completionTokens = latestCompletionTokens;
      if (latestTotalTokens >= 0L)
      {
        totalTokens = latestTotalTokens;
        hasExplicitTotalTokens = true;
      }
      else if (!hasExplicitTotalTokens
        && (latestPromptTokens >= 0L || latestCompletionTokens >= 0L))
      {
        totalTokens = Math.max(0L, promptTokens) + Math.max(0L, completionTokens);
      }
    }

    synchronized ModelUsage snapshot()
    {
      boolean hasTokenUsage = promptTokens >= 0L
        || completionTokens >= 0L
        || totalTokens >= 0L;
      long normalizedPrompt = Math.max(0L, promptTokens);
      long normalizedCompletion = Math.max(0L, completionTokens);
      long normalizedTotal = totalTokens >= 0L
        ? totalTokens
        : normalizedPrompt + normalizedCompletion;

      return new ModelUsage(
        normalizedPrompt,
        normalizedCompletion,
        normalizedTotal,
        normalizedPrompt,
        totalRequestBytes,
        peakRequestBytes,
        requestCount,
        hasTokenUsage ? 1 : 0,
        modelName,
        0L
      );
    }
  }

  /**
   * Only retry when the server clearly rejected the optional OpenAI usage
   * extension. Other 4xx responses must retain their original semantics.
   */
  static boolean isUnsupportedStreamOptionsResponse(int statusCode, String responseBody)
  {
    if ((statusCode != 400 && statusCode != 422) || responseBody == null)
    {
      return false;
    }

    String normalized = responseBody.toLowerCase(Locale.ROOT);
    boolean namesOptionalField = normalized.contains("stream_options")
      || normalized.contains("stream options")
      || normalized.contains("streamoptions")
      || normalized.contains("include_usage");
    if (!namesOptionalField)
    {
      return false;
    }

    return normalized.contains("unsupported")
      || normalized.contains("not supported")
      || normalized.contains("does not support")
      || normalized.contains("unknown")
      || normalized.contains("unrecognized")
      || normalized.contains("not recognized")
      || normalized.contains("unexpected")
      || normalized.contains("extra")
      || normalized.contains("additional properties")
      || normalized.contains("not permitted")
      || normalized.contains("not allowed")
      || normalized.contains("invalid")
      || normalized.contains("not a valid")
      || normalized.contains("不支持")
      || normalized.contains("未知")
      || normalized.contains("无效");
  }

  static boolean isTerminalFinishReason(String finishReason)
  {
    return finishReason != null
      && !finishReason.isEmpty()
      && !"null".equalsIgnoreCase(finishReason);
  }

  public TongYiClient(ModelAccessPointManager accessPointManager, ToolManager toolManager)
  {
    this(null, accessPointManager, toolManager);
  }

  public TongYiClient(Context context, ModelAccessPointManager accessPointManager, ToolManager toolManager)
  {
    this.accessPointManager = accessPointManager;
    this.toolManager = toolManager;
    this.networkRequester = new OkHttpNetworkRequester(this.accessPointManager, this.toolManager, this,
      context == null ? null : new OssManager(context));
    
    // === 🔒 #5028 启动队列处理器 ===
    startQueueProcessor();
  }
  
  // === 🔒 #5028 队列处理器 ===
  private void startQueueProcessor() {
    executor.submit(() -> {
      
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

  // 🔗 新增：带 messageId 的请求方法
  public void sendChatRequest(JSONArray messages, boolean includeTools, OnResponseListener listener, Runnable onStreamComplete, String reservedMessageId)
  {
    final long requestId = requestIdCounter.incrementAndGet();
    final long submitTime = System.currentTimeMillis();
    final int queueSizeBefore = requestQueue.size();
    final int totalRequests = totalRequestsSubmitted.incrementAndGet();
    
    // 🔗 记录 requestId ↔ messageId 映射
    if (reservedMessageId != null && !reservedMessageId.isEmpty())
    {
      requestIdToMessageIdMap.put(requestId, reservedMessageId);
      FileLogger.d(TAG, "🔗 [MAP_PUT] 请求 - 消息 ID 映射 | requestId=" + requestId + " | messageId=" + reservedMessageId);
    }
    
    // === 🔒 #5028 将请求提交到队列 ===
    boolean queued = requestQueue.offer(() -> {
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
      
      FileLogger.d(TAG, "🔒 [QUEUE_EXEC] 请求 #" + totalRequests + " (requestId=" + requestId + ") 开始执行 | 等待时间：" + waitTime + "ms | 当前队列长度：" + queueSizeNow + " | 线程：" + Thread.currentThread().getName());
      
      try {
        // 执行实际的网络请求，传入 requestId 和 messageId
        networkRequester.sendRequest(messages, includeTools, listener, onStreamComplete, requestId, reservedMessageId);

        final long endTime = System.currentTimeMillis();
        final long executionTime = endTime - startTime;

        FileLogger.d(TAG, "🔒 [QUEUE_DONE] 请求 #" + totalRequests + " (requestId=" + requestId + ") 完成 | 执行时间：" + executionTime + "ms | 总耗时：" + (waitTime + executionTime) + "ms");
        
        // 每 10 个请求输出一次统计
        if (totalRequests % 10 == 0) {
          long avgWaitTime = totalWaitTimeMs.get() / totalRequests;
          int highWaterMark = queueSizeHighWaterMark.get();
          FileLogger.i(TAG, "🔒 [QUEUE_STATS] 队列统计 | 总请求数：" + totalRequests + " | 平均等待时间：" + avgWaitTime + "ms | 队列最大长度：" + highWaterMark);
        }
      } catch (Exception e) {
        FileLogger.e(TAG, "🔒 [QUEUE_ERROR] 请求 #" + totalRequests + " (requestId=" + requestId + ") 执行失败", e);
        deliverTerminalUsage(listener, null, requestId);
        try
        {
          listener.onError(e);
        }
        catch (Exception callbackError)
        {
          FileLogger.e(TAG, "队列错误回调执行失败 (requestId=" + requestId + ")",
            callbackError);
        }
        removeRequestIdMapping(requestId);
      }
    });
    
    if (!queued) {
      FileLogger.e(TAG, "🔒 [QUEUE_REJECTED] 请求 #" + totalRequests + " (requestId=" + requestId + ") 被队列拒绝（队列已满）");
      deliverTerminalUsage(listener, null, requestId);
      try
      {
        listener.onError(new IllegalStateException("请求队列已满，无法接受新请求"));
      }
      catch (Exception callbackError)
      {
        FileLogger.e(TAG, "队列拒绝回调执行失败 (requestId=" + requestId + ")",
          callbackError);
      }
      removeRequestIdMapping(requestId);
    }
  }

  private static void deliverTerminalUsage(OnResponseListener listener, ModelUsage usage,
                                           long requestId)
  {
    try
    {
      listener.onUsage(usage);
    }
    catch (Exception callbackError)
    {
      FileLogger.e(TAG, "用量回调执行失败 (requestId=" + requestId + ")",
        callbackError);
    }
  }
  
  // ✅ 保留旧方法，兼容现有调用（默认 messageId 为 null）
  public void sendChatRequest(JSONArray messages, boolean includeTools , OnResponseListener listener, Runnable onStreamComplete)
  {
    sendChatRequest(messages, includeTools, listener, onStreamComplete, null);
  }
  
  // 🔗 新增：根据 requestId 获取对应的 messageId
  public String getMessageIdByRequestId(long requestId)
  {
    String messageId = requestIdToMessageIdMap.get(requestId);
    if (messageId != null)
    {
      FileLogger.d(TAG, "🔍 [MAP_GET] 找到 messageId | requestId=" + requestId + " | messageId=" + messageId);
    }
    else
    {
      FileLogger.w(TAG, "⚠️ [MAP_GET] 未找到 messageId | requestId=" + requestId);
    }
    return messageId;
  }
  
  // 🔗 新增：清除已完成的 requestId 映射（防止内存泄漏）
  public void removeRequestIdMapping(long requestId)
  {
    String removedId = requestIdToMessageIdMap.remove(requestId);
    if (removedId != null)
    {
      FileLogger.d(TAG, "🗑️ [MAP_REMOVE] 已清除映射 | requestId=" + requestId + " | messageId=" + removedId);
    }
  }

  public interface OnResponseListener
  {
    void onResponse(String response);
    void onError(Exception error);

    default void onUsage(ModelUsage usage)
    {
    }
  }

  interface NetworkRequester
  {
    void sendRequest(JSONArray messages, boolean includeTools, OnResponseListener listener, Runnable onStreamComplete, long requestId, String reservedMessageId);
  }

  private static class OkHttpNetworkRequester implements NetworkRequester
  {
    private static final String NETWORK_TAG = "TongYiClient.Network";
    private final OkHttpClient client;
    private final ModelAccessPointManager accessPointManager;
    private final ToolManager toolManager;
    private final TongYiClient tongYiClient; // 引用父类，用于访问映射表
    private final OssManager ossManager;
    private final Set<String> streamOptionsUnsupportedEndpoints =
      ConcurrentHashMap.newKeySet();

    public OkHttpNetworkRequester(ModelAccessPointManager accessPointManager, ToolManager toolManager,
                                  TongYiClient tongYiClient, OssManager ossManager)
    {
      this.client = new OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();
      this.accessPointManager = accessPointManager;
      this.toolManager = toolManager;
      this.tongYiClient = tongYiClient;
      this.ossManager = ossManager;
    }

    @Override
    public void sendRequest(JSONArray messages, boolean includeTools, OnResponseListener listener, Runnable onStreamComplete, long requestId, String reservedMessageId)
    {
      ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
      String apiKey = null;
      
      if (currentAccessPoint != null) {
          apiKey = currentAccessPoint.getApiKey();
      }
      
      String effectiveApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
          
      String apiKeyMasked = (apiKey != null && apiKey.length() > 12) 
          ? apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4)
          : (apiKey != null ? "***" : "null");
      FileLogger.d(NETWORK_TAG, "[API Key] 接入点=\"" + (currentAccessPoint != null ? currentAccessPoint.getName() : "null") + "\", Key=\"" + apiKeyMasked + "\" (长度：" + (apiKey != null ? apiKey.length() : 0) + ")");
      FileLogger.d(NETWORK_TAG, "[API Key] 接入点=\"" + (currentAccessPoint != null ? currentAccessPoint.getName() : "null") + "\", Key=\"" + apiKeyMasked + "\" (长度：" + (apiKey != null ? apiKey.length() : 0) + ")");
      
      // 🔗 记录请求信息
      FileLogger.d(NETWORK_TAG, "🔗 [REQUEST_INFO] requestId=" + requestId + " | messageId=" + reservedMessageId);
      RequestLifecycle lifecycle = new RequestLifecycle(listener, onStreamComplete, tongYiClient, requestId);

      try
      {
        // 🔍 #5031 检查：如果有 assistant 的 tool_calls，检查是否有对应的 tool message
        boolean hasAssistantToolCalls = false;
        List<String> toolCallIdsWithoutResponse = new ArrayList<>();
        for (int i = 0; i < messages.length(); i++)
        {
          try
          {
            JSONObject msg = messages.getJSONObject(i);
            String role = msg.optString("role", "");
            if ("assistant".equals(role) && msg.has("tool_calls"))
            {
              hasAssistantToolCalls = true;
              JSONArray toolCalls = msg.getJSONArray("tool_calls");
              for (int j = 0; j < toolCalls.length(); j++)
              {
                String tcId = toolCalls.getJSONObject(j).optString("id", "unknown");
                // 检查是否有对应的 tool message
                boolean hasResponse = false;
                for (int k = 0; k < messages.length(); k++)
                {
                  JSONObject otherMsg = messages.getJSONObject(k);
                  if ("tool".equals(otherMsg.optString("role", "")) && tcId.equals(otherMsg.optString("tool_call_id", "")))
                  {
                    hasResponse = true;
                    break;
                  }
                }
                if (!hasResponse)
                {
                  toolCallIdsWithoutResponse.add(tcId);
                }
              }
            }
          }
          catch (Exception e)
          {
            // ignore
          }
        }
        if (hasAssistantToolCalls)
        {
          if (!toolCallIdsWithoutResponse.isEmpty())
          {
            FileLogger.w(NETWORK_TAG, "🔍 [VALIDATION] ⚠️ 检测到 tool_calls 缺少对应的 tool message！缺失的 IDs：" + toolCallIdsWithoutResponse);
          }
          else
          {
            FileLogger.d(NETWORK_TAG, "🔍 [VALIDATION] ✓ 所有 tool_calls 都有对应的 tool message");
          }
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", accessPointManager.getCurrentModelName());
        requestBody.put("messages", stripLocalMessageMetadata(messages));
        requestBody.put("stream", true);
        requestBody.put("enable_thinking", false);
        
        JSONObject thinkingParams = new JSONObject();
        thinkingParams.put("type", "disabled");
        thinkingParams.put("budget_tokens", 100);
        requestBody.put("thinking", thinkingParams);
        
        JSONObject reasoningParams = new JSONObject();
        reasoningParams.put("split", true);
        requestBody.put("reasoning_split", reasoningParams.opt("split"));
        
        FileLogger.d(NETWORK_TAG, "🌐 [Minimax] 已添加 Minimax 思考控制参数：reasoning_split=true, thinking.budget_tokens=100");

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

        String baseUrl = accessPointManager.getCurrentBaseUrl();
        String endpoint = accessPointManager.getCurrentChatEndpoint();
        String fullUrl = baseUrl + endpoint;

        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            FileLogger.w(NETWORK_TAG, "⚠️ Double slash in URL!");
        }

        // Build only the request we are about to send. Image messages can make
        // this JSON very large, so the compatibility fallback is materialized
        // lazily only after an explicit stream_options validation error.
        boolean knownUnsupported = streamOptionsUnsupportedEndpoints.contains(fullUrl);
        if (!knownUnsupported)
        {
          requestBody.put("stream_options",
            new JSONObject().put("include_usage", true));
        }
        RequestAttempt primaryAttempt = buildAttempt(fullUrl, effectiveApiKey, requestBody);

        enqueueAttempt(primaryAttempt, !knownUnsupported, fullUrl, lifecycle, requestId,
          reservedMessageId,
          currentAccessPoint != null ? currentAccessPoint.getModelName() : null);
      }
      catch (Exception e)
      {
        FileLogger.e(NETWORK_TAG, "🌐 [HTTP_ERROR] 请求构建失败 (requestId=" + requestId + ")", e);

        // === 🔒 #5029 新增：检测 Authorization header 编码错误 ===
        // 当出现 IllegalArgumentException 且错误信息包含 "Unexpected char" 或 "Authorization" 时
        // 视为凭证损坏，触发接入点切换
        if (e instanceof IllegalArgumentException) {
          String errorMsg = e.getMessage();
          if (errorMsg != null && (errorMsg.contains("Unexpected char") || errorMsg.contains("Authorization"))) {
            FileLogger.w(NETWORK_TAG, "⚠️ 检测到 Authorization header 编码错误，标记接入点不可用 (requestId=" + requestId + ")");
            reportAccessPointUnavailable(accessPointManager, requestId);
            lifecycle.fail(new AccessPointUnavailableException("Invalid authorization header: " + errorMsg, e));
            return;
          }
        }

        e.printStackTrace();
        lifecycle.fail(e);
      }
    }

    private RequestAttempt buildAttempt(String fullUrl, String apiKey, JSONObject requestBody)
    {
      String requestJson = requestBody.toString();
      long requestBodyBytes = requestJson.getBytes(StandardCharsets.UTF_8).length;
      RequestBody body = RequestBody.create(
        MediaType.parse("application/json; charset=utf-8"), requestJson);
      Request request = new Request.Builder()
        .url(fullUrl)
        .addHeader("Authorization", "Bearer " + apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
        .build();
      return new RequestAttempt(request, requestJson.length(), preview(requestJson),
        requestBodyBytes);
    }

    private RequestAttempt buildFallbackAttempt(RequestAttempt originalAttempt)
      throws Exception
    {
      RequestBody originalBody = originalAttempt.request.body();
      if (originalBody == null)
      {
        throw new IOException("Cannot build stream_options fallback without a request body");
      }

      Buffer buffer = new Buffer();
      originalBody.writeTo(buffer);
      JSONObject fallbackBody = new JSONObject(buffer.readUtf8());
      fallbackBody.remove("stream_options");

      String requestJson = fallbackBody.toString();
      long requestBodyBytes = requestJson.getBytes(StandardCharsets.UTF_8).length;
      RequestBody body = RequestBody.create(
        MediaType.parse("application/json; charset=utf-8"), requestJson);
      Request request = originalAttempt.request.newBuilder().post(body).build();
      return new RequestAttempt(request, requestJson.length(), preview(requestJson),
        requestBodyBytes);
    }

    private static String preview(String requestJson)
    {
      return requestJson.length() > 200
        ? requestJson.substring(0, 200) + "..."
        : requestJson;
    }

    private void enqueueAttempt(RequestAttempt attempt, boolean allowStreamOptionsFallback,
                                String endpointKey,
                                RequestLifecycle lifecycle, long requestId,
                                String reservedMessageId, String modelName)
    {
      if (lifecycle.isCompleted()) return;

      FileLogger.d(NETWORK_TAG, "URL: " + attempt.request.url());
      FileLogger.d(NETWORK_TAG, "Body length: " + attempt.requestCharCount
        + " chars, " + attempt.requestBodyBytes + " bytes");
      FileLogger.d(NETWORK_TAG, "Body preview: " + attempt.requestPreview);

      try
      {
        Call call = client.newCall(attempt.request);
        lifecycle.recordAttempt(attempt.requestBodyBytes, modelName);
        call.enqueue(new Callback()
        {
          @Override
          public void onFailure(Call call, IOException e)
          {
            FileLogger.e(NETWORK_TAG, "🌐 [HTTP_FAILURE] 请求失败 (requestId="
              + requestId + "): " + e.getMessage() + " | 线程："
              + Thread.currentThread().getName());
            lifecycle.fail(new AccessPointUnavailableException(
              "Current access point is unavailable", e));
          }

          @Override
          public void onResponse(Call call, Response response)
          {
            if (lifecycle.isCompleted())
            {
              response.close();
              return;
            }

            int statusCode = response.code();
            FileLogger.d(NETWORK_TAG, "🌐 [HTTP_RESPONSE] HTTP Response Status: "
              + statusCode + " (requestId=" + requestId + ") | 线程："
              + Thread.currentThread().getName());

            if (!response.isSuccessful())
            {
              handleHttpError(response, attempt, allowStreamOptionsFallback,
                endpointKey, lifecycle, requestId, reservedMessageId, modelName);
              return;
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null)
            {
              response.close();
              lifecycle.fail(new AccessPointUnavailableException(
                "Successful response contained no body"));
              return;
            }

            FileLogger.d(NETWORK_TAG, "🌐 [HTTP_STREAM_START] 开始处理 SSE 流式响应 (requestId="
              + requestId + ") | 线程：" + Thread.currentThread().getName());
            processSSEStream(responseBody.charStream(), lifecycle, accessPointManager,
              requestId, reservedMessageId);
          }
        });
      }
      catch (Exception e)
      {
        FileLogger.e(NETWORK_TAG, "🌐 [HTTP_ERROR] 请求入队失败 (requestId="
          + requestId + ")", e);
        lifecycle.fail(e);
      }
    }

    private void handleHttpError(Response response, RequestAttempt attemptedRequest,
                                 boolean allowStreamOptionsFallback, String endpointKey,
                                 RequestLifecycle lifecycle, long requestId,
                                 String reservedMessageId, String modelName)
    {
      int statusCode = response.code();
      String errorBody;
      try
      {
        ResponseBody body = response.body();
        errorBody = body == null ? "" : body.string();
      }
      catch (Exception e)
      {
        response.close();
        FileLogger.e(NETWORK_TAG, "Failed to read error body: " + e.getMessage());
        lifecycle.fail(new AccessPointUnavailableException(
          "Failed to read error body: " + e.getMessage(), e));
        return;
      }

      FileLogger.e(NETWORK_TAG, "HTTP " + statusCode + " Error Body: " + errorBody);
      String errorPreview = errorBody.length() > 2000
        ? errorBody.substring(0, 2000) + "..."
        : errorBody;
      FileLogger.e(NETWORK_TAG, "Error Body Preview: " + errorPreview);

      if (allowStreamOptionsFallback
        && isUnsupportedStreamOptionsResponse(statusCode, errorBody))
      {
        streamOptionsUnsupportedEndpoints.add(endpointKey);
        try
        {
          RequestAttempt fallbackAttempt = buildFallbackAttempt(attemptedRequest);
          FileLogger.w(NETWORK_TAG, "接入点不支持 stream_options，将在同一请求生命周期内回退一次"
            + " (requestId=" + requestId + ")");
          enqueueAttempt(fallbackAttempt, false, endpointKey, lifecycle, requestId,
            reservedMessageId, modelName);
        }
        catch (Exception fallbackError)
        {
          FileLogger.e(NETWORK_TAG, "构建 stream_options 兼容回退请求失败"
            + " (requestId=" + requestId + ")", fallbackError);
          lifecycle.fail(fallbackError);
        }
        return;
      }

      // ✅ #4823 HTTP 400 → 上下文超长
      if (statusCode == 400 && ContextLengthUtils.isContextLengthError(errorBody))
      {
        FileLogger.w(NETWORK_TAG, "🔍 检测到上下文超长错误（HTTP 400），不切换接入点"
          + " (requestId=" + requestId + ")");
        lifecycle.fail(new ResponseException(response, errorBody));
        return;
      }

      // ✅ #4824 HTTP 429 → 限流错误
      if (statusCode == 429)
      {
        FileLogger.w(NETWORK_TAG, "⚠️ 检测到 HTTP 429 限流错误，不切换接入点"
          + " (requestId=" + requestId + ")");
        lifecycle.fail(new RateLimitException(response, errorBody));
        return;
      }

      FileLogger.d(NETWORK_TAG, "状态码 " + statusCode
        + " 表示接入点不可用，触发切换 (requestId=" + requestId + ")");
      lifecycle.fail(new AccessPointUnavailableException("Error: " + errorBody));
    }

    private static final class RequestAttempt
    {
      private final Request request;
      private final int requestCharCount;
      private final String requestPreview;
      private final long requestBodyBytes;

      private RequestAttempt(Request request, int requestCharCount, String requestPreview,
                             long requestBodyBytes)
      {
        this.request = request;
        this.requestCharCount = requestCharCount;
        this.requestPreview = requestPreview;
        this.requestBodyBytes = requestBodyBytes;
      }
    }

    private static final class RequestLifecycle
    {
      private final OnResponseListener listener;
      private final Runnable onStreamComplete;
      private final TongYiClient tongYiClient;
      private final long requestId;
      private final CompletionGate completionGate = new CompletionGate();
      private final RequestMetricsAccumulator metrics = new RequestMetricsAccumulator();

      private RequestLifecycle(OnResponseListener listener, Runnable onStreamComplete,
                               TongYiClient tongYiClient, long requestId)
      {
        this.listener = listener;
        this.onStreamComplete = onStreamComplete;
        this.tongYiClient = tongYiClient;
        this.requestId = requestId;
      }

      private boolean isCompleted()
      {
        return completionGate.isCompleted();
      }

      private boolean deliverResponse(String response)
      {
        if (completionGate.isCompleted()) return false;
        try
        {
          listener.onResponse(response);
          return !completionGate.isCompleted();
        }
        catch (Exception e)
        {
          fail(e);
          return false;
        }
      }

      private void recordAttempt(long requestBytes, String modelName)
      {
        metrics.recordAttempt(requestBytes, modelName);
      }

      private void recordProviderUsage(long promptTokens, long completionTokens,
                                       long totalTokens)
      {
        metrics.recordProviderUsage(promptTokens, completionTokens, totalTokens);
      }

      private void succeed()
      {
        if (!completionGate.tryComplete()) return;
        try
        {
          deliverTerminalUsage(listener,
            metrics.snapshot(), requestId);

          if (onStreamComplete != null)
          {
            try
            {
              onStreamComplete.run();
              FileLogger.d(NETWORK_TAG, "流式响应处理完成，回调已执行 (requestId="
                + requestId + ")");
            }
            catch (Exception e)
            {
              FileLogger.e(NETWORK_TAG, "流完成回调失败 (requestId=" + requestId + ")", e);
            }
          }
        }
        finally
        {
          tongYiClient.removeRequestIdMapping(requestId);
        }
      }

      private void fail(Exception error)
      {
        if (!completionGate.tryComplete()) return;
        try
        {
          deliverTerminalUsage(listener, metrics.snapshot(), requestId);
          listener.onError(error);
        }
        catch (Exception callbackError)
        {
          FileLogger.e(NETWORK_TAG, "错误回调执行失败 (requestId=" + requestId + ")",
            callbackError);
        }
        finally
        {
          tongYiClient.removeRequestIdMapping(requestId);
        }
      }
    }

    private JSONArray stripLocalMessageMetadata(JSONArray messages) throws Exception
    {
      JSONArray apiMessages = new JSONArray(messages.toString());
      if (ossManager != null) ossManager.refreshVideoMessageUrls(apiMessages);
      for (int i = 0; i < apiMessages.length(); i++)
      {
        JSONObject message = apiMessages.optJSONObject(i);
        if (message != null)
        {
          message.remove("local_attachments");
          message.remove(ModelUsage.LOCAL_METADATA_KEY);
        }
      }
      return apiMessages;
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

  private static void reportAccessPointUnavailable(ModelAccessPointManager accessPointManager,
                                                   long requestId)
  {
    try
    {
      accessPointManager.reportCurrentAccessPointUnavailable();
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "标记接入点不可用时失败 (requestId=" + requestId + ")", e);
    }
  }

  // 🔗 修改：添加 requestId 和 messageId 参数，用于日志记录
  private static void processSSEStream(java.io.Reader reader,
                                       OkHttpNetworkRequester.RequestLifecycle lifecycle,
                                       ModelAccessPointManager accessPointManager,
                                       long requestId, String reservedMessageId)
  {
    try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader))
    {
      String line;
      boolean isDone = false;
      boolean htmlChecked = false;
      StringBuilder firstLineBuffer = new StringBuilder();
      
      int lineCount = 0;
      int contentLineCount = 0;
      StringBuilder allContentBuilder = new StringBuilder();
      boolean sawTerminalChoice = false;

      while ((line = bufferedReader.readLine()) != null)
      {
        lineCount++;
        
        if (!htmlChecked)
        {
          htmlChecked = true;
          firstLineBuffer.append(line);
          
          String preview = firstLineBuffer.length() > 500 ? firstLineBuffer.substring(0, 500) : firstLineBuffer.toString();
          if (isHtmlResponse(preview))
          {
            FileLogger.e(TAG, "API returned HTML page (requestId=" + requestId + ")");
            reportAccessPointUnavailable(accessPointManager, requestId);
            lifecycle.fail(new ResponseException(null, "API returned HTML page"));
            return;
          }
        }

        if (line.startsWith("data:"))
        {
          String dataPart = line.substring(5).trim();
          

          if (!dataPart.isEmpty())
          {
            if (!dataPart.equals("[DONE]"))
            {
              boolean usageOnlyChunk = false;
              try {
                JSONObject json = new JSONObject(dataPart);
                JSONObject usageJson = json.optJSONObject("usage");
                if (usageJson != null)
                {
                  long promptTokens = optLong(usageJson, "prompt_tokens", "input_tokens");
                  long completionTokens = optLong(usageJson, "completion_tokens", "output_tokens");
                  long totalTokens = usageJson.has("total_tokens")
                    ? usageJson.optLong("total_tokens", -1L)
                    : -1L;
                  lifecycle.recordProviderUsage(promptTokens, completionTokens, totalTokens);
                }

                boolean hasChoices = json.has("choices") && json.getJSONArray("choices").length() > 0;
                usageOnlyChunk = usageJson != null && !hasChoices;
                if (hasChoices) {
                  JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                  String finishReason = choice.optString("finish_reason", "");
                  if (isTerminalFinishReason(finishReason))
                  {
                    sawTerminalChoice = true;
                  }
                  if (choice.has("delta")) {
                    JSONObject delta = choice.getJSONObject("delta");
                    
                    // 🔍 新增：打印 delta 中 tool_calls 的完整原始数据
                    if (delta.has("tool_calls")) {
                        JSONArray toolCallsInDelta = delta.getJSONArray("tool_calls");
                        String tcJson = toolCallsInDelta.toString();
                        FileLogger.d(TAG, "[SSE_TOOL_CALLS] delta.tool_calls: " + (tcJson.length() > 500 ? tcJson.substring(0, 500) + "..." : tcJson));
                    }
                    
                    String content = delta.optString("content", "");
                    
                    if (!content.isEmpty()) {
                      contentLineCount++;
                      allContentBuilder.append(content);
                    } else {
                      FileLogger.d(TAG, "[SSE Content] delta.content is empty (requestId=" + requestId + ")");
                    }
                  }
                }
              } catch (Exception e) {
                FileLogger.e(TAG, "[SSE Parse Error] Failed to parse JSON (requestId=" + requestId + "): " + e.getMessage());
              }
              
              if (!usageOnlyChunk)
              {
                if (!lifecycle.deliverResponse(dataPart)) return;
              }
            }
            else
            {
              isDone = true;
              FileLogger.d(TAG, "SSE 流处理完成 [DONE] (requestId=" + requestId + ", messageId=" + reservedMessageId + ")");
              
              FileLogger.d(TAG, "[SSE Summary] 总行数：" + lineCount);
              FileLogger.d(TAG, "[SSE Summary] content 行数：" + contentLineCount);
              FileLogger.d(TAG, "[SSE Summary] 总 content 长度：" + allContentBuilder.length());
              
              String finalContent = allContentBuilder.toString();
              if (finalContent.isEmpty()) {
                FileLogger.w(TAG, "[SSE Summary] ⚠️ 警告：模型返回空响应！(requestId=" + requestId + ")");
              } else {
                FileLogger.d(TAG, "[SSE Summary] ✓ 模型响应正常，长度：" + finalContent.length() + " (requestId=" + requestId + ")");
              }
              break;
            }
          }
        }
      }

      if (isDone || sawTerminalChoice)
      {
        if (!isDone)
        {
          FileLogger.w(TAG, "SSE 流未发送 [DONE]，但已收到 finish_reason，"
            + "按完整响应结算 (requestId=" + requestId + ")");
        }
        lifecycle.succeed();
      }
      else
      {
        FileLogger.e(TAG, "SSE 流在 [DONE] 前结束 (requestId=" + requestId + ")");
        reportAccessPointUnavailable(accessPointManager, requestId);
        lifecycle.fail(new AccessPointUnavailableException(
          "Stream ended before [DONE]"));
      }
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "SSE 流处理失败 (requestId=" + requestId + ")", e);
      reportAccessPointUnavailable(accessPointManager, requestId);
      lifecycle.fail(new AccessPointUnavailableException("Stream failed", e));
    }
  }

  private static long optLong(JSONObject json, String primaryKey, String fallbackKey)
  {
    if (json == null) return -1L;
    if (json.has(primaryKey)) return json.optLong(primaryKey, -1L);
    if (json.has(fallbackKey)) return json.optLong(fallbackKey, -1L);
    return -1L;
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
      super(response == null ? "Response error" : "HTTP " + response.code());
      this.response = response;
      this.customMessage = null;
    }

    public ResponseException(Response response, String customMessage)
    {
      super((response == null ? "Response error" : "HTTP " + response.code())
        + " - " + customMessage);
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
