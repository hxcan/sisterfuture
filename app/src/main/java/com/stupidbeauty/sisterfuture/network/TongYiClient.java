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

  // public TongYiClient()
  // {
  //   this(new ModelAccessPointManager(), new ToolManager());
  // }

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
        // .connectTimeout(1, TimeUnit.SECONDS)
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
      String api_key = "sk-5f7c2c5ae9e741d99b5b431256a5ad0d";

      try
      {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", accessPointManager.getCurrentModelName());
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

            // ✅ 根据参数决定是否注入 tools
    if (includeTools)
    {

        // 🔥 注入工具定义（基于 Tool.shouldInclude() 动态过滤）
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

          // ✅ 新增：打印工具定义用于调试
          Log.d(TAG, "Sending tools definition (filtered by shouldInclude):\n" + toolsArray.toString(2));
        }
        }


        RequestBody body = RequestBody.create
        (
          MediaType.parse("application/json; charset=utf-8"),
          requestBody.toString()
        );

        Log.d(TAG, CodePosition.newInstance().toString() + ", request body length: " + requestBody.toString().length());

        Request request = new Request.Builder()
          .url(accessPointManager.getCurrentBaseUrl() + accessPointManager.getCurrentChatEndpoint())
          .addHeader("Authorization", "Bearer " + api_key)
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
            // listener.onError(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException
          {
            if (!response.isSuccessful())
            {
              Log.e(TAG, "Unexpected code " + response);
              accessPointManager.reportCurrentAccessPointUnavailable();
              listener.onError(new AccessPointUnavailableException("Error content reading failed, access point unavailable"));
              listener.onError(new ResponseException(response));
              Log.e(TAG, "Content: \n" );
              ResponseBody responseBody = response.body();
              printErrorContent(responseBody.charStream(), listener);
            }
            else
            {
              ResponseBody responseBody = response.body();
              if (responseBody != null)
              {
                processSseStream(responseBody.charStream(), listener, accessPointManager, onStreamComplete);
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

  private static void printErrorContent(java.io.Reader reader, OnResponseListener listener)
  {
    try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader))
    {
      String line;
      while ((line = bufferedReader.readLine()) != null)
      {
        Log.e(TAG, " " + line);
      }
    }
    catch (IOException e)
    {
      listener.onError(e);
    }
    catch (IllegalStateException e) // ← 新增：捕获流已关闭的异常
    {
      // 流已被关闭（如 response body 已读取过），属于预期情况，静默忽略或记录
      Log.e(TAG, "Reader closed, cannot read error content: " + e.getMessage());
      // 不调用 listener.onError，避免二次崩溃
    }
  }

private static void processSseStream(java.io.Reader reader, OnResponseListener listener, ModelAccessPointManager accessPointManager, Runnable onStreamComplete)
{
  try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader))
  {
    String line;
    boolean isDone = false;

    while ((line = bufferedReader.readLine()) != null)
    {
      Log.d(TAG, CodePosition.newInstance().toString() + ", line: " + line);

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

    // ✅ 只有在 [DONE] 出现后才触发完成回调
    if (isDone && onStreamComplete != null)
    {
      onStreamComplete.run();
    }
  }
  catch (IOException e)
  {
    accessPointManager.reportCurrentAccessPointUnavailable();
    listener.onError(new AccessPointUnavailableException("Stream reading failed, access point unavailable", e));
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

    public ResponseException(Response response)
    {
      super("HTTP request failed with code: " + response.code());
      this.response = response;
    }

    public Response getResponse()
    {
      return response;
    }
  }
}
