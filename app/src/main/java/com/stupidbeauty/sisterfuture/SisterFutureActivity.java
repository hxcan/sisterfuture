package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.tool.ToolRegistry;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.ContextManager;
import com.stupidbeauty.sisterfuture.manager.SystemPromptManager;
import android.os.Handler;
import android.os.Looper;
import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import butterknife.OnClick;
import com.iflytek.cloud.SpeechRecognizer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import com.stupidbeauty.sisterfuture.bean.MessageItem;
import com.stupidbeauty.sisterfuture.bean.MessageType;
import com.stupidbeauty.sisterfuture.bean.Delta;
import com.stupidbeauty.sisterfuture.bean.Choice;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.ButterKnife;
import com.stupidbeauty.sisterfuture.R;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import java.util.List;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RadioGroup;
import net.tatans.tensorflowtts.utils.ThreadPoolManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import net.tatans.tensorflowtts.tts.TtsManager;
import org.json.JSONObject;
import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import android.Manifest;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.media.MediaScannerConnection;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.os.PowerManager;
import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.stupidbeauty.msclearnfootball.VoiceRecognizeResult;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.stupidbeauty.sisterfuture.network.TongYiClient;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.stupidbeauty.sisterfuture.network.TongYiClient.OnResponseListener;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.stupidbeauty.lanime.network.volley.MapUtils;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.lanime.Constants;
import com.stupidbeauty.lanime.callback.CommitTextCallback;
import com.stupidbeauty.lanime.callback.PhoneInformationCallback;
import com.stupidbeauty.sisterfuture.adapter.MessageAdapter;
import com.stupidbeauty.sisterfuture.manager.GuideManager;

