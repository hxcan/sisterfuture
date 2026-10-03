// SisterFutureActivity.java
package com.stupidbeauty.sisterfuture;

import java.io.File;
import java.io.FileOutputStream;
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
import com.stupidbeauty.sisterfuture.bean.MessageItem;
import com.stupidbeauty.sisterfuture.bean.MessageType;
import com.stupidbeauty.sisterfuture.bean.Attachment;
import com.stupidbeauty.sisterfuture.bean.AttachmentMetadata;
import com.stupidbeauty.sisterfuture.bean.ModelUsage;
import com.stupidbeauty.sisterfuture.bean.Delta;
import com.stupidbeauty.sisterfuture.bean.Choice;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import com.stupidbeauty.sisterfuture.R;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import java.util.List;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RadioGroup;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
import android.app.AlertDialog;
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
import com.stupidbeauty.sisterfuture.network.TongYiClient.OnResponseListener;
import com.stupidbeauty.sisterfuture.adapter.MessageAdapter;
import com.stupidbeauty.sisterfuture.manager.GuideManager;
import com.stupidbeauty.sisterfuture.manager.PermissionManager;
import com.stupidbeauty.sisterfuture.manager.RepeatDetectionManager;
import com.stupidbeauty.sisterfuture.manager.OssManager;
import com.stupidbeauty.sisterfuture.manager.EmptyDeltaDetectionManager;
import com.stupidbeauty.sisterfuture.manager.TurnUsageTracker;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import com.stupidbeauty.sisterfuture.utils.ToolArgumentsParser;
import com.google.gson.Gson;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import com.google.gson.JsonSyntaxException;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.lanime.Constants;
import com.stupidbeauty.lanime.callback.CommitTextCallback;
import com.stupidbeauty.lanime.callback.PhoneInformationCallback;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.stupidbeauty.lanime.network.volley.MapUtils;
import com.stupidbeauty.sisterfuture.tool.Tool;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.util.Log;
import butterknife.OnClick;
import com.iflytek.cloud.SpeechRecognizer;
import butterknife.ButterKnife;
import butterknife.BindView;
import com.stupidbeauty.sisterfuture.bean.Delta;
import com.stupidbeauty.sisterfuture.bean.Choice;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import net.tatans.tensorflowtts.utils.ThreadPoolManager;
import net.tatans.tensorflowtts.tts.TtsManager;

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

  private SessionManager sessionManager;
  private long sessionGeneration;
  private final java.util.Map<Long, ContextManager> turnContexts = new java.util.concurrent.ConcurrentHashMap<>();
  private static final class RequestState {
    final long generation;
    final com.stupidbeauty.sisterfuture.utils.PerformanceStats arrival = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();
    final com.stupidbeauty.sisterfuture.utils.PerformanceStats queue = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();
    final com.stupidbeauty.sisterfuture.utils.PerformanceStats handling = new com.stupidbeauty.sisterfuture.utils.PerformanceStats();
    long lastArrival;
    synchronized void received(long now) {
      if (lastArrival != 0) arrival.record(now - lastArrival);
      lastArrival = now;
    }
    void report(boolean force) {
      arrival.report("stream_callback_gap", force);
      queue.report("stream_ui_queue", force);
      handling.report("stream_ui_handling", force);
    }
    final ContextManager context;
    final ToolManager tools;
    final Map<Integer, String> originalIds = new HashMap<>();
    final Map<String, Function> arguments = new java.util.LinkedHashMap<>();
    RequestState(ContextManager context, ToolManager tools, long generation) {
      this.context = context; this.tools = tools; this.generation = generation;
    }
  }
  // UI-facing context; in-flight requests retain their original owner and generation.
  private ContextManager contextManager;
  private MessageAdapter messageAdapter;
  @BindView(R.id.articleListmy_recycler_view) RecyclerView articleListmyRecyclerView;
  private static final String DEFAULT_INPUT_TEXT = "君不见，黄河之水天上来，奔流到海不复回，君不见，高堂明镜悲白发，朝如青丝暮成雪，人生得意须尽欢，莫使金樽空对月";

  private StringBuilder accumulatedAnswer = new StringBuilder();

  private ActivityResultLauncher<Intent> imagePickerLauncher;
private String currentImageBase64 = null; // 🔥 保留：仅作为 OSS 失败时的兜底通道（任务 #910050720382，主人 2026-10-03 拍板）
  private String currentImagePath = null;  // WanxiangImage 工具支持参考图片：图片本地缓存路径
  private String currentImageRemoteUrl = null; // 🔥 新增：图片 OSS 签名 URL（与视频消息一致，任务 #910050720382）
  private String currentImageOssObjectKey = null; // 🔥 新增：图片 OSS 对象 key（用于 refresh URL）
  private long currentImageUrlExpiresAt = 0L; // 🔥 新增：图片 OSS URL 过期时间戳
  private volatile boolean isImageProcessing = false; // 🔥 新增：图片上传 OSS 进行中标志
  private String currentVideoPath = null;
  private String currentVideoMimeType = null;
  private String currentVideoRemoteUrl = null;
  private String currentVideoOssObjectKey = null;
  private long currentVideoUrlExpiresAt = 0L;
  private volatile boolean isVideoProcessing = false;
  private volatile int mediaSelectionGeneration = 0;
  @BindView(R.id.uploadImageButton) Button uploadImageButton;
  @BindView(R.id.resetContextButton) Button resetContextButton;

  private TongYiClient tongYiClient;
  private boolean isTtsSpeaking = false;

  private MediaPlayer mediaPlayer;
  private boolean voiceEndDetected=false;

  private TextToSpeech mTts;

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

  private boolean isDeadlockRescueMode = false;

  private int rateLimitRetryCount = 0;
  private static final int MAX_RATE_LIMIT_RETRIES = 3;

  // A group of tool calls returned by one model response counts as one hop.
  private static final int MAX_TOOL_CALL_HOPS = 8;
  private static final int MAX_CONSECUTIVE_TOOL_ERROR_HOPS = 3;
  private int toolCallHopCount = 0;
  private int consecutiveToolErrorHops = 0;
  private final TurnUsageTracker turnUsageTracker = new TurnUsageTracker();
  private final Map<Long, String> finalAssistantMessageIds = new HashMap<>();
  private volatile long activeUsageTurnId = 0L;

  private volatile long currentRequestId = 0;
  private volatile long lastSuccessRequestId = 0;
  private static final int FTP_SERVER_PORT = 2123;
  private BuiltinFtpServer builtinFtpServer = null;
  private BuiltinFtpServerErrorListener builtinFtpServerErrorListener = null;


	@Override
	public void onInit(int arg0)
	{

  }

  private void accumulateToolCalls(List<ToolCall> calls, Map<Integer, String> indexToOriginalIdMap, Map<String, Function> partialToolArgs)
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

  private List<ToolCall> getFinalToolCalls(Map<Integer, String> indexToOriginalIdMap, Map<String, Function> partialToolArgs)
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
      if (ret == ErrorCode.ERROR_COMPONENT_NOT_INSTALL