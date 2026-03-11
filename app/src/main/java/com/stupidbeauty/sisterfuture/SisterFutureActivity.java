package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.tool.RemoveNoteTool;
import com.stupidbeauty.sisterfuture.tool.ListNotesTool;
import com.stupidbeauty.sisterfuture.tool.GetGitHubFileTool;
import com.stupidbeauty.sisterfuture.tool.CreateGitHubCommitTool;


//import com.stupidbeauty.sisterfuture.SystemPromptManager;


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
import com.stupidbeauty.sisterfuture.tool.ConversationResetTool;
import com.stupidbeauty.sisterfuture.tool.SetToolRemarkTool;
import com.stupidbeauty.sisterfuture.tool.GetToolRemarkTool;
import com.stupidbeauty.sisterfuture.tool.GetRedmineTaskInfoTool;
import com.stupidbeauty.sisterfuture.tool.UpdateRedmineIssueTool;
import com.stupidbeauty.sisterfuture.tool.SearchRedmineTasksTool;
import com.stupidbeauty.sisterfuture.tool.GetIssuesListTool;
import com.stupidbeauty.sisterfuture.tool.EstablishTaskRelationshipTool;
import com.stupidbeauty.sisterfuture.tool.ListRedmineProjectsTool; // ✅ 新增导入


import com.stupidbeauty.sisterfuture.tool.BasicWebRequestTool;
import com.stupidbeauty.sisterfuture.tool.GenericWebRequestTool;
import com.stupidbeauty.sisterfuture.tool.GetContactListTool;
import com.stupidbeauty.sisterfuture.tool.FtpFileRequestTool;
import com.stupidbeauty.sisterfuture.tool.ListFtpDirectoryTool;
import com.stupidbeauty.sisterfuture.tool.FtpFileWriteTool;


import com.stupidbeauty.sisterfuture.tool.CreateRedmineTaskTool;


import com.stupidbeauty.sisterfuture.tool.WriteMemoryTool;
import com.stupidbeauty.sisterfuture.tool.SearchMemoryTool;
import com.stupidbeauty.sisterfuture.tool.ListAllMemoriesTool;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.AddNoteTool;
import com.stupidbeauty.sisterfuture.tool.AddShoppingItemTool; // ✅ 新增：确保导入存在




import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.manager.GuideManager;


import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;
import com.stupidbeauty.sisterfuture.tool.SwitchAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentAccessPointInfoTool;
import com.stupidbeauty.sisterfuture.tool.DeveloperInfoTool;
import com.stupidbeauty.sisterfuture.tool.SummaryAndShareTool;
import com.stupidbeauty.sisterfuture.tool.DelayedReplyTool;
import com.stupidbeauty.sisterfuture.tool.QueryToolEnhancementTool;
import com.stupidbeauty.sisterfuture.tool.SetToolEnhancementTool;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
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
// import android.annotationSuppressLint;
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
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
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


// ✅ 新增：导入 AddShoppingItemTool

import com.stupidbeauty.sisterfuture.tool.FuseSystemPromptTool; // 新增导入
import com.stupidbeauty.sisterfuture.tool.GetCurrentSystemPromptTool; // ✅ 修正为 tool 包

import com.stupidbeauty.sisterfuture.tool.CreateGitBranchTool; // ✅ 新增：导入 CreateGitBranchTool

// ✅ 新增：导入 ListShoppingItemsTool
import com.stupidbeauty.sisterfuture.tool.ListShoppingItemsTool;

// ✅ 新增：导入 RemoveAccessPointTool 和 ListAccessPointsTool (修复编译错误)
import com.stupidbeauty.sisterfuture.tool.RemoveAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.ListAccessPointsTool;

// ✅ 新增：导入 SearchWithBraveTool
import com.stupidbeauty.sisterfuture.tool.SearchWithBraveTool;

// ✅ 新增：导入 RemoveShoppingItemTool
import com.stupidbeauty.sisterfuture.tool.RemoveShoppingItemTool;

// ✅ 新增：导入 RemoteCommandTool
import com.stupidbeauty.sisterfuture.tool.RemoteCommandTool;

// ✅ 新增：导入 SearchFileInRepoTool




