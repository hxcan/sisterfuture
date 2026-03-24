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

public class TongYiClient
{
  private static final String TAG = "TongYiClient";
  private ModelAccessPointManager accessPointManager;
  private NetworkRequester networkRequester;
  private ToolManager toolManager;

  public TongYiClient(ModelAccessPointManager accessPointManager, ToolManager toolManager)
  {
    this.accessPointManager = accessPointManager;
    this.toolManager = toolManager;
    this.networkRequester = new OkHttpNetworkRequester(this.accessPointManager, this.toolManager);
    FileLogger.d(TAG, "TongYiClient 初始化完成");
  }

  public void sendChatRequest(JSONArray messages, boolean includeTools , OnResponseListener listener, Runnable onStreamComplete)
  {
    networkRequester.sendRequest(messages,includeTools, listener,onStreamComplete);
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
      FileLogger.d(NETWORK_TAG, "OkHttpNetworkRequester 初始化完成");
    }

    @Override
    public void sendRequest(JSONArray messages, boolean includeTools, OnResponseListener listener, Runnable onStreamComplete)
    {
      ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
      String apiKey = null;
      
      if (currentAccessPoint != null) {
          apiKey = currentAccessPoint.getApiKey();
          FileLogger.d(NETWORK_TAG, "[AP Info] 当前接入点名称：" + currentAccessPoint.getName());
          FileLogger.d(NETWORK_TAG, "[AP Info] 当前模型名称：" + currentAccessPoint.getModelName());
          FileLogger.d(NETWORK_TAG, "[AP Info] Base URL: " + currentAccessPoint.getBaseUrl());
      }
      
      String effectiveApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
          
      FileLogger.d(NETWORK_TAG, "Using API Key: " + (apiKey != null && !apiKey.isEmpty() ? "Access Point" : "No auth"));

      try
      {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", accessPointManager.getCurrentModelName());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

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
        
        // #4833 新增：记录请求体前 1000 字符用于调试
        String bodyPreview = requestBody.toString().length() > 1000 
            ? requestBody.toString().substring(0, 1000) + "..." 
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

        client.newCall(request).enqueue(new Callback()
        {
          @Override
          public void onFailure(Call call, IOException e)
          {
            FileLogger.e(NETWORK_TAG, "Request failed: " + e.getMessage());
            accessPointManager.reportCurrentAccessPointUnavailable();
            listener.onError(new AccessPointUnavailableException("Current access point is unavailable", e));
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            int statusCode = response.code();
            FileLogger.d(NETWORK_TAG, "HTTP Response Status: " + statusCode);
            
            if (!response.isSuccessful())
            {
              String errorBody = "";
              try {
                errorBody = response.body().string();
                FileLogger.e(NETWORK_TAG, "HTTP " + statusCode + " Error Body: " + errorBody);
                
                // #4833 新增：记录错误体前 2000 字符
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
                FileLogger.d(NETWORK_TAG, "开始处理 SSE 流式响应");
                processSSEStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete);
              }
            }
          }
        });
      }
      catch (Exception e)
      {
        FileLogger.e(NETWORK_TAG, "请求构建失败", e);
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