public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
  private GuideManager guideManager ;

  private JSONObject firstToolCallDelta = null;
  private boolean isFirstToolCallProcessed = false;
  private ModelAccessPointManager modelAccessPointManager;
  private ToolManager toolManager;
  private MemoryManager memoryManager;

  private Map<Integer, String> indexToOriginalIdMap = new HashMap<>();
  private Map<String, Function> partialToolArgs = new HashMap<>();

  private static final Gson gson = new Gson();

  private ContextManager contextManager;
  private MessageAdapter messageAdapter;
  @BindView(R.id.articleListmy_recycler_view) RecyclerView articleListmyRecyclerView;
  private static final String DEFAULT_INPUT_TEXT = "君不见，黄河之水天上来，奔流到海不复回，君不见，高堂明镜悲白发，朝如青丝暮成雪，人生得意须尽欢，莫使金樽空对月";

  private StringBuilder accumulatedAnswer = new StringBuilder();

  private static final int PERMISSIONS_REQUEST =1;
  private TongYiClient tongYiClient;
  private boolean isTtsSpeaking = false;

  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
  private static final String PERMISSION_FINE_LOCATIN = Manifest.permission.ACCESS_FINE_LOCATION;
  private static final String PERMISSION_INSTALL_PACKAGE = Manifest.permission.REQUEST_INSTALL_PACKAGES;
  private MediaPlayer mediaPlayer;
  private boolean voiceEndDetected=false;

  private TextToSpeech mTts;

  private static final int LanServicePort =10471;
  private String voiceRecognizeResultString;
  private Vibrator vibrator;
  @BindView(R.id.sendButtonn2) Button sendButtonn2;
  @BindView(R.id.commandRecognizebutton2) Button commandRecognizebutton2;
  @BindView(R.id.thinking_overlay) TextView thinking_overlay;
  @BindView(R.id.progressBar) ProgressBar progressBar;
  int ret = 0;
  private static final String TAG="SisterFutureActivity";

  private SpeechRecognizer mIat;

	@BindView(R.id.volumeIndicatorprogressBar) ProgressBar volumeIndicatorprogressBar;
	@BindView(R.id.recognizeResulttextView) EditText recognizeResulttextView;

	@Override
  public void onInit(int arg0)
  {

  }

  private void accumulateToolCalls(List<ToolCall> calls)
  {
    for (ToolCall call : calls)
    {
      if (call == null || call.getFunction() == null) continue;

      int index = call.getIndex();

      if (call.getId() != null && !call.getId().trim().isEmpty())
      {
        indexToOriginalIdMap.put(index, call.getId());
      }

      String originalId = indexToOriginalIdMap.get(index);
      if (originalId == null)
      {
        originalId = "fallback_" + index + "_" + (call.getFunction().getName() != null ? call.getFunction().getName() : "");
        indexToOriginalIdMap.put(index, originalId);
      }

      Function func = call.getFunction();
      Function existing = partialToolArgs.get(originalId);

      if (existing == null)
      {
        existing = new Function();
        existing.setName(func.getName());
        existing.setArguments("");
      }

      String newChunk = func.getArguments() != null ? func.getArguments() : "";
      existing.setArguments(existing.getArguments() + newChunk);
      partialToolArgs.put(originalId, existing);
    }
  }
  
  private List<ToolCall> getFinalToolCalls()
  {
    List<ToolCall> result = new ArrayList<>();
    for (Map.Entry<String, Function> entry : partialToolArgs.entrySet())
    {
      int index = -1;
      for (Map.Entry<Integer, String> mapEntry : indexToOriginalIdMap.entrySet())
      {
        if (mapEntry.getValue().equals(entry.getKey()))
        {
          index = mapEntry.getKey();
          break;
        }
      }

      ToolCall call = new ToolCall();
      call.setId(entry.getKey());
      call.setType("function");
      call.setIndex(index);
      call.setFunction(entry.getValue());
      result.add(call);
    }
    return result;
  }

  private void clearAccumulatedToolCalls()
  {
    partialToolArgs.clear();
  }

  public void stopRecordbutton2()
  {
    vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate( 100);

    if (voiceEndDetected)
    {}
    else
    {
      mIat.stopListening();
    }

    volumeIndicatorprogressBar.setIndeterminate(true);
    volumeIndicatorprogressBar.setProgress(0);

    volumeIndicatorprogressBar.setVisibility(View.INVISIBLE);

    progressBar.setVisibility(View.VISIBLE);

    commandRecognizebutton2.setEnabled(false);
    commandRecognizebutton2.setVisibility(View.INVISIBLE);
  }
  
  public void commandRecognizebutton2startRecognize()
  {
    voiceEndDetected=false;

    vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate( 100);
    if (mIat==null)
    {
      mIat=SpeechRecognizer.createRecognizer(this,null);
    }

    if (!setParam())
    {
      return;
    }

    ret = mIat.startListening(mRecognizerListener);
    if (ret != ErrorCode.SUCCESS)
    {
      if (ret == ErrorCode.ERROR_COMPONENT_NOT_INSTALLED)
      {
      }
      else
      {
      }
    }
    volumeIndicatorprogressBar.setIndeterminate(false);
    progressBar.setVisibility(View.INVISIBLE);
    recognizeResulttextView.setText(R.string.empty);
  }

  public boolean setParam()
  {
    boolean result = false;
    String mEngineType = SpeechConstant.TYPE_CLOUD;
    mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
    mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

    if ("cloud".equalsIgnoreCase(mEngineType))
    {
      mIat.setParameter(SpeechConstant.DOMAIN,"iat");
      mIat.setParameter(SpeechConstant.LANGUAGE,"zh_cn");
      mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
      result = true;
    }

    mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
    mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/asr.wav");

    return result;
  }
  
  /**
   * 显示历史消息记录（重构版）
   * 修复：#3741 #3743 - 启动时正确显示工具调用和工具回复消息
   */
  private void displayExistingContext()
  {
    List<JSONObject> history = contextManager.getHistory();
    for (JSONObject msg : history)
    {
      String role = msg.optString("role");
      String content = msg.optString("content");
      String toolCallId = msg.optString("tool_call_id");
      JSONArray toolCalls = msg.optJSONArray("tool_calls");

      // 1. 工具回复消息 (tool_call_result) - #3743
      if ("tool".equals(role) && !toolCallId.isEmpty())
      {
        String toolName = msg.optString("name", "unknown_tool");
        String displayText = "🛠️ 工具调用结果：" + toolName + "\n" + content;
        messageAdapter.addMessage(new MessageItem(displayText, MessageType.TOOL_CALL_RESULT));
        Log.d(TAG, "✅ 启动时加载工具回复消息：ID=" + toolCallId + ", Name=" + toolName);
      }
      // 2. 用户消息
      else if ("user".equals(role) && !content.isEmpty())
      {
        messageAdapter.addMessage(new MessageItem(content, MessageType.USER));
      }
      // 3. AI 消息（含工具调用）- #3741
      else if ("assistant".equals(role))
      {
        // 3.1 包含工具调用的 AI 消息
        if (toolCalls != null && toolCalls.length() > 0)
        {
          StringBuilder callText = new StringBuilder("🛠️ 正在调用工具：\n");
          for (int i = 0; i < toolCalls.length(); i++)
          {
            try
            {
              JSONObject toolCall = toolCalls.getJSONObject(i);
              JSONObject func = toolCall.optJSONObject("function");
              if (func != null)
              {
                String toolName = func.optString("name", "unknown");
                callText.append("- `").append(toolName).append("`").append("\n");
              }
            }
            catch (JSONException e)
            {
              Log.e(TAG, "解析工具调用失败", e);
            }
          }
          messageAdapter.addMessage(new MessageItem(callText.toString(), MessageType.AI));
          Log.d(TAG, "✅ 启动时加载工具调用消息，数量=" + toolCalls.length());
        }
        // 3.2 普通 AI 文本消息
        else if (!content.isEmpty())
        {
          messageAdapter.addMessage(new MessageItem(content, MessageType.AI));
        }
      }
    }
    Log.d(TAG, "✅ 历史消息加载完成，总数=" + history.size());
  }

  public void sendMessageToSister(String message)
  {
    if (message == null || message.trim().isEmpty())
    {
      return;
    }

    messageAdapter.addMessage(new MessageItem(message, MessageType.USER));
    contextManager.addUserMessage(message);
    sendChatRequest();
  }
  
  @OnClick(R.id.sendButtonn2)
  public void sendButtonn2()
  {
    voiceRecognizeResultString = recognizeResulttextView.getText().toString();
    sendMessageToSister(voiceRecognizeResultString);
  }

  private void sendChatRequest() 
  {
    recognizeResulttextView.setText("");
    
    if (guideManager != null && guideManager.isEmptyAccessPointList())
    {
      guideManager.processWithGuideLogic(voiceRecognizeResultString, new GuideManager.ChatCallback()
      {
        @Override
        public void onResponse(String message)
        {
          runOnUiThread(() ->
          {
            messageAdapter.addMessage(new MessageItem(message, MessageType.AI));
            scrollToBottom();
            ttsSayReply(message);
          });
        }

        @Override
        public void onError(String error)
        {
          runOnUiThread(() ->
          {
            messageAdapter.addMessage(new MessageItem(error, MessageType.AI));
            scrollToBottom();
          });
        }
      });
      return;
    }

    sendChatRequestTongYi();
  }

  protected void reportOperationFail(String string)
  {
    Toast.makeText(SisterFutureApplication.getAppContext(), string, Toast.LENGTH_LONG).show();
  }

  private void showThinkingOverlay()
  {
    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        ModelAccessPoint currentAp = modelAccessPointManager.getCurrentAccessPoint();
        thinking_overlay.setText(currentAp.getName() + " is thinking...");

        thinking_overlay.setVisibility(View.VISIBLE);
        recognizeResulttextView.setEnabled(false);
        sendButtonn2.setEnabled(false);
        commandRecognizebutton2.setEnabled(false);
      }
    });
  }

  private void hideThinkingOverlay()
  {
    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        thinking_overlay.setVisibility(View.GONE);
        recognizeResulttextView.setEnabled(true);
        sendButtonn2.setEnabled(true);
        commandRecognizebutton2.setEnabled(true);
      }
    });
  }

  private void sendChatRequestTongYi()
  {
    Log.d(TAG, CodePosition.newInstance().toString());

    if (voiceRecognizeResultString != null && !voiceRecognizeResultString.isEmpty())
    {
      accumulatedAnswer.setLength(0);
      showThinkingOverlay();

      String currentApName = modelAccessPointManager.getCurrentAccessPoint().getName();
      JSONArray historyArray = contextManager.getMessagesArray();
      JSONArray messagesArray = new JSONArray();

      try
      {
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this);
        systemMsg.put("content", enhancedSystemPrompt);
        messagesArray.put(systemMsg);

        for (int i = 0; i < historyArray.length(); i++)
        {
          String messageContent = historyArray.getJSONObject(i).optString("content");
          String messageRole = historyArray.getJSONObject(i).optString("role");
          String toolCAllId = historyArray.getJSONObject(i).optString("tool_call_id");

          if (messageRole.equals("assistant") || messageRole.equals("user"))
          {
            String[] parts = messageContent.split("\n");
            if (parts.length >1)
            {
              String maxWidthStr = parts[0];
              messageContent = maxWidthStr + " ...";
            }
          }

          if ((messageContent.isEmpty()) && (messageRole.equals("assistant")) )
          {
            messageContent = historyArray.getJSONObject(i).toString();
          }

          Log.d(TAG, CodePosition.newInstance().toString() + ", adding message with role: " + messageRole + ", content: " + messageContent + ", tool call id: " + toolCAllId);

          messagesArray.put(historyArray.getJSONObject(i));
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();

        try
        {
          messagesArray = new JSONArray();
          String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this);

          messagesArray.put(new JSONObject().put("role", "system").put("content", enhancedSystemPrompt));
          messagesArray.put(new JSONObject().put("role", "user").put("content", voiceRecognizeResultString));
        }
        catch (Exception ignored)
        {
        }
      }

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          parseTongYiResponse(response);
        }

        @Override
        public void onError(Exception error)
        {
          Log.e(TAG, CodePosition.newInstance().toString() + ", Error sending request to TongYi", error);
          hideThinkingOverlay();

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            Log.d(TAG, "接入点不可用，正在自动重试...\n");
            isAccessPointUnavailable = true;
          }
          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            try
            {
              String errorBody = response.body().string();
              Log.e(TAG, "Error body: " + errorBody);
              
              if (isHtmlResponse(errorBody))
              {
                Log.e(TAG, "API 返回 HTML 页面而非 JSON，跳过 Gson 解析，防止崩溃。");
                runOnUiThread(() ->
                {
                  messageAdapter.addMessage(new MessageItem("API 服务异常：返回了 HTML 页面而非 JSON。请检查接入点配置。", MessageType.AI));
                  scrollToBottom();
                });
                return;
              }
              
              TongYiResponse errResp = new Gson().fromJson(errorBody, TongYiResponse.class);
              if (errResp != null && errResp.getError() != null)
              {
                if (isContextLengthError(errResp.getError().getMessage()))
                {
                  contextManager.decreaseMaxRounds();
                }
              }
            }
            catch (IOException e)
            {
              Log.e(TAG, "Error reading response body: " + e.getMessage());
            }
            catch (IllegalStateException e)
            {
              Log.e(TAG, "Error reading response body: " + e.getMessage());
            }
          }
          else
          {
            Log.e(TAG, "未知异常，不触发重试：" + error.getMessage());
          }

          if (isAccessPointUnavailable)
          {
            sendChatRequestTongYi();
          }
        }
      },
      () ->
        {
        }
      );
    }
    else
    {
      Log.w(TAG, "Voice recognition result is empty or null.\n");
    }
  }

  private boolean isHtmlResponse(String content)
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

  protected void parseTongYiResponse(String jsonString)
  {
    Log.d(TAG, "JSON Answer: " + jsonString);
    try
    {
      TongYiResponse response = new Gson().fromJson(jsonString, TongYiResponse.class);

      if (response != null && response.getError() != null)
      {
        String errorMessage = response.getError().getMessage();
        boolean isContextTooLong = isContextLengthError(errorMessage);

        runOnUiThread(() ->
        {
          messageAdapter.addMessage(new MessageItem(errorMessage, MessageType.AI));
          scrollToBottom();
          ttsSayReply(errorMessage);
          contextManager.addAssistantMessage(errorMessage);
        });

        if (isContextTooLong)
        {
          contextManager.decreaseMaxRounds();
          sendChatRequestTongYi();
        }
        return;
      }

      if (response == null || response.getChoices() == null || response.getChoices().isEmpty())
      {
        Log.e(TAG, "Parsed response is null or choices empty");
        return;
      }

      Choice choice = response.getChoices().get(0);
      Delta delta = choice.getDelta();

      if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty())
      {
        accumulateToolCalls(delta.getToolCalls());
      }

      if ("tool_calls".equals(choice.getFinishReason()))
      {
        runOnUiThread(() ->
        {
          try
          {
            List<ToolCall> finalCalls = getFinalToolCalls();

            if (finalCalls == null || finalCalls.isEmpty())
            {
              Log.w(TAG, "No valid tool calls generated, skipping execution.");
              return;
            }

            JSONObject assistantMessage = new JSONObject();
            assistantMessage.put("role", "assistant");

            JSONArray toolCallsArray = new JSONArray();
            java.util.Map<String, JSONObject> pendingResults = new java.util.HashMap<>();

            for (ToolCall call : finalCalls)
            {
              if (call == null || call.getFunction() == null) continue;

              String toolName = call.getFunction().getName();
              String argsJsonStr = call.getFunction().getArguments();
              String toolCallId = call.getId();

              if (toolName == null || toolCallId == null)
              {
                Log.w(TAG, "Invalid tool call: name or id is null");
                continue;
              }

              if (argsJsonStr == null || argsJsonStr.trim().isEmpty())
              {
                argsJsonStr = "{}";
              }

              JSONObject args = new JSONObject(argsJsonStr);

              JSONObject toolCallObject = new JSONObject();
              toolCallObject.put("id", toolCallId);
              toolCallObject.put("type", "function");

              JSONObject functionObject = new JSONObject();
              functionObject.put("name", toolName);
              functionObject.put("arguments", argsJsonStr);
              toolCallObject.put("function", functionObject);
              toolCallsArray.put(toolCallObject);

              if (toolManager.isToolAsync(toolName))
              {
                toolManager.executeToolAsync(toolName, args, new Tool.OnResultCallback()
                {
                  @Override
                  public void onResult(JSONObject result)
                  {
                    synchronized (pendingResults)
                    {
                      try
                      {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", toolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", result);
                        pendingResults.put(toolCallId, wrapper);
                      }
                      catch (Exception e)
                      {
                        Log.e(TAG, "Failed to wrap async result", e);
                      }

                      if (pendingResults.size() == toolCallsArray.length())
                      {
                        postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                      }
                    }
                  }

                  @Override
                  public void onError(Exception e)
                  {
                    Log.e(TAG, "Async tool failed: " + toolName, e);
                    postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                  }
                });
              }
              else
              {
                JSONObject toolResult = new JSONObject();

                try
                {
                  toolResult = toolManager.executeTool(toolName, args);
                }
                catch (IllegalArgumentException e)
                {
                  JSONObject errorResult = new JSONObject();
                  errorResult.put("error", e.getMessage());
                  errorResult.put("tool_name", toolName);
                  errorResult.put("request", args.toString());
                  toolResult = errorResult;
                }
                catch (Exception e)
                {
                  JSONObject errorResult = new JSONObject();
                  errorResult.put("error", "工具执行出错：" + e.getMessage());
                  errorResult.put("tool_name", toolName);
                  errorResult.put("request", args.toString());
                  errorResult.put("stack_trace", android.util.Log.getStackTraceString(e));
                  toolResult = errorResult;
                }

                JSONObject wrapper = new JSONObject();
                wrapper.put("id", toolCallId);
                wrapper.put("name", toolName);
                wrapper.put("result", toolResult);
                pendingResults.put(toolCallId, wrapper);
              }
            }

            assistantMessage.put("tool_calls", toolCallsArray);
            contextManager.addRawMessage(assistantMessage);
            contextManager.increaseMaxRounds();

            runOnUiThread(() ->
            {
              StringBuilder callText = new StringBuilder("🛠️ 正在调用工具：\n");
              for (ToolCall call : finalCalls)
              {
                if (call != null && call.getFunction() != null)
                {
                  String toolName = call.getFunction().getName();
                  callText.append("- `").append(toolName).append("`").append("\n");
                }
              }

              messageAdapter.addMessage(new MessageItem(callText.toString(), MessageType.AI));
              scrollToBottom();
            });

            if (pendingResults.size() == toolCallsArray.length())
            {
              postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
            }
          }
          catch (Exception e)
          {
            Log.e(TAG, "Error handling tool_calls", e);
          }
        });
        return;
      }

      String answerIncrement = (delta != null && delta.getContent() != null) ? delta.getContent() : "";
      boolean isNewMessage = (accumulatedAnswer.length() == 0 && !answerIncrement.isEmpty());
      accumulatedAnswer.append(answerIncrement);

      if (isNewMessage)
      {
        runOnUiThread(() ->
        {
          messageAdapter.addMessage(new MessageItem(accumulatedAnswer.toString(), MessageType.AI));
        });
      }
      else
      {
        int lastPosition = messageAdapter.getItemCount() -1;
        runOnUiThread(() ->
        {
          messageAdapter.updateAiMessage(lastPosition, accumulatedAnswer.toString());
          scrollToBottom();
        });
      }

      if (!response.getChoices().isEmpty() && "stop".equals(response.getChoices().get(0).getFinishReason()))
      {
        runOnUiThread(() ->
        {
          String fullAnswer = accumulatedAnswer.toString();
          ttsSayReply(fullAnswer);
          contextManager.addAssistantMessage(fullAnswer);
          contextManager.increaseMaxRounds();
        });
      }
    }
    catch (Exception e)
    {
      Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
    }
  }

  private void postProcessToolResults(java.util.Map<String, JSONObject> pendingResults,
                                    JSONObject assistantMessage,
                                    JSONArray toolCallsArray)
  {
    runOnUiThread(() ->
    {
      try
      {
        for (int i = 0; i < toolCallsArray.length(); i++)
        {
          JSONObject call = toolCallsArray.getJSONObject(i);
          String id = call.getString("id");
          JSONObject wrapper = pendingResults.get(id);

          if (wrapper != null)
          {
            String name = wrapper.getString("name");
            JSONObject result = wrapper.getJSONObject("result");

            contextManager.addToolMessage(id, name, result.toString());
            Log.d(TAG, "✅ Tool message added: ID=" + id + ", Name=" + name);
            messageAdapter.addMessage(
              new MessageItem(
                "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
                MessageType.TOOL_CALL_RESULT
              )
            );
          }
        }

        clearAccumulatedToolCalls();

        int messagesAmount = contextManager.getHistory().size();
        Log.d(TAG, "Final messages array before sending request: amount: " + messagesAmount);
        int startEndMessagsOutputAmount = 5;
        boolean outputDotsDone = false;

        for (int i = 0; i < messagesAmount; i++)
        {
          if ((i >= startEndMessagsOutputAmount) && (i < (messagesAmount-startEndMessagsOutputAmount) ))
          {
            if (!outputDotsDone)
            {
              Log.d(TAG, "  [...] ");
              outputDotsDone = true;
            }
          }
          else
          {
            JSONObject msg = contextManager.getHistory().get(i);
            if (msg!=null)
            {
              Log.d(TAG, "  [" + i + "] " + msg.toString(2));
            }
          }
        }

        sendChatRequestTongYi();
      }
      catch (Exception e)
      {
        Log.e(TAG, "Error in postProcessToolResults", e);
      }
    });
  }

  private boolean isContextLengthError(String errorMessage)
  {
    if (errorMessage == null) return false;
    return errorMessage.contains("Range of input length should be") ||
           errorMessage.contains("context length") ||
           errorMessage.contains("exceeds the available context size") ||
           errorMessage.contains("exceeds maximum context length");
  }

  private void scrollToBottom()
  {
    articleListmyRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() -1);
  }

  @Override
  public void onBackPressed()
  {
    if (null!=mTts)
    {
      mTts.shutdown();
    }

    super.onBackPressed();
  }

  private void ttsSayReply(final String text)
  {
    ttsByFindroidTts(text);
  }

  private void ttsByFindroidTts(String text)
  {
    ThreadPoolManager.getInstance().execute(() ->
    {
      float speed = 1.0F;

      String inputText = text;
      if (TextUtils.isEmpty(inputText))
      {
        inputText = DEFAULT_INPUT_TEXT;
      }
      TtsManager.getInstance().speak(inputText, speed, true);
    });
  }

  private final RecognizerListener mRecognizerListener=new RecognizerListener()
	{
		@Override
		public void onVolumeChanged(int i, byte[] bytes)
    {
      volumeIndicatorprogressBar.setProgress(i);
		}

		@Override
		public void onBeginOfSpeech()
    {
      voiceRecognizeResultString="";
      volumeIndicatorprogressBar.setVisibility(View.VISIBLE);
		}

		@Override
		public void onEndOfSpeech()
    {
      volumeIndicatorprogressBar.setVisibility(View.INVISIBLE);
      voiceEndDetected=true;
		}

		@Override
		public void onResult(RecognizerResult recognizerResult, boolean b)
    {
      progressBar.setVisibility(View.INVISIBLE);
      commandRecognizebutton2.setVisibility(View.VISIBLE);
      commandRecognizebutton2.setEnabled(true);
      String text=recognizerResult.getResultString();

      Gson gson=new Gson();
      VoiceRecognizeResult voiceRecognizeResult=gson.fromJson(text, VoiceRecognizeResult.class);
      String saidText=voiceRecognizeResult.getSaidText();

      recognizeResulttextView.append(saidText);
      voiceRecognizeResultString=voiceRecognizeResultString+saidText;

      boolean isLast=voiceRecognizeResult.isLs();

      if (isLast) 
      {
        sendMessageToSister(voiceRecognizeResultString);
      }
    }

    @Override
		public void onError(SpeechError speechError)
		{
      commandRecognizebutton2.setVisibility(View.VISIBLE);
      commandRecognizebutton2.setEnabled(true);
      progressBar.setVisibility(View.INVISIBLE);
      String errorText=speechError.getErrorDescription();

      recognizeResulttextView.setText(errorText+",error code:"+speechError.getErrorCode());
		}

		@Override
		public void onEvent(int i, int i1, int i2, Bundle bundle)
		{
    }
	};

  private final View.OnTouchListener commandRecognizeButtonTouchListener=new View.OnTouchListener()
  {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
      switch (event.getAction())
      {
        case MotionEvent.ACTION_DOWN:
          commandRecognizebutton2startRecognize();
          break;

        case MotionEvent.ACTION_UP:
          stopRecordbutton2();
          break;
      }
      return true;
   }
  };

  private void connectSignals()
  {
    commandRecognizebutton2.setOnTouchListener(commandRecognizeButtonTouchListener);
  }

  private void startHttpServer()
  {
    AsyncHttpServer server=new AsyncHttpServer();
    CommitTextCallback commitTextCallback=new CommitTextCallback();
    server.get("/commitText/", commitTextCallback);
    PhoneInformationCallback phoneInformationCallback=new PhoneInformationCallback();
    server.get("/phoneInformation/", phoneInformationCallback);
    server.listen(LanServicePort);
  }

  private static String buildEnhancedSystemPrompt(ToolManager toolManager, Context context)
  {
    SystemPromptManager promptManager = SystemPromptManager.getInstance(context);

    StringBuilder promptBuilder = new StringBuilder();

    promptBuilder.append(promptManager.getCurrentPrompt());
    promptBuilder.append("\n\n");

    List<Tool> tools = toolManager.getRegisteredTools();
    if (!tools.isEmpty())
    {
      promptBuilder.append("你可以使用以下工具来获取实时信息，请在需要时调用，不要自行编造：\n");

      for (Tool tool : tools)
      {
        if (!tool.shouldInclude()) continue;

        String name = tool.getName();
        String description = "（无描述）";

        try
        {
          JSONObject definition = tool.getDefinition();
          if (definition.has("function"))
          {
            JSONObject funcDef = definition.getJSONObject("function");
            if (funcDef.has("description") && !funcDef.isNull("description"))
            {
              description = funcDef.getString("description");
            }
          }
        }
        catch (Exception e)
        {
          Log.e("SisterFutureActivity", "Failed to extract description for tool: " + name, e);
        }

        promptBuilder.append("- ").append(name).append(":").append(description).append("\n");
      }

      for (Tool tool : tools)
      {
        String enhancement = tool.getSystemPromptEnhancement(context);
        if (enhancement != null && !enhancement.trim().isEmpty())
        {
          promptBuilder.append("\n【").append(tool.getName()).append(" 特别约束】")
                      .append(enhancement).append("\n");
        }
      }

      promptBuilder.append("\n/no_think\n");
    }
    return promptBuilder.toString();
  }

  @Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.sister_future);

    initServices();
    initData();
    initTools();
    initView();
    checkPermission();
    connectSignals();
    displayExistingContext();
    
    // #4713 冷启动时自动滚动到聊天记录最底部
    if (savedInstanceState == null)
    {
      articleListmyRecyclerView.post(() -> 
      {
        scrollToBottom();
        Log.d(TAG, "#4713 冷启动完成，已自动滚动到最新消息");
      });
    }
	}

  private void initServices()
  {
    TtsManager.getInstance().init(this);
    mTts=new TextToSpeech(this,this);
    registerBroadcastReceiver();
    startHttpServer();
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
  }

  private void initData()
  {
    contextManager = new ContextManager(this);
    modelAccessPointManager = new ModelAccessPointManager(this);
    memoryManager = new MemoryManager(this);
  }

  /**
   * 初始化工具管理器
   * 重构：委托给 ToolRegistry 集中管理工具注册 (#4670)
   */
  private void initTools()
  {
    toolManager = new ToolManager();
    
    ToolRegistry.registerAll(
      toolManager,
      contextManager,
      modelAccessPointManager,
      memoryManager,
      this
    );
  }

  private void initView()
  {
    ButterKnife.bind(this);
    initializeMsc();
    checkPermission();
    messageAdapter = new MessageAdapter();
    articleListmyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    articleListmyRecyclerView.setAdapter(messageAdapter);

    recognizeResulttextView.setOnEditorActionListener(new TextView.OnEditorActionListener()
    {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
      {
        if (actionId == EditorInfo.IME_ACTION_SEND)
        {
          voiceRecognizeResultString = recognizeResulttextView.getText().toString();
          sendChatRequest();
          return true;
        }
        return false;
      }
    });

    tongYiClient = new TongYiClient(modelAccessPointManager, toolManager);
    guideManager = new GuideManager(this, modelAccessPointManager, toolManager);

    String question = getIntent().getStringExtra("question");
    if (question != null)
    {
      sendMessageToSister(question);
    }
  }

  private boolean hasPermission()
  {
    boolean result=false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    {
      ArrayList<String> articleInfoArrayList = new ArrayList<>();
        
      articleInfoArrayList.add(PERMISSION_STORAGE);
      articleInfoArrayList.add(PERMISSION_RECORD_AUDIO);
      articleInfoArrayList.add(PERMISSION_FINE_LOCATIN);
        
      for(String permissionString: articleInfoArrayList)
      {
        Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString);
        result=(checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED);
          
        if (!result)
        {
          Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString + ", no permission");
          break;
        }
      }
    }
    else
    {
      result=true;
    }

    return result;
  }

  private void requestPermission()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    {
      if ( shouldShowRequestPermissionRationale(PERMISSION_STORAGE)  || shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO) || shouldShowRequestPermissionRationale(PERMISSION_FINE_LOCATIN)  || shouldShowRequestPermissionRationale(PERMISSION_INSTALL_PACKAGE))
      {
        Toast.makeText(this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      Log.d(TAG, CodePosition.newInstance().toString() );

      requestPermissions(new String[] {PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO, PERMISSION_FINE_LOCATIN}, PERMISSIONS_REQUEST);
    }
  }
    
  private void checkPermission()
  {
    if (hasPermission())
    {
    }
    else
    {
      requestPermission();
    }
  }

  private void registerBroadcastReceiver()
  {
    IntentFilter filter = new IntentFilter();

    filter.addAction(Constants.Operation.CommitText);
    filter.addAction(Constants.NativeMessage.NOTIFY_CALLBACK_IP);
    filter.addAction(Constants.Operation.HideKeyboard);

    LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(this);
    localBroadcastManager.registerReceiver(mBroadcastReceiver, filter);
  }
  
  private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
  {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction();

      if (Constants.Operation.CommitText.equals(action))
      {
        Bundle extras=intent.getExtras();
        voiceRecognizeResultString= extras.getString("text");
        recognizeResulttextView.setText(voiceRecognizeResultString);
        sendChatRequest();
        startFriendShutDownAt2100Service();
      }
    }
  };

  protected void startFriendShutDownAt2100Service()
  {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.stupidbeauty.shutdownat2100androidnative", "com.stupidbeauty.shutdownat2100androidnative.TimeCheckService"));
    startService(intent);
  }

  private void initializeMsc()
  {
    SpeechUtility.createUtility(this, SpeechConstant.APPID+"=56e142d3");
    mIat= SpeechRecognizer.createRecognizer(this, null);
  }
}