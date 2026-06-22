package com.stupidbeauty.sisterfuture;

import java.io.File;
import com.stupidbeauty.sisterfuture.tool.ToolRegistry;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.ContextManager;
import com.stupidbeauty.sisterfuture.manager.SystemPromptManager;
import com.stupidbeauty.sisterfuture.utils.ContextLengthUtils;
import android.os.Handler;
import android.os.Looper;
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
import android.util.Log;
import com.stupidbeauty.sisterfuture.bean.MessageItem;
import com.stupidbeauty.sisterfuture.bean.MessageType;
import com.stupidbeauty.sisterfuture.bean.Delta;
import com.stupidbeauty.sisterfuture.bean.Choice;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import androidx.activity.result.ActivityResultLauncher;
import android.net.Uri;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.util.Base64;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.FileInputStream;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.media.MediaScannerConnection;
import android.annotation.SuppressLint;
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
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.stupidbeauty.builtinftp.BuiltinFtpServer;
import com.stupidbeauty.sisterfuture.listener.BuiltinFtpServerErrorListener;
import java.util.Timer;
import java.util.TimerTask;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.stupidbeauty.msclearnfootball.VoiceRecognizeResult;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechUtility;
import com.stupidbeauty.sisterfuture.network.TongYiClient;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import butterknife.BindView;
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
import com.stupidbeauty.sisterfuture.manager.PermissionManager;
import com.stupidbeauty.sisterfuture.manager.RepeatDetectionManager;
import com.stupidbeauty.sisterfuture.manager.EmptyDeltaDetectionManager;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
  private GuideManager guideManager ;

  private JSONObject firstToolCallDelta = null;
  private boolean isFirstToolCallProcessed = false;
  private ModelAccessPointManager modelAccessPointManager;
  private ToolManager toolManager;
  private MemoryManager memoryManager;
  private RepeatDetectionManager repeatDetectionManager;

  private Map<Integer, String> indexToOriginalIdMap = new HashMap<>();
  private Map<String, Function> partialToolArgs = new HashMap<>();

  private static final Gson gson = new Gson();

  private ContextManager contextManager;
  private MessageAdapter messageAdapter;
  @BindView(R.id.articleListmy_recycler_view) RecyclerView articleListmyRecyclerView;
  private static final String DEFAULT_INPUT_TEXT = "君不见，黄河之水天上来，奔流到海不复回君不见，高堂明镜悲白发，朝如青丝暮成雪，人生得意须尽欢，莫使金樽空对月";

  private StringBuilder accumulatedAnswer = new StringBuilder();

  // 📷 图片输入功能相关变量
  private ActivityResultLauncher<Intent> imagePickerLauncher;
  private String currentImageBase64 = null;
  @BindView(R.id.uploadImageButton) Button uploadImageButton;

  private TongYiClient tongYiClient;
  private boolean isTtsSpeaking = false;

  private MediaPlayer mediaPlayer;
  private boolean voiceEndDetected=false;

  private TextToSpeech mTts;

  // 权限管理器
  private PermissionManager permissionManager;

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

  // 死循环救援模式标记
  private boolean isDeadlockRescueMode = false;
  
  // HTTP 429 限流重试计数器
  private int rateLimitRetryCount = 0;
  private static final int MAX_RATE_LIMIT_RETRIES = 3;

  // 请求 ID 追踪 - 过滤旧请求的错误回调
  private volatile long currentRequestId = 0;
  private volatile long lastSuccessRequestId = 0;
  // === 内置 FTP 服务器相关成员变量 ===
  private static final int FTP_SERVER_PORT = 2123;
  private BuiltinFtpServer builtinFtpServer = null;
  private BuiltinFtpServerErrorListener builtinFtpServerErrorListener = null;


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
  
  private void displayExistingContext()
  {
    List<JSONObject> history = contextManager.getHistory();
    for (JSONObject msg : history)
    {
      String role = msg.optString("role");
      Object contentObj = msg.opt("content");
      String toolCallId = msg.optString("tool_call_id");
      JSONArray toolCalls = msg.optJSONArray("tool_calls");

      if ("tool".equals(role) && !toolCallId.isEmpty())
      {
        String toolName = msg.optString("name", "unknown_tool");
        String content = msg.optString("content");
        String displayText = "🛠️ 工具调用结果：" + toolName + "\n" + content;
        messageAdapter.addMessage(new MessageItem(displayText, MessageType.TOOL_CALL_RESULT));
      }
      else if ("user".equals(role))
      {
        if (contentObj instanceof JSONArray)
        {
          JSONArray contentArray = (JSONArray) contentObj;
          StringBuilder textBuilder = new StringBuilder();
          String imageUrl = null;
          
          for (int i = 0; i < contentArray.length(); i++)
          {
            try
            {
              JSONObject item = contentArray.optJSONObject(i);
              if (item == null) continue;
              
              String type = item.optString("type");
              if ("text".equals(type))
              {
                textBuilder.append(item.optString("text"));
              }
              else if ("image_url".equals(type))
              {
                JSONObject imageUrlObj = item.optJSONObject("image_url");
                if (imageUrlObj != null)
                {
                  String url = imageUrlObj.optString("url");
                  if (url != null && url.startsWith("data:image/jpeg;base64,"))
                  {
                    int commaIndex = url.lastIndexOf(',');
                    if (commaIndex > 0) {
                      imageUrl = url.substring(commaIndex + 1);
                    } else {
                      imageUrl = url;
                    }
                  }
                }
              }
            }
            catch (Exception e)
            {
              Log.e(TAG, "解析多模态消息失败", e);
            }
          }
          
          messageAdapter.addMessage(new MessageItem(textBuilder.toString(), MessageType.USER, imageUrl));
        }
        else
        {
          String content = msg.optString("content");
          if (!content.isEmpty())
          {
            messageAdapter.addMessage(new MessageItem(content, MessageType.USER));
          }
        }
      }
      else if ("assistant".equals(role))
      {
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
        }
        else if (!msg.optString("content").isEmpty())
        {
          messageAdapter.addMessage(new MessageItem(msg.optString("content"), MessageType.AI));
        }
      }
    }
    
    checkAndResumeLastMessage();
  }
  
  private void checkAndResumeLastMessage()
  {
    List<JSONObject> history = contextManager.getHistory();
    if (history == null || history.isEmpty())
    {
      return;
    }
    
    JSONObject lastMsg = history.get(history.size() - 1);
    String role = lastMsg.optString("role", "");
    String toolCallId = lastMsg.optString("tool_call_id", "");
    
    boolean shouldResume = false;
    
    if ("user".equals(role))
    {
      shouldResume = true;
    }
    else if ("tool".equals(role) && !toolCallId.isEmpty())
    {
      shouldResume = true;
    }
    
    if (shouldResume)
    {
      new Handler(Looper.getMainLooper()).postDelayed(() -> 
      {
        sendChatRequestTongYi();
      }, 500);
    }
  }

  // 📷 修改：统一处理纯文本和多模态消息
  public void sendMessageToSister(String message)
  {
    if (message == null || message.trim().isEmpty())
    {
      if (currentImageBase64 == null || currentImageBase64.isEmpty())
      {
        return;
      }
    }

    boolean hasImage = (currentImageBase64 != null && !currentImageBase64.isEmpty());
    
    if (hasImage)
    {
      try
      {
        JSONArray contentArray = new JSONArray();
        
        if (message != null && !message.trim().isEmpty())
        {
          JSONObject textContent = new JSONObject();
          textContent.put("type", "text");
          textContent.put("text", message);
          contentArray.put(textContent);
        }
        
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + currentImageBase64);
        imageContent.put("image_url", imageUrl);
        contentArray.put(imageContent);
        
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", contentArray);
        
        contextManager.addRawMessage(userMessage);
        
        messageAdapter.addMessage(new MessageItem(message != null ? message : "", MessageType.USER, hasImage ? currentImageBase64 : null));
        
        currentImageBase64 = null;
        
        scrollToBottom();
        
        if (isDeadlockRescueMode) {
          return;
        }
        
        if (guideManager != null && guideManager.isEmptyAccessPointList())
        {
          guideManager.processWithGuideLogic(message != null ? message : "", new GuideManager.ChatCallback()
          {
            @Override
            public void onResponse(String response) {
              runOnUiThread(() -> {
                messageAdapter.addMessage(new MessageItem(response, MessageType.AI));
                scrollToBottom();
                ttsSayReply(response);
                if (response.contains("✅")) {
                  isDeadlockRescueMode = false;
                  modelAccessPointManager.resetFailureCount();
                }
              });
            }

            @Override
            public void onError(String error) {
              runOnUiThread(() -> {
                messageAdapter.addMessage(new MessageItem(error, MessageType.AI));
                scrollToBottom();
              });
            }
          });
          return;
        }

        sendChatRequestTongYi();
      }
      catch (JSONException e)
      {
        runOnUiThread(() -> {
          Toast.makeText(SisterFutureActivity.this, "❌ 构建消息失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    }
    else
    {
      messageAdapter.addMessage(new MessageItem(message, MessageType.USER));
      contextManager.addUserMessage(message);
      
      if (isDeadlockRescueMode) {
        guideManager.handleDeadlockRescueApiKey(message, new GuideManager.ChatCallback() {
          @Override
          public void onResponse(String response) {
            runOnUiThread(() -> {
              messageAdapter.addMessage(new MessageItem(response, MessageType.AI));
              scrollToBottom();
              ttsSayReply(response);
              if (response.contains("✅")) {
                isDeadlockRescueMode = false;
                modelAccessPointManager.resetFailureCount();
              }
            });
          }

          @Override
          public void onError(String error) {
            runOnUiThread(() -> {
              messageAdapter.addMessage(new MessageItem(error, MessageType.AI));
              scrollToBottom();
            });
          }
        });
        return;
      }
      
      if (guideManager != null && guideManager.isEmptyAccessPointList())
      {
        guideManager.processWithGuideLogic(message, new GuideManager.ChatCallback()
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
  }
  
  @OnClick(R.id.sendButtonn2)
  public void sendButtonn2()
  {
    voiceRecognizeResultString = recognizeResulttextView.getText().toString();
    sendMessageToSister(voiceRecognizeResultString);
    recognizeResulttextView.setText("");
  }

  // 📷 图片上传按钮点击事件
  @OnClick(R.id.uploadImageButton)
  public void onUploadImageButton()
  {
    if (currentImageBase64 != null)
    {
      currentImageBase64 = null;
    }
    
    openImagePicker();
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
        
        SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在思考中...");
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

  private void handleContextLengthError(String errorMessage, final boolean isRetry)
  {
    runOnUiThread(() ->
    {
      String displayMessage = errorMessage + "\n⚠️ 上下文超长，自动缩短后重试";
      messageAdapter.addMessage(new MessageItem(displayMessage, MessageType.AI));
      scrollToBottom();
      ttsSayReply("上下文超长，自动缩短后重试");
      
      contextManager.addAssistantMessage("⚠️ 上下文超长，已自动缩短");
      
      contextManager.decreaseMaxRounds();
      
      if (isRetry)
      {
        sendChatRequestTongYi();
      }
    });
  }

  private void sendChatRequestTongYi()
  {
    final long requestId = System.currentTimeMillis();
    currentRequestId = requestId;
    
    SisterFutureService.updateNotificationStatus(this, "正在发送请求...");

    if (modelAccessPointManager.checkFailureThreshold()) {
      isDeadlockRescueMode = true;
      runOnUiThread(() -> {
        Toast.makeText(SisterFutureActivity.this, 
          "⚠️ 所有接入点连续失败正在启动备用接入点配置向导...", 
          Toast.LENGTH_LONG).show();
        
        guideManager.showAddAccessPointGuideForDeadlock(new GuideManager.ChatCallback() {
          @Override
          public void onResponse(String message) {
            messageAdapter.addMessage(new MessageItem(message, MessageType.AI));
            scrollToBottom();
            ttsSayReply(message);
            if (message.contains("✅")) {
              isDeadlockRescueMode = false;
              modelAccessPointManager.resetFailureCount();
            }
          }

          @Override
          public void onError(String error) {
            messageAdapter.addMessage(new MessageItem(error, MessageType.AI));
            scrollToBottom();
          }
        });
      });
      return;
    }

    if (voiceRecognizeResultString != null && !voiceRecognizeResultString.isEmpty())
    {
      accumulatedAnswer.setLength(0);
      showThinkingOverlay();

      List<JSONObject> history = contextManager.getHistory();
      List<JSONObject> cleanedHistory = contextManager.normalizeToolCallMessages(history, true);
      
      JSONArray historyArray = new JSONArray(cleanedHistory);
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

      String currentReservedMessageId = contextManager.reserveMessageId();

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
          hideThinkingOverlay();
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在生成回复...");
          
          lastSuccessRequestId = requestId;
          
          parseTongYiResponse(response);
        }

        @Override
        public void onError(Exception error)
        {
          if (requestId < lastSuccessRequestId) {
            return;
          }
          
          String errorType = error.getClass().getSimpleName();
          FileLogger.e(TAG, "❌ AI 响应错误：" + errorType);
          
          hideThinkingOverlay();
          
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "请求出错，请重试");

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            isAccessPointUnavailable = true;
          }
          else if (error instanceof TongYiClient.RateLimitException) {
            handleRateLimitError();
            return;
          }
          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            if (response != null) {
              int statusCode = response.code();
              
              if (statusCode == 401 || statusCode == 403 || statusCode == 500 || statusCode == 503) {
                isAccessPointUnavailable = true;
              }
              else if (statusCode == 400) {
                String errorBody = responseException.getCustomMessage();
                if (ContextLengthUtils.isContextLengthError(errorBody)) {
                  handleContextLengthError(errorBody, true);
                  return;
                }
              }
            }
          }

          if (isAccessPointUnavailable)
          {
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            sendChatRequestTongYi();
          }
          else
          {
            modelAccessPointManager.resetFailureCount();
          }
        }
      },
      () ->
        {
        },
        currentReservedMessageId);
    }
  }

  private void handleRateLimitError() 
  {
    if (rateLimitRetryCount >= MAX_RATE_LIMIT_RETRIES) 
    {
      rateLimitRetryCount = 0;
      
      int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
      sendChatRequestTongYi();
      return;
    }
    
    int delayMs = 1000 * (1 << rateLimitRetryCount);
    
    new Handler(Looper.getMainLooper()).postDelayed(() -> 
    {
      rateLimitRetryCount++;
      sendChatRequestTongYi();
    }, delayMs);
  }

  private boolean isHtmlResponse(String content)
  {
    if (content == null || content.isEmpty())
    {
      return false;
    }
    
    String trimmedContent = content.trim();
    return trimmedContent.startsWith("<!DOCTYPE html") ||
           trimmedContent.startsWith("<html");
  }

  protected void parseTongYiResponse(String jsonString)
  {
    try
    {
      TongYiResponse response = new Gson().fromJson(jsonString, TongYiResponse.class);

      if (response != null && response.getError() != null)
      {
        String errorMessage = response.getError().getMessage();
        boolean isContextTooLong = ContextLengthUtils.isContextLengthError(errorMessage);

        if (isContextTooLong)
        {
          handleContextLengthError(errorMessage, true);
        }
        else
        {
          runOnUiThread(() ->
          {
            messageAdapter.addMessage(new MessageItem(errorMessage, MessageType.AI));
            scrollToBottom();
            ttsSayReply(errorMessage);
            contextManager.addAssistantMessage(errorMessage);
          });
        }
        return;
      }

      if (response == null || response.getChoices() == null || response.getChoices().isEmpty())
      {
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
                continue;
              }

              if (argsJsonStr == null || argsJsonStr.trim().isEmpty())
              {
                argsJsonStr = "{}";
              }

              JSONObject args;
              try
              {
                args = new JSONObject(argsJsonStr);
              }
              catch (JSONException e)
              {
                continue;
              }

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
                toolManager.executeToolAsync(toolCallId, toolName, args, new Tool.OnResultCallback()
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
                    synchronized (pendingResults)
                    {
                      try
                      {
                        JSONObject errorResult = new JSONObject();
                        errorResult.put("error", e.getMessage());
                        errorResult.put("tool_name", toolName);
                        
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", toolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", errorResult);
                        pendingResults.put(toolCallId, wrapper);
                        
                        if (pendingResults.size() == toolCallsArray.length())
                        {
                          postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                        }
                      }
                      catch (Exception ex)
                      {
                      }
                    }
                  }
                });
              }
              else
              {
                JSONObject toolResult = toolManager.executeTool(toolName, args);

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
          
          boolean hasToolCalls = (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty());
          
          if (EmptyDeltaDetectionManager.getInstance().checkAndRecordResponse(fullAnswer, hasToolCalls, contextManager.getHistory().size())) {
              EmptyDeltaDetectionManager.getInstance().acknowledgeTrigger();
              handleContextLengthError("检测到连续空响应，判定为上下文超长", true);
              return;
          }
          
          if (!hasToolCalls && repeatDetectionManager != null && repeatDetectionManager.recordAndCheck(fullAnswer))
          {
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            repeatDetectionManager.reset();
            sendChatRequestTongYi();
            return;
          }
          
          ttsSayReply(fullAnswer);
          contextManager.addAssistantMessage(fullAnswer);
          contextManager.increaseMaxRounds();
          
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "回复完成");
          
          modelAccessPointManager.resetFailureCount();
          rateLimitRetryCount = 0;
        });
      }
    }
    catch (Exception e)
    {
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
          
          if (wrapper == null)
          {
            continue;
          }
          
          String name = wrapper.getString("name");
          JSONObject result = wrapper.getJSONObject("result");

          boolean isDuplicate = !toolManager.tryMarkToolCallAsReplied(id);
          
          if (isDuplicate)
          {
            return;
          }
          
          contextManager.addToolMessage(id, name, result.toString());
          messageAdapter.addMessage(
            new MessageItem(
              "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
              MessageType.TOOL_CALL_RESULT
            )
          );
        }

        clearAccumulatedToolCalls();
        sendChatRequestTongYi();
      }
      catch (Exception e)
      {
      }
    });
  }

  private void scrollToBottom()
  {
    if (messageAdapter.getItemCount() > 0)
    {
      articleListmyRecyclerView.post(() -> {
        articleListmyRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
      });
    }
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
        recognizeResulttextView.setText("");
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
    initImagePicker();
    initTools();
    initView();
    connectSignals();
    displayExistingContext();
    
    scheduleStartBuiltinFtpServer();

    SisterFutureService.startForegroundService(this);
    
    if (savedInstanceState == null)
    {
      articleListmyRecyclerView.post(() -> 
      {
        scrollToBottom();
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
    
    permissionManager = new PermissionManager(this, new PermissionManager.PermissionCallback() {
      @Override
      public void onAllPermissionsGranted() {
      }

      @Override
      public void onPermissionDenied(String permission) {
      }

      @Override
      public void onNotificationPermissionDenied() {
      }
    });
    
    if (permissionManager != null) {
      permissionManager.checkPermission();
      permissionManager.requestNotificationPermission();
    }
  }

  private void initData()
  {
    contextManager = new ContextManager(this);
    modelAccessPointManager = new ModelAccessPointManager(this);
    memoryManager = new MemoryManager(this);
    repeatDetectionManager = new RepeatDetectionManager();
  }

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
    messageAdapter = new MessageAdapter();
    articleListmyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    articleListmyRecyclerView.setAdapter(messageAdapter);

    // 🗑️ 设置删除消息监听器（仅删除上下文，不自动触发新请求）
    messageAdapter.setOnMessageDeleteListener(new MessageAdapter.OnMessageDeleteListener() {
      @Override
      public void onMessageDeleted(MessageItem message, int position) {
        FileLogger.i(TAG, "🗑️ 收到删除消息回调 | position=" + position);
        
        // 从上下文中删除对应位置的消息
        List<JSONObject> history = contextManager.getHistory();
        
        if (history != null && !history.isEmpty()) {
          String deleteContent = message.getText();
          boolean foundAndRemoved = false;
          
          for (int i = history.size() - 1; i >= 0 && !foundAndRemoved; i--) {
            JSONObject msg = history.get(i);
            String content = msg.optString("content", "");
            
            if (content.contains(deleteContent) || deleteContent.contains(content)) {
              contextManager.removeMessage(i);
              foundAndRemoved = true;
              FileLogger.i(TAG, "🗑️ 已从上下文删除 | index=" + i);
            }
          }
          
          if (!foundAndRemoved) {
            contextManager.removeMessage(history.size() - 1);
            FileLogger.i(TAG, "🗑️ 删除上下文中最后一条消息");
          }
        }
        
        // ⚠️ 不再自动触发新请求继续对话
      }
    });

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

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (permissionManager != null) {
      permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

  private void startBuiltinFtpServer() 
  {
    builtinFtpServer = new BuiltinFtpServer(this);
    builtinFtpServerErrorListener = new BuiltinFtpServerErrorListener();
    
    builtinFtpServer.setPort(FTP_SERVER_PORT);
    builtinFtpServer.setAllowActiveMode(false);
    builtinFtpServer.setErrorListener(builtinFtpServerErrorListener);
    builtinFtpServer.start();
  }

  private void scheduleStartBuiltinFtpServer() {
    Timer timerObj = new Timer();
    TimerTask timerTaskObj = new TimerTask() {
      public void run() {
        startBuiltinFtpServer();
      }
    };
    timerObj.schedule(timerTaskObj, 2000);
  }

  private void initImagePicker()
  {
  }

  private void handleSelectedImage(Intent data)
  {
    try
    {
      Uri imageUri = data.getData();
      if (imageUri == null) return;
      
      InputStream inputStream = getContentResolver().openInputStream(imageUri);
      if (inputStream == null) return;
      
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1)
      {
        byteArrayOutputStream.write(buffer, 0, bytesRead);
      }
      inputStream.close();
      
      byte[] imageBytes = byteArrayOutputStream.toByteArray();
      currentImageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
      
      runOnUiThread(() -> {
        Toast.makeText(this, "✅ 图片已加载", Toast.LENGTH_SHORT).show();
      });
    }
    catch (Exception e)
    {
      runOnUiThread(() -> {
        Toast.makeText(this, "❌ 图片加载失败", Toast.LENGTH_LONG).show();
      });
    }
  }

  private void openImagePicker()
  {
    Intent pickIntent = new Intent(Intent.ACTION_PICK);
    pickIntent.setType("image/*");
    try
    {
      startActivityForResult(pickIntent, 1001);
    }
    catch (Exception e)
    {
      Toast.makeText(this, "❌ 无法打开相册", Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == 1001 && resultCode == RESULT_OK && data != null)
    {
      handleSelectedImage(data);
    }
  }

}