package com.stupidbeauty.sisterfuture.network;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.Tool;

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
      ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
      String apiKey = null;
      
      if (currentAccessPoint != null) {
          apiKey = currentAccessPoint.getApiKey();
      }
      
      String effectiveApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
          
      Log.d(TAG, "Using API Key: " + (apiKey != null && !apiKey.isEmpty() ? "Access Point" : "No auth"));

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
        
        Log.d(TAG, "URL: " + fullUrl);
        Log.d(TAG, "Body length: " + requestBody.toString().length());

        if (baseUrl.endsWith("/") && endpoint.startsWith("/")) {
            Log.w(TAG, "⚠️ Double slash in URL!");
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
            Log.e(TAG, "Request failed: " + e.getMessage());
            accessPointManager.reportCurrentAccessPointUnavailable();
            listener.onError(new AccessPointUnavailableException("Current access point is unavailable", e));
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            if (!response.isSuccessful())
            {
              String errorBody = "";
              try {
                errorBody = response.body().string();
                Log.e(TAG, "HTTP " + response.code() + ": " + errorBody);
              } catch (Exception e) {
                Log.e(TAG, "Failed to read error body: " + e.getMessage());
              }
              
              accessPointManager.reportCurrentAccessPointUnavailable();
              listener.onError(new AccessPointUnavailableException("Error: " + errorBody));
              listener.onError(new ResponseException(response, errorBody));
            }
            else
            {
              ResponseBody responseBody = response.body();
              if (responseBody != null)
              {
                processSSEStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete);
              }
            }
          }
        });
      }
      catch (Exception e)
      {
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

    while ((line = bufferedReader.readLine()) != null)
    {
      if (!htmlChecked)
      {
        htmlChecked = true;
        firstLineBuffer.append(line);
        
        String preview = firstLineBuffer.length() > 500 ? firstLineBuffer.substring(0, 500) : firstLineBuffer.toString();
        if (isHtmlResponse(preview))
        {
          Log.e(TAG, "API returned HTML page");
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
            listener.onResponse(dataPart);
          }
          else
          {
            isDone = true;
          }
        }
      }
    }

    if (isDone && onStreamComplete != null)
    {
      onStreamComplete.run();
    }
  }
  catch (IOException e)
  {
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