/*
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 
*/
public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
  private GuideManager guideManager ;

  private JSONObject firstToolCallDelta = null; // 用于缓存第一条 tool_calls 的 delta
  private boolean isFirstToolCallProcessed = false; // 标记是否已处理第一条
  private ModelAccessPointManager modelAccessPointManager;
  private ToolManager toolManager;
  private MemoryManager memoryManager;


  // 一级映射：通过 index 关联到原始 id
  private Map<Integer, String> indexToOriginalIdMap = new HashMap<>();

  // 工具调用累积状态（简化版，假设单次请求只有一个工具调用）
  private Map<String, Function> partialToolArgs = new HashMap<>();


  private static final Gson gson = new Gson();

  private ContextManager contextManager;
  private MessageAdapter messageAdapter;
  @BindView(R.id.articleListmy_recycler_view) RecyclerView articleListmyRecyclerView; //< Message list.
  private static final String DEFAULT_INPUT_TEXT = "君不见，黄河之水天上来，奔流到海不复回，君不见，高堂明镜悲白发，朝如青丝暮成雪，人生得意须尽欢，莫使金樽空对月";
  // 在 Activity 中添加一个变量用于追踪是否正在合成语音
  private StringBuilder accumulatedAnswer = new StringBuilder();

  private static final int PERMISSIONS_REQUEST =1; //!权限请求标识
  // 设这是您的通义千问客户端
  private TongYiClient tongYiClient;
  // 在 Activity 中添加一个变量用于追踪是否正在合成语音
  private boolean isTtsSpeaking = false;

  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO; //!录音权限。
  private static final String PERMISSION_FINE_LOCATIN = Manifest.permission.ACCESS_FINE_LOCATION; //!位置权限
  private static final String PERMISSION_INSTALL_PACKAGE = Manifest.permission.REQUEST_INSTALL_PACKAGES; //!安装应用程序权限
  private MediaPlayer mediaPlayer;
  private boolean voiceEndDetected=false; //!是否已经探测到用户声音结束。
  // private String textTitle;

  private TextToSpeech mTts;

  private static final int LanServicePort =10471;
  private String voiceRecognizeResultString; //!语音识别结果。
  private Vibrator vibrator;
  @BindView(R.id.sendButtonn2) Button sendButtonn2;
  @BindView(R.id.commandRecognizebutton2) Button commandRecognizebutton2; //!开始识别的按钮。
  @BindView(R.id.thinking_overlay) TextView thinking_overlay;
  @BindView(R.id.progressBar) ProgressBar progressBar; //!进度条。
  int ret = 0;
  private static final String TAG="SisterFutureActivity"; //!输出调试信息时使用的标记。
  // @BindView(R.id.speakerVerifyRegisterPasswordtextView) TextView speakerVerifyRegisterPasswordtextView; //!声纹注册密码文本标签。

		private SpeechRecognizer mIat; //!语言识别器。



	//@BindView(R.id.statustextView) TextView statustextView; //!用来显示状态的文字标签。



	@BindView(R.id.volumeIndicatorprogressBar) ProgressBar volumeIndicatorprogressBar; //!用来显示音量的进度条。

	@BindView(R.id.recognizeResulttextView) EditText recognizeResulttextView; //!识别结果。
  @Override
  public void onInit(int arg0)
  {
    // TODO 自动生成的方法存根
  }
