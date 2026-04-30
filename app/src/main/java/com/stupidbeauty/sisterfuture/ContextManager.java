package com.stupidbeauty.sisterfuture.network;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

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
  private final Map<Long, String> requestIdToMessageIdMap = new HashMap<>();
  private final AtomicLong requestIdCounter = new AtomicLong(0);

  public TongYiClient(ModelAccessPointManager accessPointManager, ToolManager toolManager)
  {
    this.accessPointManager = accessPointManager;
    this.toolManager = toolManager;
    this.networkRequester = new OkHttpNetworkRequester(this.accessPointManager, this.toolManager, this);
    
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
              // 🔧 #774530570947 新增：检查 arguments 是否包含非法的非 ASCII 字符
              if (hasInvalidNonAsciiChars(argumentsStr))
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: arguments contains invalid non-ASCII characters");
                return;
              // 🔧 #774530570947 新增：检查 arguments 中是否存在未加引号的 Key
              if (hasUnquotedKeys(argumentsStr))
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: arguments contains unquoted keys");
                return;
              }
              }
        
        FileLogger.d(TAG, "🔒 [QUEUE_DONE] 请求 #" + totalRequests + " (requestId=" + requestId + ") 完成 | 执行时间：" + executionTime + "ms | 总耗时：" + (waitTime + executionTime) + "ms");
        
        // 每 10 个请求输出一次统计
        if (totalRequests % 10 == 0) {
          long avgWaitTime = totalWaitTimeMs.get() / totalRequests;
          int highWaterMark = queueSizeHighWaterMark.get();
          FileLogger.i(TAG, "🔒 [QUEUE_STATS] 队列统计 | 总请求数：" + totalRequests + " | 平均等待时间：" + avgWaitTime + "ms | 队列最大长度：" + highWaterMark);
        }
      } catch (Exception e) {
        FileLogger.e(TAG, "🔒 [QUEUE_ERROR] 请求 #" + totalRequests + " (requestId=" + requestId + ") 执行失败", e);
        throw e;
      }
    });
    
    if (!queued) {
      FileLogger.e(TAG, "🔒 [QUEUE_REJECTED] 请求 #" + totalRequests + " (requestId=" + requestId + ") 被队列拒绝（队列已满）");
      listener.onError(new IllegalStateException("请求队列已满，无法接受新请求"));
    }
  }
  
  // ✅ 保留旧方法，兼容现有调用（默认 messageId 为 null）
  public void sendChatRequest(JSONArray messages, boolean includeTools , OnResponseListener listener, Runnable onStreamComplete)
  {
    sendChatRequest(messages, includeTools, listener, onStreamComplete, null);
  private boolean hasUnquotedStringValues(String jsonStr)
  {
    // Step 1: Remove all properly quoted strings (including escaped quotes)
    String withoutQuotedStrings = jsonStr.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    
    // Step 2: Look for pattern: : followed by whitespace and an identifier
    // Identifiers start with letter/underscore, followed by alphanumeric/underscore
    Pattern pattern = Pattern.compile(":\\s*([a-zA-Z_][a-zA-Z0-9_]*)");
    Matcher matcher = pattern.matcher(withoutQuotedStrings);
    
    while (matcher.find())
    {
      String identifier = matcher.group(1);
      
      // Step 3: Check if it's NOT a valid JSON keyword
      if (!identifier.equals("true") && 
          !identifier.equals("false") && 
          !identifier.equals("null"))
      {
        FileLogger.d(TAG, "[hasUnquotedStringValues] Found unquoted identifier: " + identifier);
        return true;
      }
    }
    
    return false;
  }
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

    public OkHttpNetworkRequester(ModelAccessPointManager accessPointManager, ToolManager toolManager, TongYiClient tongYiClient)
      this.accessPointManager = accessPointManager;
      this.toolManager = toolManager;
      this.tongYiClient = tongYiClient;
    }

    @Override
  // 🔧 #774530570947 新增：检查 arguments 是否包含非法的非 ASCII 字符（如 Base64 中文）
  private boolean hasInvalidNonAsciiChars(String jsonStr)
  {
    if (jsonStr == null || jsonStr.isEmpty())
    {
      return false;
    }
    
    // 检查是否包含非 ASCII 字符（除了常见的 Unicode 转义序列 \uXXXX）
    for (int i = 0; i < jsonStr.length(); i++)
    {
      char c = jsonStr.charAt(i);
      if (c < 32 || c > 126)
      {
        if (c == '\\' && i + 5 < jsonStr.length() && 
            jsonStr.charAt(i+1) == 'u' &&
            isHexDigit(jsonStr.charAt(i+2)) &&
            isHexDigit(jsonStr.charAt(i+3)) &&
            isHexDigit(jsonStr.charAt(i+4)) &&
            isHexDigit(jsonStr.charAt(i+5)))
        {
          i += 5;
          continue;
        }
        FileLogger.d(TAG, "[hasInvalidNonAsciiChars] Found invalid non-ASCII char at position " + i + ": " + (int)c);
        return true;
      }
    }
    return false;
  }
  
  private boolean isHexDigit(char c)
  {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }
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
      
      // 🔗 记录请求信息
      FileLogger.d(NETWORK_TAG, "🔗 [REQUEST_INFO] requestId=" + requestId + " | messageId=" + reservedMessageId);

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
        // === 🔒 #5033 新增：调试完整 tool_calls 结构 ===
        FileLogger.d(NETWORK_TAG, "🔍 [TOOL_CALLS_DEBUG] Checking messages for tool_calls...");
        for (int i = 0; i < messages.length(); i++) {
            try {
                JSONObject msg = messages.getJSONObject(i);
                if (msg.has("tool_calls")) {
                    JSONArray toolCalls = msg.getJSONArray("tool_calls");
                    FileLogger.d(NETWORK_TAG, "🔍 [TOOL_CALLS_DEBUG] Message #" + i + " has " + toolCalls.length() + " tool_calls");
                    for (int j = 0; j < toolCalls.length(); j++) {
                        JSONObject tc = toolCalls.getJSONObject(j);
                        String id = tc.optString("id", "unknown");
                        String type = tc.optString("type", "unknown");
                        JSONObject function = tc.optJSONObject("function");
                        String funcName = function != null ? function.optString("name", "unknown") : "null";
                        String args = function != null ? function.optString("arguments", "{}") : "{}";
                        
                        FileLogger.d(NETWORK_TAG, "🔍 [TOOL_CALLS_DEBUG] Tool Call #" + j + ":");
                        FileLogger.d(NETWORK_TAG, "  - id: " + id);
                        FileLogger.d(NETWORK_TAG, "  - type: " + type);
                        FileLogger.d(NETWORK_TAG, "  - function.name: " + funcName);
                        FileLogger.d(NETWORK_TAG, "  - function.arguments: " + args);
                        
                        // 尝试验证 arguments
                        try {
                            new JSONObject(args);
                            FileLogger.d(NETWORK_TAG, "  - arguments JSON Valid: true");
                        } catch (Exception e) {
                            FileLogger.e(NETWORK_TAG, "  - arguments JSON Invalid! Error: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                FileLogger.e(NETWORK_TAG, "🔍 [TOOL_CALLS_DEBUG] Error processing message #" + i, e);
            }
        }
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", accessPointManager.getCurrentModelName());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        requestBody.put("enable_thinking", false);
        
        // === 🔒 #新功能：为所有模型添加 Minimax 思考控制参数 ===
        // 即使不是 Minimax 模型也加上这些参数，让 API 自行判断是否支持
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
        // === 🔒 #5032 新增：调试工具调用参数 JSON 格式 ===
        try {
          String bodyJsonStr = requestBody.toString();
          JSONObject bodyObj = new JSONObject(bodyJsonStr);
          if (bodyObj.has("messages")) {
            JSONArray msgs = bodyObj.getJSONArray("messages");
            for (int i = 0; i < msgs.length(); i++) {
              JSONObject msg = msgs.getJSONObject(i);
              if (msg.has("tool_calls")) {
                JSONArray toolCalls = msg.getJSONArray("tool_calls");
                for (int j = 0; j < toolCalls.length(); j++) {
                  JSONObject tc = toolCalls.getJSONObject(j);
                  String arguments = tc.optString("arguments", "{}");
                  FileLogger.d(NETWORK_TAG, "🔍 [JSON_DEBUG] Tool Call Arguments Raw: " + arguments);
                  // 尝试验证 arguments 是否是合法 JSON
                  try {
                    new JSONObject(arguments);
                    FileLogger.d(NETWORK_TAG, "🔍 [JSON_DEBUG] Arguments JSON Valid: true");
                  } catch (Exception e) {
                    FileLogger.e(NETWORK_TAG, "🔍 [JSON_DEBUG] Arguments JSON Invalid! Error: " + e.getMessage());
                    FileLogger.e(NETWORK_TAG, "🔍 [JSON_DEBUG] Failed Arguments Content: " + arguments);
                  }
                }
              }
            }
          }
        } catch (Exception e) {
          FileLogger.e(NETWORK_TAG, "🔍 [JSON_DEBUG] Main Body JSON Parse Failed: " + e.getMessage());
        }

        client.newCall(request).enqueue(new Callback()
        {
          @Override
          public void onFailure(Call call, IOException e)
          {
            FileLogger.e(NETWORK_TAG, "🌐 [HTTP_FAILURE] 请求失败 (requestId=" + requestId + "): " + e.getMessage() + " | 线程：" + Thread.currentThread().getName());
            listener.onError(new AccessPointUnavailableException("Current access point is unavailable", e));
            // 🔗 清理映射
            tongYiClient.removeRequestIdMapping(requestId);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            int statusCode = response.code();
            FileLogger.d(NETWORK_TAG, "🌐 [HTTP_RESPONSE] HTTP Response Status: " + statusCode + " (requestId=" + requestId + ") | 线程：" + Thread.currentThread().getName());
            
            if (!response.isSuccessful())
            {
              String errorBody = "";
              try {
                errorBody = response.body().string();
                FileLogger.e(NETWORK_TAG, "HTTP " + statusCode + " Error Body: " + errorBody);
                
                String errorPreview = errorBody.length() > 2000 
                    ? errorBody.substring(0, 2000) + "..." 
                    : errorBody;
                FileLogger.e(NETWORK_TAG, "Error Body Preview: " + errorPreview);
                
                // ✅ #4823 HTTP 400 → 上下文超长
                if (statusCode == 400 && ContextLengthUtils.isContextLengthError(errorBody)) {
                  FileLogger.w(NETWORK_TAG, "🔍 检测到上下文超长错误（HTTP 400），不切换接入点 (requestId=" + requestId + ")");
                  listener.onError(new ResponseException(response, errorBody));
                  // 🔗 清理映射
                  tongYiClient.removeRequestIdMapping(requestId);
                  return; // 只调用一次 onError()
                }
                
                // ✅ #4824 HTTP 429 → 限流错误
                if (statusCode == 429) {
                  FileLogger.w(NETWORK_TAG, "⚠️ 检测到 HTTP 429 限流错误，不切换接入点 (requestId=" + requestId + ")");
                  listener.onError(new RateLimitException(response, errorBody));
                  // 🔗 清理映射
                  tongYiClient.removeRequestIdMapping(requestId);
                  return; // 只调用一次 onError()
                }
                
                // ✅ 其他错误 (401/403/500/503) → 接入点不可用
                FileLogger.d(NETWORK_TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换 (requestId=" + requestId + ")");
                listener.onError(new AccessPointUnavailableException("Error: " + errorBody));
                // 🔗 清理映射
                tongYiClient.removeRequestIdMapping(requestId);
                return; // 只调用一次 onError()
              } catch (Exception e) {
                FileLogger.e(NETWORK_TAG, "Failed to read error body: " + e.getMessage());
                listener.onError(new AccessPointUnavailableException("Failed to read error body: " + e.getMessage()));
                // 🔗 清理映射
                tongYiClient.removeRequestIdMapping(requestId);
                return; // 只调用一次 onError()
              }
            }
            else
            {
              ResponseBody responseBody = response.body();
              if (responseBody != null)
              {
                FileLogger.d(NETWORK_TAG, "🌐 [HTTP_STREAM_START] 开始处理 SSE 流式响应 (requestId=" + requestId + ") | 线程：" + Thread.currentThread().getName());
                processSSEStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete, requestId, reservedMessageId);
              }
            }
          }
        });
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
            accessPointManager.reportCurrentAccessPointUnavailable();
            listener.onError(new AccessPointUnavailableException("Invalid authorization header: " + errorMsg, e));
            // 🔗 清理映射
            tongYiClient.removeRequestIdMapping(requestId);
            return;
          }
        }
        
        e.printStackTrace();
        listener.onError(e);
        // 🔗 清理映射
        tongYiClient.removeRequestIdMapping(requestId);
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

  // 🔗 修改：添加 requestId 和 messageId 参数，用于日志记录
  private static void processSSEStream(java.io.Reader reader, OnResponseListener listener, ModelAccessPointManager accessPointManager, Runnable onStreamComplete, long requestId, String reservedMessageId)
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
            accessPointManager.reportCurrentAccessPointUnavailable();
            listener.onError(new ResponseException(null, "API returned HTML page"));
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
              try {
                JSONObject json = new JSONObject(dataPart);
                if (json.has("choices") && json.getJSONArray("choices").length() > 0) {
                  JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                  if (choice.has("delta")) {
                    JSONObject delta = choice.getJSONObject("delta");
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
              
              listener.onResponse(dataPart);
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
            }
          }
        }
      }

      if (isDone && onStreamComplete != null)
      {
        onStreamComplete.run();
        FileLogger.d(TAG, "流式响应处理完成，回调已执行 (requestId=" + requestId + ")");
      }
    }
    catch (IOException e)
    {
      FileLogger.e(TAG, "SSE 流处理失败 (requestId=" + requestId + ")", e);
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