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
import androidx.activity.result.ActivityResultLauncher;
import android.net.Uri;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.util.Base64;
import androidx.activity.result.contract.ActivityResultContracts;
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
  // 📷 #280 图片输入功能相关变量
  private ActivityResultLauncher<Intent> imagePickerLauncher;
  private String currentImageBase64 = null;
  private Button uploadImageButton;
  private Vibrator vibrator;
  @BindView(R.id.sendButtonn2) Button sendButtonn2;
  @BindView(R.id.commandRecognizebutton2) Button commandRecognizebutton2;
  @BindView(R.id.thinking_overlay) TextView thinking_overlay;
  @BindView(R.id.progressBar) ProgressBar progressBar;
  int ret = 0;
  private static final String TAG="SisterFutureActivity";

  private SpeechRecognizer mIat;

  // 📷 #280 初始化图片选择器
  private void initImagePicker()
  {
    // 由于 Activity 不支持 registerForActivityResult，需要使用 fragment 或者手动管理 requestCode
    // 这里我们使用传统的 startActivityForResult 方式
    // imagePickerLauncher 变量保留但暂不使用
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
        uploadImageButton.setVisibility(View.VISIBLE);
      });
      
      FileLogger.i(TAG, "📷 [IMAGE_LOADED] 图片已加载，Base64 长度：" + (currentImageBase64 != null ? currentImageBase64.length() : 0));
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [IMAGE_ERROR] 加载图片失败", e);
      runOnUiThread(() -> {
        Toast.makeText(this, "❌ 图片加载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
      });
    }
  }
  
  // 📷 #280 图片上传按钮点击事件
  @OnClick(R.id.uploadImageButton)
  public void onUploadImageButton()
  {
    if (currentImageBase64 != null)
    {
      currentImageBase64 = null;
      uploadImageButton.setVisibility(View.GONE);
      Toast.makeText(this, "🗑️ 已清除图片", Toast.LENGTH_SHORT).show();
      FileLogger.d(TAG, "🗑️ [IMAGE_CLEARED] 用户清除了暂存的图片");
    }
    else
    {
      openImagePicker();
    }
  }
  
  // 📷 #280 打开图片选择器
  private void openImagePicker()
  {
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
    
    if (requestCode == 1001 && resultCode == RESULT_OK && data != null)
    {
      handleSelectedImage(data);
    }
  }
  
  // 📷 #280 发送带图片的消息
  private void sendMessageWithImage(String textMessage)
  {
    boolean hasImage = (currentImageBase64 != null && !currentImageBase64.isEmpty());
    
    if (!hasImage && (textMessage == null || textMessage.trim().isEmpty()))
    {
      FileLogger.w(TAG, "⚠️ [SEND_CANCELLED] 没有图片和文字，取消发送");
      return;
    }
    
    if (hasImage)
    {
      FileLogger.i(TAG, "📷 [SEND_WITH_IMAGE] 发送带图片的消息 | 文字长度：" + (textMessage != null ? textMessage.length() : 0) + " | Base64 长度：" + currentImageBase64.length());
      
      try
      {
        JSONArray contentArray = new JSONArray();
        
        if (textMessage != null && !textMessage.trim().isEmpty())
        {
          JSONObject textContent = new JSONObject();
          textContent.put("type", "text");
          textContent.put("text", textMessage);
          contentArray.put(textContent);
        }
        
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + currentImageBase64);
        imageContent.put("image_url", imageUrl);
        contentArray.put(imageContent);
        
        JSONObject currentUserMsg = new JSONObject();
        currentUserMsg.put("role", "user");
        currentUserMsg.put("content", contentArray);
        
        contextManager.addRawMessage(currentUserMsg);
        messageAdapter.addMessage(new MessageItem(hasImage ? "📷 [图片消息]" : textMessage, MessageType.USER));
        
        currentImageBase64 = null;
        uploadImageButton.setVisibility(View.GONE);
        
        scrollToBottom();
        sendChatRequestTongYi();
      }
      catch (JSONException e)
      {
        FileLogger.e(TAG, "❌ [MULTIMODAL_ERROR] 构建多模态消息失败", e);
        runOnUiThread(() -> {
          Toast.makeText(this, "❌ 构建消息失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    }
    else
    {
      sendMessageToSister(textMessage);
    }
  }