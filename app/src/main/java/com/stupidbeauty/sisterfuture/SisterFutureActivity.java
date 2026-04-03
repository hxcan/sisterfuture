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
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

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
  private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
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

  // 🔥 #4657 死循环救援模式标记
  private boolean isDeadlockRescueMode = false;
  
  // ⚠️ #4824 HTTP 429 限流重试计数器
  private int rateLimitRetryCount = 0;
  private static final int MAX_RATE_LIMIT_RETRIES = 3;

  // 🔍 #4997 请求 ID 追踪 - 过滤旧请求的错误回调
  private volatile long currentRequestId = 0;
  private volatile long lastSuccessRequestId = 0;
  // === 内置 FTP 服务器相关成员变量 ===
  private static final int FTP_SERVER_PORT = 2123;  // 端口规划：BlindBox.her=2121, JoyMan=2122, SisterFuture=2123
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
      String content = msg.optString("content");
      String toolCallId = msg.optString("tool_call_id");
      JSONArray toolCalls = msg.optJSONArray("tool_calls");

      if ("tool".equals(role) && !toolCallId.isEmpty())
      {
        String toolName = msg.optString("name", "unknown_tool");
        String displayText = "🛠️ 工具调用结果：" + toolName + "\n" + content;
        messageAdapter.addMessage(new MessageItem(displayText, MessageType.TOOL_CALL_RESULT));
      }
      else if ("user".equals(role) && !content.isEmpty())
      {
        messageAdapter.addMessage(new MessageItem(content, MessageType.USER));
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
        else if (!content.isEmpty())
        {
          messageAdapter.addMessage(new MessageItem(content, MessageType.AI));
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
      FileLogger.d(TAG, "🔄 [AUTO_RESUME] 历史记录为空，跳过自动恢复");
      return;
    }
    
    JSONObject lastMsg = history.get(history.size() - 1);
    String role = lastMsg.optString("role", "");
    String toolCallId = lastMsg.optString("tool_call_id", "");
    
    FileLogger.d(TAG, "🔄 [AUTO_RESUME] 检查最后一条消息：role=" + role + ", toolCallId=" + toolCallId);
    
    boolean shouldResume = false;
    String resumeReason = "";
    
    if ("user".equals(role))
    {
      shouldResume = true;
      resumeReason = "最后一条是用户消息";
    }
    else if ("tool".equals(role) && !toolCallId.isEmpty())
    {
      shouldResume = true;
      resumeReason = "最后一条是工具调用结果";
    }
    
    if (shouldResume)
    {
      FileLogger.i(TAG, "🔄 [AUTO_RESUME] 触发自动恢复：" + resumeReason);
      
      new Handler(Looper.getMainLooper()).postDelayed(() -> {
        FileLogger.d(TAG, "🔄 [AUTO_RESUME] 开始发送自动恢复请求");
        sendChatRequestTongYi();
      }, 500);
    }
    else
    {
      FileLogger.d(TAG, "🔄 [AUTO_RESUME] 不需要自动恢复");
    }
  }

  public void sendMessageToSister(String message)
  {
    if (message == null || message.trim().isEmpty())
    {
      return;
    }

    messageAdapter.addMessage(new MessageItem(message, MessageType.USER));
    contextManager.addUserMessage(message);
    
    if (isDeadlockRescueMode) {
      FileLogger.d(TAG, "🔥 [RESCUE_MODE] 处于死循环救援模式，处理 API Key 输入");
      guideManager.handleDeadlockRescueApiKey(message, new GuideManager.ChatCallback() {
        @Override
        public void onResponse(String response) {
          runOnUiThread(() -> {
            messageAdapter.addMessage(new MessageItem(response, MessageType.AI));
            scrollToBottom();
            ttsSayReply(response);
            if (response.contains("✅")) {
              FileLogger.i(TAG, "✅ [BACKUP_AP_CREATED] 备用接入点配置成功，退出救援模式");
              isDeadlockRescueMode = false;
              FileLogger.d(TAG, "ℹ️ [RESCUE_MODE] 退出救援模式：false");
              modelAccessPointManager.resetFailureCount();
              FileLogger.i(TAG, "✅ [FAILURE_RESET] 救援成功，计数器已重置：" + modelAccessPointManager.getConsecutiveFailures());
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
  
  @OnClick(R.id.sendButtonn2)
  public void sendButtonn2()
  {
    voiceRecognizeResultString = recognizeResulttextView.getText().toString();
    sendMessageToSister(voiceRecognizeResultString);
    recognizeResulttextView.setText("");
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
    FileLogger.w(TAG, "🔍 [CONTEXT_LENGTH] 检测到上下文超长错误，自动缩短上下文");
    
    contextManager.logFullHistory("ContextLengthError");
    
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
    FileLogger.d(TAG, "🆔 [REQUEST_ID] 开始发送请求 #" + requestId + " | 当前接入点索引：" + modelAccessPointManager.getCurrentAccessPointIndex());
    
    SisterFutureService.updateNotificationStatus(this, "正在发送请求...");

    if (modelAccessPointManager.checkFailureThreshold()) {
      FileLogger.e(TAG, "🚨 [DEADLOCK_RESCUE] 检测到连续失败超过阈值！触发救援模式");
      FileLogger.d(TAG, "⚠️ [RESCUE_MODE] 进入救援模式：true");
      isDeadlockRescueMode = true;
      runOnUiThread(() -> {
        Toast.makeText(SisterFutureActivity.this, 
          "⚠️ 所有接入点连续失败，正在启动备用接入点配置向导...", 
          Toast.LENGTH_LONG).show();
        
        guideManager.showAddAccessPointGuideForDeadlock(new GuideManager.ChatCallback() {
          @Override
          public void onResponse(String message) {
            messageAdapter.addMessage(new MessageItem(message, MessageType.AI));
            scrollToBottom();
            ttsSayReply(message);
            if (message.contains("✅")) {
              FileLogger.i(TAG, "✅ [BACKUP_AP_CREATED] 备用接入点配置成功，退出救援模式");
              isDeadlockRescueMode = false;
              FileLogger.d(TAG, "ℹ️ [RESCUE_MODE] 退出救援模式：false");
              modelAccessPointManager.resetFailureCount();
              FileLogger.i(TAG, "✅ [FAILURE_RESET] 救援成功，计数器已重置：" + modelAccessPointManager.getConsecutiveFailures());
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

      contextManager.logFullHistory("BeforeSendRequest");

      // 🔍 #5030【救援模式】遍历消息列表，检查所有 tool_call 的 arguments
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 开始检查消息列表中的 tool_call arguments | 消息总数：" + messagesArray.length());
      for (int i = 0; i < messagesArray.length(); i++) {
        try {
          JSONObject msg = messagesArray.getJSONObject(i);
          String role = msg.optString("role", "unknown");
          
          if ("tool".equals(role)) {
            String toolCallId = msg.optString("tool_call_id", "unknown");
            String toolName = msg.optString("name", "unknown_tool");
            String content = msg.optString("content", "");
            
            FileLogger.w(TAG, "🔧 [TOOL_MESSAGE] 索引=" + i + 
                ", role=tool, tool_call_id=" + toolCallId + 
                ", name=" + toolName);
            FileLogger.d(TAG, "   📄 [TOOL_CONTENT] content 长度=" + content.length());
            
            try {
              new JSONObject(content);
              FileLogger.d(TAG, "   ✅ [JSON_VALID] content 是有效的 JSON");
            } catch (JSONException e) {
              FileLogger.e(TAG, "   ❌ [JSON_INVALID] content 不是有效的 JSON! Error: " + e.getMessage());
              FileLogger.e(TAG, "   📋 [RAW_CONTENT] 原始内容：" + content);
            }
          }
          
          if ("assistant".equals(role)) {
            JSONArray toolCalls = msg.optJSONArray("tool_calls");
            if (toolCalls != null && toolCalls.length() > 0) {
              FileLogger.w(TAG, "🤖 [ASSISTANT_MESSAGE] 索引=" + i + 
                  ", role=assistant, tool_calls 数量=" + toolCalls.length());
              
              for (int j = 0; j < toolCalls.length(); j++) {
                JSONObject toolCall = toolCalls.getJSONObject(j);
                String id = toolCall.optString("id", "unknown");
                JSONObject func = toolCall.optJSONObject("function");
                
                if (func != null) {
                  String funcName = func.optString("name", "unknown_function");
                  String args = func.optString("arguments", "");
                  
                  FileLogger.w(TAG, "   🔧 [TOOL_CALL] 索引=" + j + 
                      ", id=" + id + ", name=" + funcName);
                  FileLogger.d(TAG, "      📄 [ARGUMENTS] arguments 长度=" + args.length());
                  
                  // ✅ 先输出原始内容（无论有效无效）
                  FileLogger.d(TAG, "      📋 [RAW_ARGS] 原始参数：" + args);
                  
                  // 尝试解析 arguments 是否为有效 JSON
                  try {
                    new JSONObject(args);
                    FileLogger.d(TAG, "      ✅ [JSON_VALID] 解析成功");
                  } catch (JSONException e) {
                    FileLogger.e(TAG, "      ❌ [JSON_INVALID] 解析失败：" + e.getMessage());
                  }
                }
              }
            }
          }
        } catch (JSONException e) {
          FileLogger.e(TAG, "❌ [PARSE_ERROR] 解析消息 #" + i + " 失败", e);
        }
      }
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 消息列表检查完成");

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
          FileLogger.d(TAG, "❌ [ERROR_CHECK] 请求 #" + requestId + " 错误 | lastSuccessRequestId=" + lastSuccessRequestId + " | 忽略=" + (requestId < lastSuccessRequestId));
          
          if (requestId < lastSuccessRequestId) {
            FileLogger.w(TAG, "⚠️ [IGNORED] 忽略旧请求 #" + requestId + " 的错误回调（lastSuccessRequestId=" + lastSuccessRequestId + "）");
            return;
          }
          
          FileLogger.e(TAG, "请求出错：" + error.getClass().getSimpleName() + " - " + error.getMessage());
          hideThinkingOverlay();
          
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "请求出错，请重试");

          boolean isAccessPointUnavailable = false;

          if (error instanceof TongYiClient.AccessPointUnavailableException)
          {
            FileLogger.d(TAG, "接入点不可用异常，准备切换");
            isAccessPointUnavailable = true;
          }
          else if (error instanceof TongYiClient.RateLimitException) {
            FileLogger.w(TAG, "⚠️ [RATE_LIMIT] 限流错误，等待后重试 #" + rateLimitRetryCount);
            handleRateLimitError();
            return;
          }
          else if (error instanceof TongYiClient.ResponseException)
          {
            TongYiClient.ResponseException responseException = (TongYiClient.ResponseException) error;
            Response response = responseException.getResponse();
            if (response != null) {
              int statusCode = response.code();
              FileLogger.d(TAG, "HTTP 响应异常，状态码：" + statusCode);
              
              if (statusCode == 401 || statusCode == 403 || statusCode == 500 || statusCode == 503) {
                FileLogger.d(TAG, "状态码 " + statusCode + " 表示接入点不可用，触发切换");
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
            
            String errorBody = responseException.getCustomMessage();
            FileLogger.e(TAG, "HTTP " + (response != null ? response.code() : 0) + ": " + errorBody);
            
            if (isHtmlResponse(errorBody))
            {
              FileLogger.e(TAG, "API 返回 HTML 页面，防止崩溃");
              runOnUiThread(() ->
              {
                messageAdapter.addMessage(new MessageItem("API 返回 HTML 页面", MessageType.AI));
                scrollToBottom();
              });
              return;
            }
          }
          else
          {
            FileLogger.e(TAG, "未知异常，不触发切换：" + error.getMessage());
          }

          if (isAccessPointUnavailable)
          {
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            FileLogger.w(TAG, "🔥 [FAILURE_COUNT] 接入点不可用，计数器递增：" + failures);
            
            FileLogger.d(TAG, "🔄 [RETRY] 准备重试，thread=" + Thread.currentThread().getName());
            
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
        }
      );
    }
    else
    {
      FileLogger.w(TAG, "语音识别结果为空");
    }
  }

  private void handleRateLimitError() {
    if (rateLimitRetryCount >= MAX_RATE_LIMIT_RETRIES) {
      FileLogger.e(TAG, "❌ [RATE_LIMIT] 限流重试次数过多（" + rateLimitRetryCount + " >= " + MAX_RATE_LIMIT_RETRIES + "），放弃");
      rateLimitRetryCount = 0;
      runOnUiThread(() -> {
        messageAdapter.addMessage(new MessageItem("⚠️ 请求过于频繁，请稍后再试", MessageType.AI));
        scrollToBottom();
      });
      return;
    }
    
    int delayMs = 1000 * (1 << rateLimitRetryCount);
    FileLogger.w(TAG, "⏳ [RATE_LIMIT] 限流重试 #" + rateLimitRetryCount + "，等待 " + delayMs + "ms");
    
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
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
           trimmedContent.startsWith("<html") ||
           trimmedContent.startsWith("<HTML") ||
           trimmedContent.contains("<title") ||
           trimmedContent.contains("<TITLE");
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
        FileLogger.e(TAG, "响应为空或 choices 为空");
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
              FileLogger.w(TAG, "没有有效的工具调用，跳过执行");
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
                FileLogger.w(TAG, "工具调用无效：name 或 id 为空");
                continue;
              }

              if (argsJsonStr == null || argsJsonStr.trim().isEmpty())
              {
                argsJsonStr = "{}";
              }

              // 🔧 #4886 添加 JSON 解析异常处理，容忍格式错误的工具调用参数
              JSONObject args;
              try
              {
                args = new JSONObject(argsJsonStr);
              }
              catch (JSONException e)
              {
                FileLogger.e(TAG, "❌ [TOOL_CALL_JSON_ERROR] 工具调用参数 JSON 格式错误，已跳过 | toolName=" + toolName + ", toolCallId=" + toolCallId, e);
                FileLogger.e(TAG, "   📋 [RAW_ARGS] 原始参数：" + argsJsonStr);
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
                SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在执行：" + toolName);
                
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
                        FileLogger.e(TAG, "封装异步结果失败", e);
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
                    FileLogger.e(TAG, "异步工具失败：" + toolName + ", toolCallId=" + toolCallId, e);
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
            FileLogger.e(TAG, "处理工具调用失败", e);
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
          
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "回复完成");
          
          modelAccessPointManager.resetFailureCount();
          rateLimitRetryCount = 0;
        });
      }
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "解析 JSON 响应失败：" + e.getMessage());
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

            if (!toolManager.tryMarkToolCallAsReplied(id))
            {
              FileLogger.w(TAG, "⚠️ 忽略重复的工具回复消息，toolCallId=" + id + ", toolName=" + name);
              continue;
            }

            contextManager.addToolMessage(id, name, result.toString());
            FileLogger.d(TAG, "工具消息已添加：ID=" + id + ", Name=" + name);
            messageAdapter.addMessage(
              new MessageItem(
                "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
                MessageType.TOOL_CALL_RESULT
              )
            );
          }
        }

        clearAccumulatedToolCalls();

        sendChatRequestTongYi();
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "postProcessToolResults 出错", e);
      }
    });
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
          FileLogger.e("SisterFutureActivity", "提取工具描述失败：" + name, e);
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
    
    scheduleStartBuiltinFtpServer();

    SisterFutureService.startForegroundService(this);
    requestNotificationPermission();
    
    if (savedInstanceState == null)
    {
      articleListmyRecyclerView.post(() -> 
      {
        scrollToBottom();
        FileLogger.d(TAG, "冷启动完成，已自动滚动到最新消息");
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
        result=(checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED);
          
        if (!result)
        {
          FileLogger.d(TAG, "权限不足：" + permissionString);
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
        Toast.makeText(this, "需要存储和录音权限", Toast.LENGTH_LONG).show();
      }

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

  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        FileLogger.d(TAG, "请求 POST_NOTIFICATIONS 权限");
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.POST_NOTIFICATIONS},
            NOTIFICATION_PERMISSION_REQUEST);
      } else {
        FileLogger.d(TAG, "POST_NOTIFICATIONS 权限已授予");
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        FileLogger.d(TAG, "✅ POST_NOTIFICATIONS 权限已授予");
      } else {
        FileLogger.w(TAG, "⚠️ POST_NOTIFICATIONS 权限被拒绝，通知功能可能不可用");
        Toast.makeText(this, "通知权限被拒绝，后台通知可能无法显示", Toast.LENGTH_LONG).show();
      }
    } else if (requestCode == PERMISSIONS_REQUEST) {
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

  private void startBuiltinFtpServer() {
    File rootDir = getFilesDir();
    File parentDir = rootDir.getParentFile();
    
    FileLogger.d(TAG, "📁 [FTP_DEBUG] 应用 files 目录：" + rootDir.getAbsolutePath());
    FileLogger.d(TAG, "📂 [FTP_DEBUG] 应用私有目录（FTP 根目录）：" + parentDir.getAbsolutePath());
    FileLogger.d(TAG, "📂 [FTP_DEBUG] 根目录是否存在：" + (parentDir != null ? parentDir.exists() : "null"));
    
    if (parentDir != null && parentDir.exists()) {
      File[] files = parentDir.listFiles();
      FileLogger.d(TAG, "📋 [FTP_DEBUG] 根目录下文件/目录数量：" + (files != null ? files.length : "null"));
      
      if (files != null) {
        for (File file : files) {
          FileLogger.d(TAG, "  - 📄 [FTP_DEBUG] " + (file.isDirectory() ? "DIR" : "FILE") + ": " + file.getName());
        }
      }
      
      FileLogger.d(TAG, "📂 [FTP_DEBUG] databases/ 存在：" + new File(parentDir, "databases").exists());
      FileLogger.d(TAG, "📂 [FTP_DEBUG] shared_prefs/ 存在：" + new File(parentDir, "shared_prefs").exists());
      FileLogger.d(TAG, "📂 [FTP_DEBUG] files/ 存在：" + new File(parentDir, "files").exists());
      FileLogger.d(TAG, "📂 [FTP_DEBUG] code_cache/ 存在：" + new File(parentDir, "code_cache").exists());
    }
    
    builtinFtpServer = new BuiltinFtpServer(this);
    builtinFtpServerErrorListener = new BuiltinFtpServerErrorListener();
    
    builtinFtpServer.setPort(FTP_SERVER_PORT);
    builtinFtpServer.setAllowActiveMode(false);
    builtinFtpServer.setErrorListener(builtinFtpServerErrorListener);
    builtinFtpServer.start();
    
    FileLogger.d(TAG, "🚀 [FTP_DEBUG] 内置 FTP 服务器已启动，端口：" + FTP_SERVER_PORT);
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

}