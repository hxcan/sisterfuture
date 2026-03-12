package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.tool.RemoveNoteTool;
import com.stupidbeauty.sisterfuture.tool.ListNotesTool;
import com.stupidbeauty.sisterfuture.tool.GetGitHubFileTool;
import com.stupidbeauty.sisterfuture.tool.CreateGitHubCommitTool;


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
import com.stupidbeauty.sisterfuture.tool.ListRedmineProjectsTool; \// ✅ 新增导入
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
import com.stupidbeauty.sisterfuture.tool.AddShoppingItemTool; // ✅ 新增：注内孙在
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


import com.stupidbeauty.sisterfuture.tool.FuseSystemPromptTool; // 新增忼酥
import com.stupidbeauty.sisterfuture.tool.GetCurrentSystemPromptTool; // ✅ 俞歏下 tool 包
import com.stupidbeauty.sisterfuture.tool.CreateGitBranchTool; // ✅ 新增：导兡 CreateGitBranchTool

import com.stupidbeauty.sisterfuture.tool.ListShoppingItemsTool;

import com.stupidbeauty.sisterfuture.tool.RemoveAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.ListAccessPointsTool;

import com.stupidbeauty.sisterfuture.tool.SearchWithBraveTool;

import com.stupidbeauty.sisterfuture.tool.RemoveShoppingItemTool;


import com.stupidbeauty.sisterfuture.tool.RemoteCommandTool;


import com.stupidbeauty.sisterfuture.tool.SearchFileInRepoTool;





public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
  private GuideManager guideManager ;

  private JSONObject firstToolCallDelta = null; // 用于缓存第一条 tool_calls 的 delta
  private boolean isFirstToolCallProcessed = false; // 蠇记是否已处理第一条
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


  private TextToSpeech mTts;

  private static final int LanServicePort =10471;
  private String voiceRecognizeResultString; //!语音识别结果。
  private Vibrator vibrator;
  @BindView(R.id.sendButtonn2) Button sendButtonn2;
  @BindView(R.id.commandRecognizebutton2) Button commandRecognizebutton2; //!开始识别的按钮。
  @BindView(R.id.thinking_overlay) TextView thinking_overlay;
  @BindView(R.id.progressBar) ProgressBar progressBar; //!进度条。
  int ret = 0;
  private static final String TAG="SisterFutureActivity"; //!输出