// private java.util.Map<String, Function> partialToolArgs = new java.util.HashMap<>();

  private void accumulateToolCalls(List<ToolCall> calls)
  {
    for (ToolCall call : calls)
    {
      if (call == null || call.getFunction() == null) continue;

      int index = call.getIndex();


      // ✅ 一级映射：记录 index 到原始 id 的关系
      if (call.getId() != null && !call.getId().trim().isEmpty())
      {
        indexToOriginalIdMap.put(index, call.getId());
      }


      // ✅ 二级映射：通过原始 id 关联函数参数
      String originalId = indexToOriginalIdMap.get(index);
      if (originalId == null)
      {
        // ✅ Fallback: 使用 index + name 组合作为唯一 key
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
      // ✅ 通过原始 id 找到对应的 index
      int index = -1;
      for (Map.Entry<Integer, String> mapEntry : indexToOriginalIdMap.entrySet())
      {
        if (mapEntry.getValue().equals(entry.getKey()))
        {
          index = mapEntry.getKey();
          break;
        }
      }

      // ✅ 创建 toolCall，使用原始的 id
      ToolCall call = new ToolCall();
      call.setId(entry.getKey()); // 保留原始的 id
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

  /**
  * 止录音。
  **/
  public void stopRecordbutton2()
  {
    vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate( 100);

    if (voiceEndDetected) //之前已经探测到用户的声音结束。
    {}//if (voiceEndDetected) //之前已经探测到用户的声音结束。
    else //之前未探测到用户的声音结束。
    {
      mIat.stopListening(); //停止录音。
    }//else //之前未探测到用户的声音结束。

    volumeIndicatorprogressBar.setIndeterminate(true); //处于未决状态，以表示正在识别。
    volumeIndicatorprogressBar.setProgress(0); //进度归零。

    volumeIndicatorprogressBar.setVisibility(View.INVISIBLE); //停止录音，则不再显示音量。

    progressBar.setVisibility(View.VISIBLE); //显示进度条。

    commandRecognizebutton2.setEnabled(false); //禁用按钮。
    commandRecognizebutton2.setVisibility(View.INVISIBLE); //隐藏按钮。
  }
  /**
  * 在线命令词识别。
  **/
  public void commandRecognizebutton2startRecognize()
  {
    voiceEndDetected=false; //重置状态，未探测到用户的声音结束。

    vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    vibrator.vibrate( 100);
	if (mIat==null) //识别器未创建。
	{
		mIat=SpeechRecognizer.createRecognizer(this,null); //创建识别器。
	}//if (mIat==null) //识别器未创建。

    if (!setParam()) //参数设置失败。
    {
      // statustextView.setText("请先构建语法。");

      return;
    }//if (!setParam()) //参数设置失败。

    ret = mIat.startListening(mRecognizerListener);
    if (ret != ErrorCode.SUCCESS)
    {
      if (ret == ErrorCode.ERROR_COMPONENT_NOT_INSTALLED)
      {
      }
      else
      {
        // statustextView.setText("识别失败，错误码：" + ret);
      }
    }
    volumeIndicatorprogressBar.setIndeterminate(false); //处于决定状态，以表示音量值。
    progressBar.setVisibility(View.INVISIBLE); //隐藏显示进度条。
    recognizeResulttextView.setText(R.string.empty); //显示空白内容。
  }

  /**
  * 参数设置
  *
  * @return 是否设置成功。
  **/
  public boolean setParam()
  {
    boolean result = false;
    // 设置识别引擎
    String mEngineType = SpeechConstant.TYPE_CLOUD;
    mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
    // 设置返回结果为 json 格式
    mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

    if ("cloud".equalsIgnoreCase(mEngineType))
    {
      // 设置云端识别使用的语法 id
      mIat.setParameter(SpeechConstant.DOMAIN,"iat");
      mIat.setParameter(SpeechConstant.LANGUAGE,"zh_cn");
      mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
      result = true;
    }

    // 设置音频保存路径，保存音频格式支持 pcm、wav，设置路径为 sd 卡请注意 WRITE_EXTERNAL_STORAGE 权限
    // 注：AUDIO_FORMAT 参数语记需要更新版本才能生效
    mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
    mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/asr.wav"); //设置录音存储路径。

    return result;
  }
  private void displayExistingContext() {
      List<JSONObject> history = contextManager.getHistory();
      for (JSONObject msg : history) {
          String role = msg.optString("role");
          String content = msg.optString("content");

          // 只显示有 content 的 user 和 assistant 消息
          if ("user".equals(role) && !content.isEmpty()) {
              messageAdapter.addMessage(new MessageItem(content, MessageType.USER));
          } else if ("assistant".equals(role) && !content.isEmpty()) {
              messageAdapter.addMessage(new MessageItem(content, MessageType.AI));
          }
          // 忽略 tool 消息和其他无 content 的消息
      }
  }

  /**
  * 通用消息发送接口，供外部调用（如文字选中、语音输入等）
  **/
  public void sendMessageToSister(String message) {
      if (message == null || message.trim().isEmpty()) {
          return;
      }
      
      // 添加用户消息到界面
      messageAdapter.addMessage(new MessageItem(message, MessageType.USER));
      
      // 添加到上下文管理器
      contextManager.addUserMessage(message);
      
      // 发起聊天请求
      sendChatRequest();
  }
  
  /**
  * Send by button.
  **/
  @OnClick(R.id.sendButtonn2)
  public void sendButtonn2()
  {
    voiceRecognizeResultString = recognizeResulttextView.getText().toString();
    
    sendMessageToSister(voiceRecognizeResultString);
    // messageAdapter.addMessage(new MessageItem(voiceRecognizeResultString, MessageType.USER));
    // contextManager.addUserMessage(voiceRecognizeResultString);
    // sendChatRequest();
  }


  /**
  * 发送闲聊请求。
  **/
  private void sendChatRequest() 
  {
    recognizeResulttextView.setText(""); // Clear the recognize result or input content.
    
    // ✅ 新增：MVP 引导逻辑集成
    if (guideManager != null && guideManager.isEmptyAccessPointList()) {
        guideManager.processWithGuideLogic(voiceRecognizeResultString, new GuideManager.ChatCallback() {
            @Override
            public void onResponse(String message) {
                runOnUiThread(() -> {
                    messageAdapter.addMessage(new MessageItem(message, MessageType.AI));
                    scrollToBottom();
                    ttsSayReply(message);
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
        // 如果引导逻辑已处理，不再发送常规聊天请求
        return;
    }

    sendChatRequestTongYi(); // Send chat request to tong yi.
  }


  /**
  * Report that the operation has failed.
  * @param string 服务器回复的结果说明文字。
  **/
  protected void reportOperationFail(String string)
  {
    Toast.makeText(SisterFutureApplication.getAppContext(), string, Toast.LENGTH_LONG).show();   //做一个提示，Failed adding address ,please retry.
  }


  private void showThinkingOverlay()
  {
    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        // 正确更新遮罩层的文本
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
        // statustextView.setText("");
      }
    });
  }


  /**
  * 向通义千问发送请求并处理回复。
  **/
  private void sendChatRequestTongYi()
  {
    Log.d(TAG, CodePosition.newInstance().toString()); // Debug.

    if (voiceRecognizeResultString != null && !voiceRecognizeResultString.isEmpty())
    {
      accumulatedAnswer.setLength(0); // clear the last incremental result.
      // 显示思考状态
      showThinkingOverlay();

      // 获取当前访问点名称
      String currentApName = modelAccessPointManager.getCurrentAccessPoint().getName();

      // 获取历史消息（包含之前的 user/assistant 对话）
      JSONArray historyArray = contextManager.getMessagesArray();

      // 构造最终 messages 数组
      JSONArray messagesArray = new JSONArray();

      try
      {
        // system 消息必须在最前面，且不存入历史
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this); // ← 新增
        systemMsg.put("content", enhancedSystemPrompt);
        messagesArray.put(systemMsg);

        // 追加历史消息（user + assistant）
        for (int i = 0; i < historyArray.length(); i++)
        {
          String messageContent = historyArray.getJSONObject(i).optString("content");
          String messageRole = historyArray.getJSONObject(i).optString("role");
          String toolCAllId = historyArray.getJSONObject(i).optString("tool_call_id");

          if (messageRole.equals("assistant") || messageRole.equals("user")) // assistant message or user message
          {
            String[] parts = messageContent.split("\n");
            if (parts.length >1)
            {
              String maxWidthStr = parts[0];
              messageContent = maxWidthStr + " ...";
            }
          } // if (role.equals("assistant")) // assistant message

          if ((messageContent.isEmpty()) && (messageRole.equals("assistant")) )
          {
            messageContent = historyArray.getJSONObject(i).toString();
          } // if ((messageContent.isEmpty()) && (messageRole.equals("assistant")) )


          Log.d(TAG, CodePosition.newInstance().toString() + ", adding message with role: " + messageRole + ", content: " + messageContent + ", tool call id: " + toolCAllId); // Debug.


          messagesArray.put(historyArray.getJSONObject(i));
        }
      }
      catch (Exception e)
      {
        e.printStackTrace();


        // 出错时至少发送当前用户消息（降级）
        try
        {
          messagesArray = new JSONArray();
          String enhancedSystemPrompt = buildEnhancedSystemPrompt(toolManager, this); // ← 新增

          messagesArray.put(new JSONObject().put("role", "system").put("content", enhancedSystemPrompt));
          messagesArray.put(new JSONObject().put("role", "user").put("content", voiceRecognizeResultString));
        }
        catch (Exception ignored)
        {
        }
      }

      // 使用通义千问客户端发送请求
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
              // ✅ 新增：先读取响应体并检测是否为 HTML
              String errorBody = response.body().string();
              Log.e(TAG, "Error body: " + errorBody);
              
              // 检测 HTML 响应
              if (isHtmlResponse(errorBody))
              {
                Log.e(TAG, "API 返回 HTML 页面而非 JSON，跳过 Gson 解析，防止崩溃。");
                runOnUiThread(() -> {
                  messageAdapter.addMessage(new MessageItem("API 服务异常：返回了 HTML 页面而非 JSON。请检查接入点配置。", MessageType.AI));
                  scrollToBottom();
                });
                return; // 直接返回，不执行后续 Gson 解析
              }
              
              // 非 HTML 响应，继续原有逻辑
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
          }
          else
          {
            // 其他异常，不做重试
            Log.e(TAG, "未知异常，不触发重试：" + error.getMessage());
          }

          // ✅ 重试逻辑：仅在接入点不可用时触发
          if (isAccessPointUnavailable)
          {
            sendChatRequestTongYi();
          }
        }
      },
() ->
      {
        // ✅ 流结束回调
      }
      );
    }
    else
    {
      Log.w(TAG, "Voice recognition result is empty or null.\n");
    }
  }

  /**
   * 检测响应内容是否为 HTML 页面
   * 用于防止 API 返回错误页面（如登录页、404 页）时客户端解析崩溃
   */
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

  /**
  * 解析提交问题的结果。
  **/
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

          sendChatRequestTongYi(); // Request again.
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

      // ✅ 仅累积 tool_calls 内容
      if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty())
      {
        accumulateToolCalls(delta.getToolCalls());
      }

      // ✅ 判断 finish_reason 是否为 tool_calls —— 唯一构造时机
      if ("tool_calls".equals(choice.getFinishReason()))
      {
        runOnUiThread(() ->
        {
          try
          {
            List<ToolCall> finalCalls = getFinalToolCalls();

            // ✅ 查 finalCalls 是否为空
            if (finalCalls == null || finalCalls.isEmpty()) {
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

              // 构造 tool_call 对象
              JSONObject toolCallObject = new JSONObject();
              toolCallObject.put("id", toolCallId);
              toolCallObject.put("type", "function");

              JSONObject functionObject = new JSONObject();
              functionObject.put("name", toolName);
              functionObject.put("arguments", argsJsonStr);
              toolCallObject.put("function", functionObject);
              toolCallsArray.put(toolCallObject);

              // ✅ 区分同步与异步
              if (toolManager.isToolAsync(toolName))
              {
                // 异步工具：通过回调收集结果
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

                      // 否全部完成
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
                    // 即使出错也尝试继续（避免卡死）
                    postProcessToolResults(pendingResults, assistantMessage, toolCallsArray);
                  }
                });
              }
              else
              {
                // 同步工具：立即执行并记录
                JSONObject toolResult = new JSONObject();

                // 在界面类的工具调用部分添加完整的异常处理
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

            // 保存 assistant 消息
            assistantMessage.put("tool_calls", toolCallsArray);
            contextManager.addRawMessage(assistantMessage);
            contextManager.increaseMaxRounds();

            // 跟踪上下文写入，在 UI 中显示"正在调用"消息
            runOnUiThread(() -> {
                StringBuilder callText = new StringBuilder("🛠️ 正在调用工具：\n");
                for (ToolCall call : finalCalls) {
                    if (call != null && call.getFunction() != null) {
                        String toolName = call.getFunction().getName();
                        callText.append("- `").append(toolName).append("`").append("\n");
                    }
                }

                // 使用 AI 消息类型，复用 AIMessageViewHolder
                messageAdapter.addMessage(new MessageItem(callText.toString(), MessageType.AI));
                scrollToBottom();
            });

            // 如果全是同步工具，直接处理；否则等待回调
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

      // ✅ 文本流处理逻辑不变
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

  // ✅ 新增私有方法：用于处理最终的工具结果
  private void postProcessToolResults(java.util.Map<String, JSONObject> pendingResults,
                                    JSONObject assistantMessage,
                                    JSONArray toolCallsArray)
  {
    runOnUiThread(() ->
    {
      try
      {
        // ✅ 统一追加所有工具结果
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
            // 就在这里...啊...主任轻点...添加消息显示...
            messageAdapter.addMessage(new MessageItem(
                "🛠️ 工具调用结果：" + name + "\n" + result.toString(), 
                MessageType.TOOL_CALL_RESULT));
          }
        }

        clearAccumulatedToolCalls();

        int messagesAmount = contextManager.getHistory().size();
        Log.d(TAG, "Final messages array before sending request: amount: " + messagesAmount);
        int startEndMessagsOutputAmount = 5;
        boolean outputDotsDone = false;

        for (int i = 0; i < messagesAmount; i++)
        {
          if ((i >= startEndMessagsOutputAmount) && (i < (messagesAmount-startEndMessagsOutputAmount) )) // It is in the middle, skip, not output.
          {
            if (!outputDotsDone)
            {
              Log.d(TAG, "  [...] ");

              outputDotsDone = true;
            }//if (!outputDotsDone)
          }//if ((i >= startEndMessagsOutputAmount) && (i <= (messagesAmount-startEndMessagsOutputAmount) )) // It is in the middle, skip, not output.
          else // output.
          {
            JSONObject msg = contextManager.getHistory().get(i);
            if (msg!=null)//The msg exists
            {
              Log.d(TAG, "  [" + i + "] " + msg.toString(2));
            }//if (msg!=null)//The msg exists
          }//else //output.
        }

        sendChatRequestTongYi();
      }
      catch (Exception e)
      {
        Log.e(TAG, "Error in postProcessToolResults", e);
      }
    });
  }

  /**
   * 判断是否为"上下文长度超出限制"的错误。
  **/
  private boolean isContextLengthError(String errorMessage)
  {
    if (errorMessage == null) return false;
    // 根据你日志里的实际错误信息匹配
    return errorMessage.contains("Range of input length should be") ||
           errorMessage.contains("context length") ||
           errorMessage.contains("exceeds the available context size") ||
           errorMessage.contains("exceeds maximum context length");
  }

  private void scrollToBottom()
  {
    // 移动到列表的最后一个可见项
    articleListmyRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() -1);
  }

    // statustextView.setText(answer); //显示结果。
  @Override
  public void onBackPressed()
  {
    if (null!=mTts) //TTS 引擎还在。
    {
      mTts.shutdown(); //关闭。
    }//if (null!=mTts) //TTS 引擎还在。



    super.onBackPressed();
  }


  // 修改 ttsSayReply 方法
  private void ttsSayReply(final String text)
  {
    // 直接开始语音合成
    // tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
    // ttsByAndroidSystemTts(text); //使用系统自带的 TTS 接口。
    // ttsByBiaoBei(text); //使用标贝语音来发声。
    ttsByFindroidTts(text); // 使用 findroid 介绍的 TTS 接口。
  }

  /**
  *  使用 findroid 介绍的 TTS 接口。
  * https://github.com/tatans-coder/TensorflowTTS_chinese/blob/master/app/src/main/java/net/tatans/tensorflowtts/MainActivity.java
  **/
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
      volumeIndicatorprogressBar.setProgress(i); //显示新的值。
		}

		@Override
		public void onBeginOfSpeech()
    {
      voiceRecognizeResultString="";//重置识别结果。

		volumeIndicatorprogressBar.setVisibility(View.VISIBLE); //显示音量。
		}

		@Override
		public void onEndOfSpeech()
    {
		volumeIndicatorprogressBar.setVisibility(View.INVISIBLE); //不显示音量。

      voiceEndDetected=true; //记录，已经探测到用户声音结束。
		}

		@Override
		public void onResult(RecognizerResult recognizerResult, boolean b)
    {
      progressBar.setVisibility(View.INVISIBLE); //隐藏显示进度条。

      commandRecognizebutton2.setVisibility(View.VISIBLE); //重新显示按钮。
      commandRecognizebutton2.setEnabled(true); //启用按钮。
      //完整内容:
		String text=recognizerResult.getResultString(); //结果字符串。

      Gson gson=new Gson(); //创建 gson 对象。
		VoiceRecognizeResult voiceRecognizeResult=gson.fromJson(text, VoiceRecognizeResult.class); //解析成结果对象。
		String saidText=voiceRecognizeResult.getSaidText(); //获取完整的说出内容。

      recognizeResulttextView.append(saidText); //显示内容。

      voiceRecognizeResultString=voiceRecognizeResultString+saidText; //追加结果。

      boolean isLast=voiceRecognizeResult.isLs(); //获取属性，是否是最终结果。

      if (isLast) 
      {
        sendMessageToSister(voiceRecognizeResultString);

        // messageAdapter.addMessage(new MessageItem(voiceRecognizeResultString, MessageType.USER));
        // contextManager.addUserMessage(voiceRecognizeResultString);
        // sendChatRequest();
      }
	}

    @Override
		public void onError(SpeechError speechError)
		{
      commandRecognizebutton2.setVisibility(View.VISIBLE); //重新显示按钮。

      commandRecognizebutton2.setEnabled(true); //启用按钮。
      progressBar.setVisibility(View.INVISIBLE); //隐藏显示进度条。
		String errorText=speechError.getErrorDescription(); //获取错误信息。

		recognizeResulttextView.setText(errorText+",error code:"+speechError.getErrorCode()); //显示错误信息。
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
      switch (event.getAction()) //根据不同事件进行处理。
      {
        case MotionEvent.ACTION_DOWN: //按下。
          commandRecognizebutton2startRecognize(); //开始识别。




          break;//跳出;


        case MotionEvent.ACTION_UP: //松开。
          stopRecordbutton2(); //停止识别。

          break;//跳出;
      }//switch (event.getAction()) //根据不同事件进行处理。
      return true;
   }
  };

  private void connectSignals()
  {
    commandRecognizebutton2.setOnTouchListener(commandRecognizeButtonTouchListener);
  }

  private void startHttpServer()
  {
    AsyncHttpServer server=new AsyncHttpServer(); //Create the async server.
    CommitTextCallback commitTextCallback=new CommitTextCallback(); //创建回调对象，告知有人订台.
    server.get("/commitText/", commitTextCallback); //添加这个回调对象.
    PhoneInformationCallback phoneInformationCallback=new PhoneInformationCallback(); //创建回调对象，查询手机信息.
    server.get("/phoneInformation/", phoneInformationCallback); //添加这个回调对象.
    server.listen(LanServicePort); //监听 15563 端口.tcp。
}


  /**
  * 构造增强版系统提示词，从每个工具的 getDefinition() 中提取 description。
  **/
  private static String buildEnhancedSystemPrompt(ToolManager toolManager, Context context)
  {

SystemPromptManager promptManager = SystemPromptManager.getInstance(context);


    StringBuilder promptBuilder = new StringBuilder();
    // promptBuilder.append(SfBaseDef.DEFAULT_SYSTEM_PROMPT);




//promptBuilder.append(  promptManager.getBasePrompt()  );


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

      // 新增：追加工具自身的系统提示增强
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
	/**
  *此活动正在被创建。
  **/
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState); //超类创建。




		requestWindowFeature(Window.FEATURE_NO_TITLE); //不显示标题栏。
		
		setContentView(R.layout.sister_future); //显示界面。

    // ==================== INIT METHODS CALLS ====================
    initServices();         // TTS, HTTP, Media 等服务初始化 (<50 行)
    initData();             // Context, Memory 等数据层初始化 (<50 行)
    initTools();            // ToolManager 及工具注册 (<50 行)
    initView();             // ButterKnife, RecyclerView 等视图绑定 (<50 行)
    checkPermission();      // 权限检查 (<50 行)
    connectSignals();       // 信号槽连接 (<50 行)
    displayExistingContext(); // 显示历史消息 (<50 行)
	}

  // ==================== INIT METHODS DEFINITIONS (平级于 onCreate) ====================

  /**
   * 初始化服务层 (TTS, HTTP, Media 等)
   * //~35 lines
   */
  private void initServices() {
    TtsManager.getInstance().init(this);
    registerBroadcastReceiver();
    startHttpServer();
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
  }

  /**
   * 初始化数据层 (Context, Memory, ModelAccess 等)
   * //~20 lines
   */
  private void initData() {
    contextManager = new ContextManager(this);
    // ✅ 修改为：注入 ModelAccessPointManager 实例给新工具
    modelAccessPointManager = new ModelAccessPointManager(this);
    // ✅ 新增：初始化 MemoryManager
    memoryManager = new MemoryManager(this);
  }

  /**
   * 初始化工具管理器
   * //~20 lines (注意：registerTool 将在 #4670 提取至独立类)
   */
  private void initTools() {
    toolManager = new ToolManager();
    
    // ✅ 新增：每次启动时清空聊天历史（但保留 currentMaxRounds）
    // contextManager.replaceHistory(new ArrayList<>());
    
    // 初始化工具注册（临时保留在 Activity，#4670 将提取至 ToolRegistry）
    toolManager.registerTool(new ConversationResetTool(contextManager)); // ← 注入
    toolManager.registerTool(new GetCurrentTimeTool()); // ← 新增
    toolManager.registerTool(new SwitchAccessPointTool(modelAccessPointManager));
    toolManager.registerTool(new GetCurrentAccessPointInfoTool(modelAccessPointManager));
    toolManager.registerTool(new DeveloperInfoTool());
    toolManager.registerTool(new SummaryAndShareTool(this, modelAccessPointManager, toolManager, contextManager));
    toolManager.registerTool(new DelayedReplyTool(this));
    // ✅ 新增：注册查询工具增强提示词工具
    toolManager.registerTool(new QueryToolEnhancementTool(toolManager, this));
    toolManager.registerTool(new SetToolEnhancementTool(toolManager, this));

    // ✅ 新增：注册读取和设置工具备注的工具
    toolManager.registerTool(new GetToolRemarkTool(toolManager, this));
    toolManager.registerTool(new SetToolRemarkTool(toolManager, this));
    toolManager.registerTool(new GetRedmineTaskInfoTool(this));
    toolManager.registerTool(new CreateRedmineTaskTool(this));
    toolManager.registerTool(new UpdateRedmineIssueTool(this));
    toolManager.registerTool(new SearchRedmineTasksTool(this));
    toolManager.registerTool(new GetIssuesListTool(this));
    toolManager.registerTool(new ListRedmineProjectsTool(this)); // ✅ 新增：注册新工具
    toolManager.registerTool(new EstablishTaskRelationshipTool(this));
    
    
    toolManager.registerTool(new BasicWebRequestTool(this));
    toolManager.registerTool(new GenericWebRequestTool(this)); // ✅ 新增：通用 HTTP 请求工具
    toolManager.registerTool(new GetContactListTool(this));
    
    toolManager.registerTool(new FtpFileRequestTool(this));
    toolManager.registerTool(new ListFtpDirectoryTool(this));
    toolManager.registerTool(new FtpFileWriteTool(this));



    toolManager.registerTool(new WriteMemoryTool(memoryManager, this));
    toolManager.registerTool(new SearchMemoryTool(memoryManager, this));
    toolManager.registerTool(new ListAllMemoriesTool(memoryManager, this));
    
    toolManager.registerTool(new AddModelAccessPointTool(modelAccessPointManager, this));
    
    toolManager.registerTool(new AddNoteTool(this));
    toolManager.registerTool(new RemoveNoteTool(this));    
    toolManager.registerTool(new ListNotesTool(this)); // 注册列出记事工具
    
    toolManager.registerTool(new GetGitHubFileTool(this)); // 注册列出记事工具
    toolManager.registerTool(new CreateGitHubCommitTool(this)); // 注册列出记事工具

    // ✅ 注册 fuse_system_prompt 工具
    toolManager.registerTool(new FuseSystemPromptTool(this));

    // ✅ 修复：使用 casted SisterFutureApplication instance
    SisterFutureApplication app = (SisterFutureApplication) SisterFutureApplication.getAppContext();
    toolManager.registerTool(new GetCurrentSystemPromptTool(app));

    // ✅ 新增：注册 create_git_branch 工具
    toolManager.registerTool(new CreateGitBranchTool(this));

    // ✅ 新增：注册 ListShoppingItemsTool
    toolManager.registerTool(new ListShoppingItemsTool(this));

    // ✅ 正确的注册顺序：确保 AddShoppingItemTool 在最后面，不会影响其他工具的注释。
    toolManager.registerTool(new AddShoppingItemTool(this));

    // ✅ 新增：注册 RemoveAccessPointTool
    toolManager.registerTool(new RemoveAccessPointTool(modelAccessPointManager, this));

    // ✅ 新增：注册 ListAccessPointsTool
    toolManager.registerTool(new ListAccessPointsTool(modelAccessPointManager, this));

    // ✅ 新增：注册 SearchWithBraveTool
    toolManager.registerTool(new SearchWithBraveTool(this));

    // ✅ 新增：注册 RemoveShoppingItemTool
    toolManager.registerTool(new RemoveShoppingItemTool(this));

    // ✅ 新增：注册 RemoteCommandTool
    toolManager.registerTool(new RemoteCommandTool(this));

    // ✅ 新增：注册 SearchFileInRepoTool
    toolManager.registerTool(new SearchFileInRepoTool(this));
  }

  /**
   * 初始化视图层 (ButterKnife, RecyclerView 等)
   * //~30 lines
   */
  private void initView() {
    ButterKnife.bind(this);
    initializeMsc(); // MSC 初始化
    checkPermission(); // 权限检查
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
          voiceRecognizeResultString = recognizeResulttextView.getText().toString(); // Get the input text.
          sendChatRequest();
          return true; //消耗事件
        }
        return false;
      }
    });

    tongYiClient = new TongYiClient(modelAccessPointManager, toolManager);

    // ✅ 新增：创建并注册 GuideManager
    guideManager = new GuideManager(this, modelAccessPointManager, toolManager);

    String question = getIntent().getStringExtra("question");
    if (question != null) {
        // 自动发送给 AI 引擎
        sendMessageToSister(question);
    }
  }

  private boolean hasPermission()
  {
    boolean result=false; //结果。

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //安卓 6.
    {
      ArrayList<String> articleInfoArrayList = new ArrayList<>(); //权限列表。
        
      articleInfoArrayList.add(PERMISSION_STORAGE);
      articleInfoArrayList.add(PERMISSION_RECORD_AUDIO);
      articleInfoArrayList.add(PERMISSION_FINE_LOCATIN);
      // articleInfoArrayList.add(PERMISSION_INSTALL_PACKAGE); //安装应用程序的权限。
        
      for(String permissionString: articleInfoArrayList) //一个个检查
      {
        Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString); //Debug.
        result=(checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED); //录音权限。
          
        if (!result) //没有权限
        {
          Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString + ", no permission"); //Debug.
          break;//没有权限。
        }//if (!result)//没有权限
      }//for(String permissionString: articleInfoArrayList)//一个个检查
    }//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)//安卓 6.
    else //旧版本。
    {
      result=true;//有权限。
    }//else //旧版本。

    return result;
  }

  /**
  * 请求获取权限
  **/
  private void requestPermission()
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //动态权限
    {
      if ( shouldShowRequestPermissionRationale(PERMISSION_STORAGE)  || shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO) || shouldShowRequestPermissionRationale(PERMISSION_FINE_LOCATIN)  || shouldShowRequestPermissionRationale(PERMISSION_INSTALL_PACKAGE)) //应当告知原因。
      {
        Toast.makeText(this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }//if ( shouldShowRequestPermissionRationale(PERMISSION_STORAGE)  || shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO)) //应当告知原因。
      Log.d(TAG, CodePosition.newInstance().toString() ); //Debug.


      // requestPermissions(new String[] {PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO, PERMISSION_FINE_LOCATIN, PERMISSION_INSTALL_PACKAGE}, PERMISSIONS_REQUEST);
      requestPermissions(new String[] {PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO, PERMISSION_FINE_LOCATIN}, PERMISSIONS_REQUEST);
    }//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)//动态权限
  }
    
  /**
  * 查权限。
  **/
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


  /**
  * 注册广播事件接收器。
  **/
  private void registerBroadcastReceiver()
  {
    IntentFilter filter = new IntentFilter();

    filter.addAction(Constants.Operation.CommitText); //提交文本内容。
    filter.addAction(Constants.NativeMessage.NOTIFY_CALLBACK_IP); //报告回调 IP。
    filter.addAction(Constants.Operation.HideKeyboard); //隐藏软键盘。

    LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(this); //Get the local broadcast manager instance.
    localBroadcastManager.registerReceiver(mBroadcastReceiver, filter); //注册接收器。
  }
  
  /**
  * 广播接收器。
  **/
  private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
  {
    @Override
    /**
    *接收到广播。
    **/
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction(); //获取广播中带的动作字符串。




      if (Constants.Operation.CommitText.equals(action)) //提交文本内容。
      {
        Bundle extras=intent.getExtras(); //获取参数包。




        voiceRecognizeResultString= extras.getString("text"); //记录识别结果。




        recognizeResulttextView.setText(voiceRecognizeResultString); //显示结果。




        sendChatRequest(); //发送闲聊请求。
        startFriendShutDownAt2100Service(); //启动友军"21 点关机"的服务。
      }
    }
  };



  /**
  * 启动友军"21 点关机"的服务。
  **/
  protected void startFriendShutDownAt2100Service()
  {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName("com.stupidbeauty.shutdownat2100androidnative", "com.stupidbeauty.shutdownat2100androidnative.TimeCheckService")); //设置组件。
    startService(intent); //启动服务。
  }

  /**
  * 初始化 MSC。
  **/
  private void initializeMsc()
  {
    SpeechUtility.createUtility(this, SpeechConstant.APPID+"=56e142d3"); //创建工具。

    mIat= SpeechRecognizer.createRecognizer(this, null);
  }
}
