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
  private static final String DEFAULT_INPUT_TEXT = "君不见，黄河之水天上来，奔流到海不复回，君不见，高堂明镜悲白发，朝如青丝暮成雪，人生得意须尽欢，莫使金樽空对月";

  private StringBuilder accumulatedAnswer = new StringBuilder();

  // 📷 #280 图片输入功能相关变量
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
        // 🖼️ 检测是否为多模态消息（包含图片）
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
                    // ✅ 修复：使用 lastIndexOf(',') 动态查找逗号位置，替代硬编码的 substring(21)
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
          
          // 使用三参数构造函数，传递文字和图片
          messageAdapter.addMessage(new MessageItem(textBuilder.toString(), MessageType.USER, imageUrl));
        }
        else
        {
          // 纯文本消息
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
      FileLogger.d(TAG, "🔄 [AUTO_RESUME] 历史记录为空，跳过自动恢复");
      return;
    }
    
    JSONObject lastMsg = history.get(history.size() - 1);
    String role = lastMsg.optString("role", "");
    String toolCallId = lastMsg.optString("tool_call_id", "");
    
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
      new Handler(Looper.getMainLooper()).postDelayed(() -> 
      {
        FileLogger.d(TAG, "🔄 [AUTO_RESUME] 开始发送自动恢复请求");
        sendChatRequestTongYi();
      }, 500);
    }
    else
    {
      FileLogger.d(TAG, "🔄 [AUTO_RESUME] 不需要自动恢复");
    }
  }

  // 📷 #280 修改：统一处理纯文本和多模态消息
  public void sendMessageToSister(String message)
  {
    if (message == null || message.trim().isEmpty())
    {
      // 如果没有文字但有图片，仍然可以发送图片
      if (currentImageBase64 == null || currentImageBase64.isEmpty())
      {
        return;
      }
    }

    boolean hasImage = (currentImageBase64 != null && !currentImageBase64.isEmpty());
    
    // 🖼️ 检测是否有图片，构建多模态消息或纯文本消息
    if (hasImage)
    {
      FileLogger.i(TAG, "📷 [SEND_WITH_IMAGE] 发送带图片的消息 | 文字长度：" + (message != null ? message.length() : 0) + " | Base64 长度：" + currentImageBase64.length());
      
      try
      {
        JSONArray contentArray = new JSONArray();
        
        // 添加文字部分（如果有）
        if (message != null && !message.trim().isEmpty())
        {
          JSONObject textContent = new JSONObject();
          textContent.put("type", "text");
          textContent.put("text", message);
          contentArray.put(textContent);
        }
        
        // 添加图片部分
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + currentImageBase64);
        imageContent.put("image_url", imageUrl);
        contentArray.put(imageContent);
        
        // 构建完整的用户消息
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", contentArray);
        
        // ✅ 添加到上下文管理器（唯一真相源）
        contextManager.addRawMessage(userMessage);
        
        // 验证是否成功添加
        List<JSONObject> history = contextManager.getHistory();
        int contextSize = history != null ? history.size() : 0;
        FileLogger.i(TAG, "✅ [CONTEXT_ADDED] 多模态消息已添加到上下文 | 消息总数=" + contextSize);
        
        // UI 显示 - 🖼️ 传递图片数据到 MessageItem，保留原始文字
        messageAdapter.addMessage(new MessageItem(message != null ? message : "", MessageType.USER, hasImage ? currentImageBase64 : null));
        
        // ✅ 发送完成后清除图片缓存，但保持按钮可见
        currentImageBase64 = null;
        
        scrollToBottom();
        
        // 继续发送请求
        if (isDeadlockRescueMode) {
          // 救援模式下不处理，由救援逻辑接管
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

        sendChatRequestTongYi();
      }
      catch (JSONException e)
      {
        FileLogger.e(TAG, "❌ [MULTIMODAL_ERROR] 构建多模态消息失败", e);
        runOnUiThread(() -> {
          Toast.makeText(SisterFutureActivity.this, "❌ 构建消息失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    }
    else
    {
      // 📝 纯文本消息处理（原有逻辑）
      FileLogger.i(TAG, "📝 [SEND_TEXT] 发送纯文本消息 | 长度：" + message.length());
      
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
  }

  @OnClick(R.id.sendButtonn2)
  public void sendButtonn2()
  {
    voiceRecognizeResultString = recognizeResulttextView.getText().toString();
    sendMessageToSister(voiceRecognizeResultString);
    recognizeResulttextView.setText("");
  }

  // 📷 #280 图片上传按钮点击事件 - 始终可见
  @OnClick(R.id.uploadImageButton)
  public void onUploadImageButton()
  {
    FileLogger.d(TAG, "📷 [CLICK] 图片按钮被点击了！当前状态：hasImage=" + (currentImageBase64 != null));
    
    // ✅ 如果有旧图片，先清除它
    if (currentImageBase64 != null)
    {
      currentImageBase64 = null;
      FileLogger.d(TAG, "🗑️ [IMAGE_CLEARED] 清除了旧图片，准备选择新图片");
    }
    
    // ✅ 始终打开相册选择器
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
    FileLogger.w(TAG, "🔍 [CONTEXT_LENGTH] 检测到上下文超长错误，自动缩短上下文");
    
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

      // 🌌 #759909257401 平行宇宙时间线理论：发送请求前清理悬而未决的工具调用

      // 获取当前历史并应用严厉模式清理
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

      // 🔍 #5030【救援模式】遍历消息列表，检查所有 tool_call 的 arguments
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 开始检查消息列表中的 tool_call arguments | 消息总数：" + messagesArray.length());
      
      // 🖼️ 检测是否有图片消息在上下文中
      boolean hasImageInContext = false;
      int imageMessageIndex = -1;
      
      for (int i = 0; i < messagesArray.length(); i++) 
      {
        try 
        {
          JSONObject msg = messagesArray.getJSONObject(i);
          String role = msg.optString("role", "unknown");
          
          if ("user".equals(role)) {
            Object contentObj = msg.opt("content");
            if (contentObj instanceof JSONArray) {
              JSONArray contentArray = (JSONArray) contentObj;
              for (int j = 0; j < contentArray.length(); j++) {
                JSONObject item = contentArray.optJSONObject(j);
                if (item != null && "image_url".equals(item.optString("type"))) {
                  hasImageInContext = true;
                  imageMessageIndex = i;
                  
                  // 获取 Base64 前 50 字符用于验证
                  JSONObject imageUrl = item.optJSONObject("image_url");
                  if (imageUrl != null) {
                    String url = imageUrl.optString("url", "");
                    if (url.startsWith("data:image/jpeg;base64,")) {
                      int commaIndex = url.lastIndexOf(',');
                      String base64 = (commaIndex > 0) ? url.substring(commaIndex + 1) : url;
                      String preview = base64.length() > 50 ? base64.substring(0, 50) + "..." : base64;
                      FileLogger.i(TAG, "🖼️ [IMAGE_IN_CONTEXT] 检测到图片消息 | 位置=" + i + " | Base64 长度=" + base64.length() + " | 前 50 字符：" + preview);
                    }
                  }
                  break;
                }
              }
            }
          }
          
          if ("tool".equals(role)) 
          {
            String toolCallId = msg.optString("tool_call_id", "unknown");
            String toolName = msg.optString("name", "unknown_tool");
            String content = msg.optString("content", "");
            
            try 
            {
              new JSONObject(content);
            }
            catch (JSONException e) 
            {
            }
          }
          
          if ("assistant".equals(role)) 
          {
            JSONArray toolCalls = msg.optJSONArray("tool_calls");
            if (toolCalls != null && toolCalls.length() > 0) 
            {
              for (int j = 0; j < toolCalls.length(); j++) 
              {
                JSONObject toolCall = toolCalls.getJSONObject(j);
                String id = toolCall.optString("id", "unknown");
                JSONObject func = toolCall.optJSONObject("function");
                
                if (func != null) 
                {
                  String funcName = func.optString("name", "unknown_function");
                  String args = func.optString("arguments", "");
                  
                  // 尝试解析 arguments 是否为有效 JSON
                  try 
                  {
                    new JSONObject(args);
                  }
                  catch (JSONException e) 
                  {
                    FileLogger.e(TAG, "      ❌ [JSON_INVALID] 解析失败：" + e.getMessage());
                  }
                }
              }
            }
          }
        }
        catch (JSONException e) 
        {
          FileLogger.e(TAG, "❌ [PARSE_ERROR] 解析消息 #" + i + " 失败", e);
        }
      }
      
      if (hasImageInContext) {
        FileLogger.i(TAG, "✅ [IMAGE_CONFIRMED] 图片消息已确认存在于上下文中 | 总消息数=" + messagesArray.length());
      } else {
        FileLogger.w(TAG, "⚠️ [IMAGE_MISSING] 上下文中未检测到图片消息 | 总消息数=" + messagesArray.length());
      }
      
      FileLogger.i(TAG, "🔍 [RESCUE_DEBUG] 消息列表检查完成");
      
      // 🔗 生成预留消息 ID
      String currentReservedMessageId = contextManager.reserveMessageId();
      FileLogger.i(TAG, "🔗 [RESERVE_ID] 已生成预留消息 ID | requestId=" + requestId + " | messageId=" + currentReservedMessageId);

      tongYiClient.sendChatRequest(messagesArray, true, new OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
        hideThinkingOverlay();
          SisterFutureService.updateNotificationStatus(SisterFutureActivity.this, "正在生成回复...");
          
          lastSuccessRequestId = requestId;
          
          // 🤖 记录 AI 响应
          int responseLength = response != null ? response.length() : 0;
          String responsePreview = response != null && response.length() > 100 ? response.substring(0, 100) + "..." : response;
          FileLogger.i(TAG, "🤖 [AI_RESPONSE] 收到 AI 响应 | 长度=" + responseLength + " | 前 100 字符：" + responsePreview);
          
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
          
          // ❌ 记录 AI 错误
          String errorType = error.getClass().getSimpleName();
          String errorMsg = error.getMessage();
          FileLogger.e(TAG, "❌ [AI_ERROR] AI 响应错误 | 错误类型=" + errorType + " | 错误信息=" + errorMsg);
          
          FileLogger.e(TAG, "请求出错：" + errorType + " - " + errorMsg);
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
        },
        currentReservedMessageId);
    }
    else
    {
    }
  }

  private void handleRateLimitError() 
  {
    if (rateLimitRetryCount >= MAX_RATE_LIMIT_RETRIES) 
    {
      FileLogger.e(TAG, "❌ [RATE_LIMIT] 限流重试次数过多（" + rateLimitRetryCount + " >= " + MAX_RATE_LIMIT_RETRIES + "），切换接入点");
      rateLimitRetryCount = 0;
      
      // 🔥 #4824 重试失败后切换接入点
      int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
      FileLogger.w(TAG, "🔥 [FAILURE_COUNT] 限流导致接入点标记为不可用，计数器：" + failures);
      
      FileLogger.i(TAG, "🔄 [ACCESS_POINT_SWITCH] 限流重试失败，切换到下一个接入点");
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
    return trimmedContent.startsWith("<!DOCTYPE html>") ||
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
                
                // 🔍 添加日志：工具执行开始
                FileLogger.d(TAG, "🔧 [TOOL_EXEC_START] 执行异步工具 | id=" + toolCallId + " | name=" + toolName);
                
                toolManager.executeToolAsync(toolCallId, toolName, args, new Tool.OnResultCallback()
                {
                  @Override
                  public void onResult(JSONObject result)
                  {
                    // 🔍 添加日志：异步工具成功回调
                    FileLogger.d(TAG, "🔧 [TOOL_ASYNC_RESULT] 异步工具成功 | id=" + toolCallId + " | name=" + toolName);
                    
                    synchronized (pendingResults)
                    {
                      try
                      {
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", toolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", result);
                        pendingResults.put(toolCallId, wrapper);
                        FileLogger.d(TAG, "🔧 [TOOL_PENDING_UPDATE] pendingResults 大小：" + pendingResults.size() + " / total=" + toolCallsArray.length());
                      }
                      catch (Exception e)
                      {
                        FileLogger.e(TAG, "封装异步结果失败", e);
                      }

                      if (pendingResults.size() == toolCallsArray.length())
                      {
                        FileLogger.d(TAG, "🔧 [TOOL_ALL_COMPLETE] 所有工具完成，准备调用 postProcessToolResults");
                        postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                      }
                    }
                  }

                  @Override
                  public void onError(Exception e)
                  {
                    // 🔍 添加日志：异步工具失败回调
                    FileLogger.e(TAG, "❌ [TOOL_ASYNC_ERROR] 异步工具失败 | id=" + toolCallId + " | name=" + toolName + " | error=" + e.getMessage());
                    
                    synchronized (pendingResults)
                    {
                      // ✅ 修复：构造错误结果对象，确保异步工具失败时也能生成 Tool Message
                      try
                      {
                        JSONObject errorResult = new JSONObject();
                        errorResult.put("error", e.getMessage());
                        errorResult.put("error_type", e.getClass().getSimpleName());
                        errorResult.put("tool_name", toolName);
                        
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("id", toolCallId);
                        wrapper.put("name", toolName);
                        wrapper.put("result", errorResult);
                        pendingResults.put(toolCallId, wrapper);
                        
                        FileLogger.d(TAG, "🔧 [TOOL_ERROR_HANDLER] 错误处理器触发 | pendingResultsSize=" + pendingResults.size() + " | toolCallsCount=" + toolCallsArray.length());
                        
                        if (pendingResults.size() == toolCallsArray.length())
                        {
                          FileLogger.d(TAG, "🔧 [TOOL_ALL_COMPLETE] 所有工具完成（含错误），准备调用 postProcessToolResults");
                          postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                        }
                      }
                      catch (Exception ex)
                      {
                        FileLogger.e(TAG, "❌ [TOOL_ERROR_WRAPPER_FAIL] 封装错误结果失败", ex);
                      }
                    }
                  }
                });
              }
              else
              {
                // 🔍 添加日志：同步工具执行
                FileLogger.d(TAG, "🔧 [TOOL_SYNC_EXEC] 执行同步工具 | id=" + toolCallId + " | name=" + toolName);
                
                JSONObject toolResult = new JSONObject();

                try
                {
                  toolResult = toolManager.executeTool(toolName, args);
                  FileLogger.d(TAG, "🔧 [TOOL_SYNC_SUCCESS] 同步工具成功 | id=" + toolCallId + " | name=" + toolName);
                }
                catch (IllegalArgumentException e)
                {
                  FileLogger.e(TAG, "❌ [TOOL_SYNC_ILLEGAL_ARG] 同步工具参数错误 | id=" + toolCallId + " | name=" + toolName, e);
                  JSONObject errorResult = new JSONObject();
                  errorResult.put("error", e.getMessage());
                  errorResult.put("tool_name", toolName);
                  errorResult.put("request", args.toString());
                  toolResult = errorResult;
                }
                catch (Exception e)
                {
                  FileLogger.e(TAG, "❌ [TOOL_SYNC_ERROR] 同步工具执行出错 | id=" + toolCallId + " | name=" + toolName, e);
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
              FileLogger.d(TAG, "🔧 [TOOL_SYNC_ALL_COMPLETE] 同步工具全部完成，准备调用 postProcessToolResults");
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
          
          // 🔍 #759909257401 检测连续重复回复
          // ⚠️ #5031 跳过工具调用消息的空回复检测（工具调用时 content 为空是正常的）
          boolean hasToolCalls = (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty());
          
          // 🆕 #816587404117 检测空响应：在正常流程中检测连续空响应
          if (EmptyDeltaDetectionManager.getInstance().checkAndRecordResponse(fullAnswer, hasToolCalls, contextManager.getHistory().size())) {
              EmptyDeltaDetectionManager.getInstance().acknowledgeTrigger();
              handleContextLengthError("检测到连续空响应，判定为上下文超长", true);
              return;
          }
          
          if (!hasToolCalls && repeatDetectionManager != null && repeatDetectionManager.recordAndCheck(fullAnswer))
          {
            FileLogger.e(TAG, "🚨 [REPEAT_THRESHOLD_REACHED] 检测到连续 3 次相同回复，触发接入点切换！");
            
            int failures = modelAccessPointManager.reportCurrentAccessPointUnavailable();
            FileLogger.w(TAG, "🔥 [FAILURE_COUNT] 重复回复导致接入点标记为不可用，计数器：" + failures);
            
            FileLogger.i(TAG, "🔄 [ACCESS_POINT_SWITCH] 因重复回复切换到下一个接入点");
            
            repeatDetectionManager.reset();
            
            // 🔄 切换接入点后，立即重新发送请求
            FileLogger.d(TAG, "🔄 [RETRY] 准备重试，使用新接入点发送请求");
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
      FileLogger.e(TAG, "解析 JSON 响应失败：" + e.getMessage());
    }
  }

  private void postProcessToolResults(java.util.Map<String, JSONObject> pendingResults,
                                    JSONObject assistantMessage,
                                    JSONArray toolCallsArray)
  {
    // 🔍 添加入口日志
    FileLogger.d(TAG, "🔧 [POST_PROCESS_ENTER] 进入 postProcessToolResults | pendingResultsSize=" + pendingResults.size() + " | toolCallsCount=" + toolCallsArray.length());
    
    runOnUiThread(() ->
    {
      try
      {
        // 🔑 关键修复：遍历所有工具，检查幂等性
        for (int i = 0; i < toolCallsArray.length(); i++)
        {
          JSONObject call = toolCallsArray.getJSONObject(i);
          String id = call.getString("id");
          JSONObject wrapper = pendingResults.get(id);
          
          if (wrapper == null)
          {
            FileLogger.w(TAG, "⚠️ [SKIP] 工具结果不存在 | id=" + id);
            continue;
          }
          
          String name = wrapper.getString("name");
          JSONObject result = wrapper.getJSONObject("result");

          // 🔑 关键：检查幂等性
          boolean isDuplicate = !toolManager.tryMarkToolCallAsReplied(id);
          
          if (isDuplicate)
          {
            FileLogger.w(TAG, "⚠️ [DUPLICATE] 发现重复工具 | id=" + id + " | name=" + name + " | 说明已处理过，跳过本次请求触发");
            return;
          }
          
          // 只有未处理过的工具才添加消息
          FileLogger.d(TAG, "🔧 [PROCESS] 处理工具消息 | id=" + id + " | name=" + name);
          contextManager.addToolMessage(id, name, result.toString());
          FileLogger.d(TAG, "工具消息已添加：ID=" + id + ", Name=" + name);
          messageAdapter.addMessage(
            new MessageItem(
              "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
              MessageType.TOOL_CALL_RESULT
            )
          );
        }

        clearAccumulatedToolCalls();

        // 🔑 能走到这里，说明没有重复的工具，需要触发新请求
        FileLogger.i(TAG, "🚀 [TRIGGER] 准备触发新请求 | toolCallsCount=" + toolCallsArray.length());
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
    if (messageAdapter.getItemCount() > 0)
    {
      // ✅ 修复 #753566214831：使用 post() + scrollToPosition() 消除震荡
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
    
    // 初始化权限管理器
    permissionManager = new PermissionManager(this, new PermissionManager.PermissionCallback() {
      @Override
      public void onAllPermissionsGranted() {
        FileLogger.d(TAG, "All permissions granted");
      }

      @Override
      public void onPermissionDenied(String permission) {
        FileLogger.w(TAG, "Permission denied: " + permission);
      }

      @Override
      public void onNotificationPermissionDenied() {
        FileLogger.w(TAG, "Notification permission denied");
      }
    });
    
    // 检查并请求权限
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

    // 🗑️ #821166321034 设置删除消息监听器 - 使用 messageId 删除
    messageAdapter.setOnMessageDeleteListener(new MessageAdapter.OnMessageDeleteListener() {
      @Override
      public void onMessageDeleted(MessageItem message, int position, String messageId) {
        FileLogger.i(TAG, "🗑️ 收到删除消息回调 | position=" + position + " | messageId=" + messageId);
        
        // 优先使用 messageId 精确删除
        if (messageId != null && !messageId.isEmpty()) {
          contextManager.removeMessageById(messageId);
          FileLogger.i(TAG, "🗑️ 已根据 messageId 删除 | messageId=" + messageId);
        } else {
          // 如果没有 messageId，使用下标删除（降级方案）
          FileLogger.w(TAG, "⚠️ messageId 为空，使用下标删除 | position=" + position);
          contextManager.removeMessage(position);
        }
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
    File rootDir = getFilesDir();
    File parentDir = rootDir.getParentFile();
    
    if (parentDir != null && parentDir.exists()) 
    {
      File[] files = parentDir.listFiles();
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

  // 📷 #280 初始化图片选择器
  private void initImagePicker()
  {
    FileLogger.d(TAG, "📷 [IMAGE_PICKER_INIT] 图片选择器已初始化");
  }

  // 📷 #280 处理选中的图片
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
      
      FileLogger.i(TAG, "✅ [PROCESS] 图片处理完成 | Base64 长度：" + (currentImageBase64 != null ? currentImageBase64.length() : 0));
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [IMAGE_ERROR] 加载图片失败", e);
      runOnUiThread(() -> {
        Toast.makeText(this, "❌ 图片加载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
      });
    }
  }

  // 📷 #280 打开图片选择器
  private void openImagePicker()
  {
    FileLogger.d(TAG, "📂 [PICKER] 准备打开相册选择器...");
    
    Intent pickIntent = new Intent(Intent.ACTION_PICK);
    pickIntent.setType("image/*");
    try
    {
      startActivityForResult(pickIntent, 1001);
      FileLogger.d(TAG, "📷 [IMAGE_PICKER] 已打开图片选择器");
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [IMAGE_PICKER_ERROR] 打开图片选择器失败", e);
      Toast.makeText(this, "❌ 无法打开相册：" + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    
    FileLogger.d(TAG, "🔄 [RESULT] 收到相册返回结果 | requestCode=" + requestCode + " | resultCode=" + resultCode);
    
    if (requestCode == 1001 && resultCode == RESULT_OK && data != null)
    {
      handleSelectedImage(data);
    }
  }

}
