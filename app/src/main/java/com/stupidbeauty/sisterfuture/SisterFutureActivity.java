package com.stupidbeauty.sisterfuture;

import android.app.StatusBarManager;
import com.stupidbeauty.hxlauncher.callback.AddQuickSettingsResultCallback;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.drawable.Icon;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.drawable.Icon;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.content.ComponentName;
import android.content.Context;
import com.stupidbeauty.codeposition.CodePosition;
import android.os.ParcelFileDescriptor;
import com.stupidbeauty.dynamicwallpaper.service.MyLiveWallpaperService.MyEngine;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import com.stupidbeauty.dynamicwallpaper.service.MyLiveWallpaperService;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import com.bumptech.glide.Glide;
import java.util.Random;
import android.widget.ImageView;
import java.util.Random;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import butterknife.OnClick;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import com.stupidbeauty.sisterfuture.bean.MessageItem;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import android.provider.Settings;
import android.content.Intent;
import android.os.Environment;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import com.stupidbeauty.sisterfuture.R; // Make sure to import the correct R class
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import java.util.List;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import net.tatans.tensorflowtts.utils.ThreadPoolManager;
// import androidx.localbroadcastmanager.content.LocalBroadcastManager;
// import net.tatans.tensorflowtts.tts.TtsManager;
import org.json.JSONObject;
import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
// import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.os.PowerManager;
// import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import android.Manifest;
import android.annotation.SuppressLint;
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
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
// import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
// import android.widget.Switch;
import android.widget.Switch;
// import com.android.volley.Request;
// import com.android.volley.RequestQueue;
// import com.android.volley.Response;
// import com.android.volley.VolleyError;
// import com.google.gson.Gson;
import com.stupidbeauty.msclearnfootball.VoiceRecognizeResult;
// import com.iflytek.cloud.ErrorCode;
// import com.iflytek.cloud.RecognizerListener;
// import com.iflytek.cloud.RecognizerResult;
// import com.iflytek.cloud.SpeechConstant;
// import com.iflytek.cloud.SpeechError;
// import com.iflytek.cloud.SpeechRecognizer;
// import com.iflytek.cloud.SpeechUtility;
// import com.stupidbeauty.sisterfuture.network.TongYiClient;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
// import butterknife.Bind;
import butterknife.ButterKnife;
// import tv.xiaoqiu.paperred.network.VolleyManager;
// import com.stupidbeauty.sisterfuture.network.TongYiClient.OnResponseListener;
// import com.koushikdutta.async.http.server.AsyncHttpServer;
// import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
// import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
// import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.stupidbeauty.lanime.network.volley.MapUtils;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.ugmate.common.LogHelper;
// import com.stupidbeauty.ugmate.network.volley.GsonRequest;
import com.stupidbeauty.x2app.BossResponse;
import com.stupidbeauty.lanime.Constants;
// import com.stupidbeauty.lanime.callback.CommitTextCallback;
// import com.stupidbeauty.lanime.callback.PhoneInformationCallback;
// import com.stupidbeauty.sisterfuture.adapter.MessageAdapter;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 */
public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener
{
@BindView(R.id.add_quick_settings_button)
Button addQuickSettingsButton;

// private MessageAdapter messageAdapter;
  // RecyclerView articleListmyRecyclerView;
@BindView(R.id.articleListmy_recycler_view) RecyclerView articleListmyRecyclerView; //!< Message list.

@BindView(R.id.set_wallpaper_button)
Button setWallpaperButton;

@BindView(R.id.refresh_wallpaper_button)
Button refreshWallpaperButton;

// 🔴 钉住壁纸开关（新增）
@BindView(R.id.pin_wallpaper_switch)
Switch pinWallpaperSwitch;

private static final String PIN_PREF_NAME = "dynamic_wallpaper"; //!< 与壁纸服务共享的 SharedPreferences
private static final String PIN_PREF_KEY = "wallpaper_pinned"; //!< 钉住状态的 key


    
    private static final String DEFAULT_INPUT_TEXT = "君不见,黄河之水天上来,奔流到海不复回,君不见,高堂明镜悲白发,朝如青丝暮成雪,人生得意须尽欢,莫使金樽空对月";
// 在Activity中添加一个StringBuilder来存储累积的回答文本
private StringBuilder accumulatedAnswer = new StringBuilder();

  private static final int PERMISSIONS_REQUEST = 1; //!<权限请求标识
    // 假设这是您的通义千问客户端
    // private TongYiClient tongYiClient;
// 在Activity中添加一个变量用于追踪是否正在合成语音
private boolean isTtsSpeaking = false;

  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  private static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO; //!<录音权限。
  private static final String PERMISSION_FINE_LOCATIN = Manifest.permission.ACCESS_FINE_LOCATION; //!<位置权限
  private static final String PERMISSION_INSTALL_PACKAGE = Manifest.permission.REQUEST_INSTALL_PACKAGES; //!< 安装应用程序权限
    private MediaPlayer mediaPlayer;
    private boolean voiceEndDetected=false; //!<是否已经探测到用户声音结束。
    private String textTitle;

    private TextToSpeech mTts;

    private static final int LanServicePort = 10471;

    private String voiceRecognizeResultString; //!<语音识别结果。
    // private RequestQueue mQueue; //!<Volley请求队列。

    private Vibrator vibrator;
    
    @BindView(R.id.commandRecognizebutton2) Button commandRecognizebutton2; //!<开始识别的按钮。
    
    @BindView(R.id.progressBar) ProgressBar progressBar; //!<进度条。
    int ret = 0;
    private static final String TAG="SisterFutureActivity"; //!<输出调试信息时使用的标记。
    @BindView(R.id.speakerVerifyRegisterPasswordtextView) TextView speakerVerifyRegisterPasswordtextView; //!<声纹注册密码文本标签。

	// private SpeechRecognizer mIat; //!<语言识别器。



	@BindView(R.id.statustextView)
	TextView statustextView; //!<用来显示状态的文字标签。

	@BindView(R.id.volumeIndicatorprogressBar)
	ProgressBar volumeIndicatorprogressBar; //!<用来显示音量的进度条。

	@BindView(R.id.recognizeResulttextView) EditText recognizeResulttextView; //!<识别结果。

@OnClick(R.id.add_quick_settings_button)
public void onAddQuickSettingsClicked() {
    StatusBarManager statusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);

    if (statusBarManager == null) {
        Toast.makeText(this, "不支持快捷方式", Toast.LENGTH_SHORT).show();
        return;
    }

    ExecutorService executor = Executors.newFixedThreadPool(1);

    Icon icon = Icon.createWithResource(this, R.drawable.ic_refresh);
    CharSequence label = getText(R.string.wallpaper_refresh_tile);

    ComponentName componentName = new ComponentName(
        getPackageName(),
        "com.stupidbeauty.dynamicwallpaper.service.WallpaperRefreshTileService"
    );

    AddQuickSettingsResultCallback callback = new AddQuickSettingsResultCallback();

    statusBarManager.requestAddTileService(componentName, label, icon, executor, callback);
}

	// 在 setWallpaperButton 之后添加点击事件
@OnClick(R.id.set_wallpaper_button)
public void onSetWallpaperClicked() {
    Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
    intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(
        getPackageName(),
        MyLiveWallpaperService.class.getName()
    ));
    startActivityForResult(intent, 1001);
}

    @Override
    public void onInit(int arg0) {
        // TODO 自动生成的方法存根

    }

@OnClick(R.id.refresh_wallpaper_button)
public void onRefreshWallpaperClicked() {
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "=== onRefreshWallpaperClicked START ===");
    // 1. 通知壁纸服务 Engine 重新加载图片
    SisterFutureApplication app = (SisterFutureApplication) getApplication();
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "app=" + app);
    MyEngine engine = app.getMyEngine();
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "engine=" + engine);
    if (engine != null) {
            com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "engine != null, calling reloadWallpaper(false)");
        engine.reloadWallpaper(false);
    }

    // 2. 预览也显示与壁纸服务一致的图片（从 SharedPreferences 读取）
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "calling loadCurrentWallpaperPreview");
    loadCurrentWallpaperPreview();
}


    /**
     * 停止录音。
     */
    public void stopRecordbutton2()
    {
        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate( 100);

        volumeIndicatorprogressBar.setIndeterminate(true); //处于未决状态，以表示正在识别。
        volumeIndicatorprogressBar.setProgress(0); //进度归零。

        volumeIndicatorprogressBar.setVisibility(View.INVISIBLE); //停止录音，则不再显示音量。

        progressBar.setVisibility(View.VISIBLE); //显示进度条。

        commandRecognizebutton2.setEnabled(false); //禁用按钮。
        commandRecognizebutton2.setVisibility(View.INVISIBLE); //隐藏按钮。
    } //public void stopRecordbutton2()

	/**
	 * 在线命令词识别。
	 */
  public void commandRecognizebutton2()
	{
	    voiceEndDetected=false; //重置状态，未探测到用户的声音结束。

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate( 100);

        if (!setParam()) //参数设置失败。
        {
            statustextView.setText("请先构建语法。");

            return;
        } //if (!setParam()) //参数设置失败。

        volumeIndicatorprogressBar.setIndeterminate(false); //处于决定状态，以表示音量值。
        progressBar.setVisibility(View.INVISIBLE); //隐藏显示进度条。
        recognizeResulttextView.setText(R.string.empty); //显示空白内容。
	} //public void commandRecognizebutton2()

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 1001) {
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "已成功设置为动态壁纸", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show();
        }
    }
}

    /**
     * 参数设置
     *
     * @return 是否设置成功。
     */
    public boolean setParam()
    {
        LogHelper.d(TAG, "setParam.190"); //Debug.
        boolean result = false;
        // 设置识别引擎

        return result;
    }

    /**
     * 计算签名字符串。
     * @param map 参数映射。
     * @return 计算出来的签名字符串。
     */
    private String calculateSign(Map<String, String> map)
    {
        String rawUrl=""; //#原始网址内容。

        String url;

        try
        {

            rawUrl="app_id="+map.get("app_id");
        rawUrl=rawUrl+"&"+"nonce_str="+map.get("nonce_str");

        String question=map.get("question");

        String questionEncoded=URLEncoder.encode(question, "UTF-8");

        rawUrl=rawUrl+"&"+"question="+  questionEncoded;
        rawUrl=rawUrl+"&"+"session="+map.get("session");
        rawUrl=rawUrl+"&"+"time_stamp="+map.get("time_stamp");
        rawUrl=rawUrl+"&"+"app_key="+map.get("app_key");



        LogHelper.d(TAG,rawUrl); //#Debug.


//            url= URLEncoder.encode(rawUrl, "UTF-8"); //#编码。

        }
        catch (UnsupportedEncodingException e)
        {

        } //catch (UnsupportedEncodingException e)

        url=rawUrl; //记录网址。


        LogHelper.d(TAG,url); //#Debug.

        MessageDigest md=null;

        try
        {
            md=MessageDigest.getInstance("MD5");

        }
        catch (NoSuchAlgorithmException e)
        {

        }

        md.update(url.getBytes());
        byte byteData[]=md.digest();

        StringBuilder hexString= new StringBuilder();
        for (byte aByteData : byteData) {
            String hex = Integer.toHexString(0xff & aByteData);

            if (hex.length() == 1) {
                hexString.append('0');
//                hexString.append(hex);
            }

            hexString.append(hex);
        }

        String urlHexDigest=hexString.toString();

        String sign=urlHexDigest.toUpperCase();

        LogHelper.d(TAG,"sign: "+sign); //#Debug.

            return sign;


    } //private String calculateSign(Map<String, String> map)
    
    /**
    * Send chat request to qq chat service.
    */
    private void sendChatRequestQqChat()
    {
      Map<String,String> map = new HashMap<>();

      String serialNumber="welcome_logon.getText().toString()"; //获取验证码。
      map.put("validCode", serialNumber); //添加参数，验证码。

      LogHelper.d(TAG,"sendValidationCode,序列号："+serialNumber); //Debug.

      String userFullName="passwordEditText1.getText().toString()"; //获取密码。
      map.put("password", userFullName); //添加参数，用户名字。

      String app_id="2107629525";

      long time_stamp= System.currentTimeMillis()/1000; //时间戳整数。秒。
      String timeStampString=String.valueOf(time_stamp); //时间戳字符串。
      String nonce_str=String.valueOf(time_stamp); //#随机字符串。
      String session=nonce_str; //#会话编号。
      String question=voiceRecognizeResultString; //#问题。
      //        question="妳都是什么时候接客啊？"; //#问题。
      String app_key="EjjAfr2pidPcaIQ2"; //#应用密钥。

      map.put("app_id", app_id);
      map.put("nonce_str", nonce_str);
      map.put("question", question);
      map.put("session", session);
      map.put("time_stamp", timeStampString);
      map.put("app_key", app_key);

      String sign= calculateSign(map);
      map.put("sign", sign);
    } // private void sendChatRequestQqChat()
    
    /**
    * Send by button.
    */
    @OnClick(R.id.sendButtonn2)
    public void sendButtonn2()
    {
      voiceRecognizeResultString = recognizeResulttextView.getText().toString(); // Get the content.
                    // 创建新的用户消息条目
        // messageAdapter.addMessage(new MessageItem(voiceRecognizeResultString, false));

      sendChatRequest(); // Send the request.
    } // public void sendButtonn2()

    /**
     * 发送闲聊请求。
     */
    private void sendChatRequest()
    {
      recognizeResulttextView.setText(""); // Clear the recognize result or input content.

      // sendChatRequestQqChat();

      sendChatRequestTongYi(); // Send chat reqeuswt to tong yi.
    } //private void sendChatRequest()

    /**
     * Report that the operation has failed.
     * @param string 服务器回复的结果说明文字。
     */
    protected void reportOperationFail(String string)
    {
      Toast.makeText(SisterFutureApplication.getAppContext(), string, Toast.LENGTH_LONG).show();   //做一个提示，Failed adding address ,please retry.
    } //protected void reportOperationFail()

    /**
     * 向通义千问发送请求并处理回复。
     */
    private void sendChatRequestTongYi() 
    {
        if (voiceRecognizeResultString != null && !voiceRecognizeResultString.isEmpty()) 
        {
        accumulatedAnswer.setLength(0); // clear the last incremental result.
        }
        else 
        {
            Log.w(TAG, "Voice recognition result is empty or null.");
        }
    }
    
    /**
    * 解析提交问题的结果。
    * @param jsonString JSON格式的回答内容。
    */
    protected void parseTongYiResponse(String jsonString) 
    {
      LogHelper.d(TAG, "JSON Answer: " + jsonString); // Debug.

      try 
      {
        // 尝试解析 JSON 字符串
        JSONObject jsonResponse = new JSONObject(jsonString);
        JSONObject output = jsonResponse.getJSONObject("output");
        JSONArray choices = output.getJSONArray("choices");
        
        for (int i = 0; i < choices.length(); i++) 
        {
            JSONObject choice = choices.getJSONObject(i);
            JSONObject message = choice.getJSONObject("message");
            String answerIncrement = message.getString("content");

            // 检查是否需要创建新的消息条目
            boolean isNewMessage = accumulatedAnswer.length() == 0;

            // 更新StringBuilder以累积答案
            accumulatedAnswer.append(answerIncrement);


            if (isNewMessage) {
                // 创建新的AI消息条目
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                // messageAdapter.addMessage(new MessageItem(accumulatedAnswer.toString(), true));
                }
            });
            } else {
                // 更新现有AI消息的内容
                // int lastPosition = messageAdapter.getItemCount() - 1;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                // messageAdapter.updateAiMessage(lastPosition, accumulatedAnswer.toString());
                
                scrollToBottom();
                }
            });
            }
        }

        // 检查是否完成接收
        if (output.getJSONArray("choices").getJSONObject(0).getString("finish_reason").equals("stop")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String fullAnswer = accumulatedAnswer.toString();
                    
                    // 语音合成完整的答案
                    ttsSayReply(fullAnswer);
                }
            });
        }

    } catch (Exception e) {
        // 如果解析 JSON 出现错误，则打印异常
        LogHelper.e(TAG, "Error parsing JSON response: " + e.getMessage());
    }
}


private void scrollToBottom() {
    // 移动到列表的最后一个可见项
    // articleListmyRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
}

    /**
     * 解析提交密码信息的结果。
     * @param weatherInfo 结果对象。
     */
    protected void parseSubmitPasswordResponse(BossResponse weatherInfo)
    {
      String answer=weatherInfo.getData().getAnswer();
      LogHelper.d(TAG,"Answer: "+answer); //Debug.

      statustextView.setText(answer); //显示结果。

      ttsSayReply(answer); //语音合成回复结果。
    } //protected void parseSubmitPasswordResponse(PhoneRegisterResponse weatherInfo)

    @Override
    public void onBackPressed()
    {
        if (null!=mTts) //TTS引擎还在。
        {
            mTts.shutdown(); //关闭。
        } //if (null!=mTts) //TTS引擎还在。

        super.onBackPressed();
    } //public void onBackPressed()

    /**
     * 使用系统自带的TTS接口。
     * @param answer 要发声的内容。
     */
    private void ttsByAndroidSystemTts(String answer)
    {
        try
        {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, textTitle);
            mTts.speak(answer, TextToSpeech.QUEUE_FLUSH, params);

        } //try
        catch(Exception e)
        {
            e.printStackTrace(); //Debug.
        } //catch(Exception e)


    } //private void ttsByAndroidSystemTts(String answer)

    /*!
     * \brief SearchEngineManager::constructGoogleSearchUrl 构造谷歌的搜索网址。
     * \param searchKeyWord 搜索关键字。
     * \return 针对谷歌的搜索网址。
     */
    private String constructBiaobeiTtsUrl(String searchKeyWord)
    {
        String biaobeiServiceUrl="http://39.104.162.93:8005/tts"; //!<XiaoINuance服务地址。
        int speed=5; //!<语音合成的语速。


        String searchEnginePrefix=biaobeiServiceUrl; //!<AI引擎的网址前缀。

        String searchUrlString=searchEnginePrefix; //!<构造搜索路径字符串。
        String searchUrl=searchUrlString; //!<构造URL。

        Map<String, String> srchQry=new HashMap<>(); //!<查询对象。
        String srchKyWrdPcntEcd=(searchKeyWord); //!<转换成百分号编码。
        srchQry.put("text", srchKyWrdPcntEcd); //!<设置查询条件。
        srchQry.put("user_id", "speech");
        srchQry.put("domain", "1");
        srchQry.put("language", "zh");
        srchQry.put("rate", "4"); //!<设置会话编号。
        srchQry.put("volume", "5"); //!<设置会话编号。
        srchQry.put("speed", String.valueOf(speed)); //!<设置语速。

        searchUrl=searchUrl+"?"+ MapUtils.toUrlGetString(srchQry);

        return searchUrl;
    } //QUrl SearchEngineManager::constructGoogleSearchUrl(QString searchKeyWord)


    /**
     * 使用标贝语音来发声。
     * @param answer 要发声的内容。
     */
    private void ttsByBiaoBei(String answer)
    {
        try
        {
            String AudioURL=constructBiaobeiTtsUrl(answer); //构造整个网址。
            LogHelper.d(TAG, "ttsByBiaoBei, audio url: "+ AudioURL); //Debug.

            mediaPlayer.reset(); //重置。

            mediaPlayer.setDataSource(AudioURL);
            mediaPlayer.prepare();

        }
        catch (IllegalArgumentException e)
        {

        }
        catch (SecurityException e)
        {

        }
        catch (IllegalStateException e)
        {

        }
        catch (IOException e)
        {

        }

        mediaPlayer.start();
    } //private void ttsByBiaoBei(String answer)
    
    // 修改ttsSayReply方法
private void ttsSayReply(final String text) 
{
    // 直接开始语音合成
    // tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
       // ttsByAndroidSystemTts(text); //使用系统自带的TTS接口。
            // ttsByBiaoBei(text); //使用标贝语音来发声。
       ttsByFindroidTts(text); // 使用 findroid 介绍的 TTS接口。
}

/**
*  使用 findroid 介绍的 TTS接口。
* https://github.com/tatans-coder/TensorflowTTS_chinese/blob/master/app/src/main/java/net/tatans/tensorflowtts/MainActivity.java
*/
private void        ttsByFindroidTts(String text)
{
          ThreadPoolManager.getInstance().execute(() -> {
                    float speed = 1.0F;

                    String inputText = text;
                    if (TextUtils.isEmpty(inputText)) {
                        inputText = DEFAULT_INPUT_TEXT;
                    }
                    // TtsManager.getInstance().speak(inputText, speed, true);
                });

} // private void        ttsByFindroidTts(String text)

    private final View.OnTouchListener commandRecognizeButtonTouchListener=new View.OnTouchListener()
    {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            switch (event.getAction()) //根据不同事件进行处理。
                {
                case MotionEvent.ACTION_DOWN: //按下。
                    commandRecognizebutton2(); //开始识别。

                    break; //跳出。

                case MotionEvent.ACTION_UP: //松开。
                    stopRecordbutton2(); //停止识别。

                    break; //跳出。
            } //switch (event.getAction()) //根据不同事件进行处理。

            return true;
        } //public boolean onTouch(View v, MotionEvent event)
    };

    /**
     * 连接信号信号槽。
     */
    private void connectSignals()
    {
        commandRecognizebutton2.setOnTouchListener(commandRecognizeButtonTouchListener); //设置触摸事件监听器。
    } //private void connectSignals()

// 在类内部添加：图片加载工具方法
private void loadRandomWallpaper() {
    adjustPreviewImageAspectRatio();
    // 获取图片 URI 列表
    List<Uri> imageUris = queryImages(this);

    if (imageUris.isEmpty()) {
        Log.w("DynamicWallpaper", "No images found in media store");
        return;
    }

    // 随机选择一张
    Uri randomUri = imageUris.get(new Random().nextInt(imageUris.size()));

    // 获取 ImageView
    ImageView imageView = findViewById(R.id.preview_image);

    // 使用 Glide 加载图片
    Glide.with(this)
        .load(randomUri)
        .centerCrop()
        .placeholder(R.color.gray)
        .error(R.drawable.placeholder_image)
        .into(imageView);
}

// 查询系统图片（支持 JPEG/PNG/WebP）
private List<Uri> queryImages(Context context) {
    List<Uri> imageUris = new ArrayList<>();

    String[] projection = {
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_ADDED
    };

    String selection = MediaStore.Images.Media.MIME_TYPE + " = ? OR " +
                       MediaStore.Images.Media.MIME_TYPE + " = ? OR " +
                       MediaStore.Images.Media.MIME_TYPE + " = ?";

    String[] selectionArgs = {"image/jpeg", "image/png", "image/webp"};

    String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

    try (Cursor cursor = context.getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder)) {

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                imageUris.add(uri);
            } while (cursor.moveToNext());
        }
    } catch (Exception e) {
        Log.e("DynamicWallpaper", "Error querying images: " + e.getMessage(), e);
    }

    Log.i("DynamicWallpaper", "Found " + imageUris.size() + " images");
    return imageUris;
}


// 调整预览图片尺寸与屏幕比例一致
private void adjustPreviewImageAspectRatio() {
    ImageView imageView = findViewById(R.id.preview_image);
    if (imageView == null) {
        return;
    }
    // 获取屏幕真实尺寸（包括状态栏和导航栏）
    android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
    getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
    int screenWidth = displayMetrics.widthPixels;
    int screenHeight = displayMetrics.heightPixels;
    // 计算屏幕宽高比
    float aspectRatio = (float) screenWidth / (float) screenHeight;
    // 设置 ImageView 的宽高与屏幕比例一致
    android.view.ViewGroup.LayoutParams params = imageView.getLayoutParams();
    params.width = screenWidth;
    params.height = (int) (screenWidth / aspectRatio);
    imageView.setLayoutParams(params);
    // 同时调整 scaleType 使图片按比例显示
    imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
}


// 读取当前壁纸 URI 并显示在预览上（与壁纸服务保持一致）
private void loadCurrentWallpaperPreview() {
    ImageView imageView = findViewById(R.id.preview_image);
    if (imageView == null) {
        return;
    }
    // 1. 优先读取 SharedPreferences 中壁纸服务保存的当前 URI
    android.content.SharedPreferences prefs = getSharedPreferences("dynamic_wallpaper", MODE_PRIVATE);
    String savedUri = prefs.getString("current_wallpaper_uri", null);
    com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("RefreshBtn", "=== loadCurrentWallpaperPreview START, savedUri=" + savedUri);
    if (savedUri != null) {
        // 应用已被设为壁纸服务，显示壁纸服务当前显示的图片
        Uri currentUri = Uri.parse(savedUri);
        com.bumptech.glide.Glide.with(this)
            .load(currentUri)
            .centerCrop()
            .placeholder(R.color.gray)
            .error(R.drawable.placeholder_image)
            .into(imageView);
    } else {
        // 2. 应用未被设为壁纸服务，显示系统当前壁纸
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            android.graphics.drawable.Drawable wallpaperDrawable = wallpaperManager.getDrawable();
            if (wallpaperDrawable != null) {
                imageView.setImageDrawable(wallpaperDrawable);
            }
        } catch (Exception e) {
            Log.e("DynamicWallpaper", "Failed to get system wallpaper: " + e.getMessage(), e);
        }
    }
}
	/*
	  此活动正在被创建。
	 */
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState); //超类创建。

		requestWindowFeature(Window.FEATURE_NO_TITLE); //不显示标题栏。
		
		setContentView(R.layout.sister_future); //显示界面。

        // 延迟再次加载预览，确保壁纸服务完成初始化（只读，不写入）
        new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                loadCurrentWallpaperPreview();
            }
        }, 500);

        // 显示与壁纸服务一致的当前壁纸
        loadCurrentWallpaperPreview();

        // 调整预览图片尺寸与屏幕比例一致
        adjustPreviewImageAspectRatio();

	        // TtsManager.getInstance().init(this);

    mTts=new TextToSpeech(this,this); //创建TTS对象。

    LogHelper.initLocalLogUtil();// after set the context to utils then //

    registerBroadcastReceiver(); //注册广播事件接收器。

    // startHttpServer(); //启动HTTP服务器

    // mQueue= VolleyManager.shareInstance().getRequestQueue(); //Get the request queue.

    mediaPlayer=new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

    ButterKnife.bind(this); //视图注入。

        // 初始化通义千问客户端
        // tongYiClient = new TongYiClient();

        checkPermission(); //检查权限。


		connectSignals(); //连接信号信号槽。

        // 🔴 初始化"钉住壁纸"开关（新增）
        initPinWallpaperSwitch();
		
		    // messageAdapter = new MessageAdapter();
    articleListmyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    // articleListmyRecyclerView.setAdapter(messageAdapter);

            recognizeResulttextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                voiceRecognizeResultString = recognizeResulttextView.getText().toString(); // Get the input text.
                    sendChatRequest();
                    return true; // 消耗事件
                }
                return false;
            }
        });

	} //protected void onCreate(Bundle savedInstanceState)

    // 🔴 初始化"钉住壁纸"开关（新增）
    private void initPinWallpaperSwitch() {
        // 1. 从 SharedPreferences 读取当前状态
        android.content.SharedPreferences prefs = getSharedPreferences(PIN_PREF_NAME, MODE_PRIVATE);
        boolean isPinned = prefs.getBoolean(PIN_PREF_KEY, false);
        pinWallpaperSwitch.setChecked(isPinned);
        com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("PinSwitch", "initPinWallpaperSwitch: isPinned=" + isPinned);

        // 2. 监听变化，保存到 SharedPreferences
        pinWallpaperSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                android.content.SharedPreferences.Editor editor = getSharedPreferences(PIN_PREF_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(PIN_PREF_KEY, isChecked);
                editor.apply();
                com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("PinSwitch", "onCheckedChanged: isChecked=" + isChecked);

                // 3. 给用户清晰的反馈
                if (isChecked) {
                    Toast.makeText(SisterFutureActivity.this, "📌 已钉住当前壁纸，将不会自动换图", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SisterFutureActivity.this, "已取消钉住，31分钟后会自动换图", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🔴 每次回到界面，刷新开关状态（保证 Service 修改后能看到）
        if (pinWallpaperSwitch != null) {
            android.content.SharedPreferences prefs = getSharedPreferences(PIN_PREF_NAME, MODE_PRIVATE);
            boolean isPinned = prefs.getBoolean(PIN_PREF_KEY, false);
            // 避免触发 OnCheckedChangeListener
            pinWallpaperSwitch.setOnCheckedChangeListener(null);
            pinWallpaperSwitch.setChecked(isPinned);
            // 重新绑定监听器
            pinWallpaperSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    android.content.SharedPreferences.Editor editor = getSharedPreferences(PIN_PREF_NAME, MODE_PRIVATE).edit();
                    editor.putBoolean(PIN_PREF_KEY, isChecked);
                    editor.apply();
                    com.stupidbeauty.dynamicwallpaper.utils.FileLogger.i("PinSwitch", "onCheckedChanged (rebound): isChecked=" + isChecked);
                    if (isChecked) {
                        Toast.makeText(SisterFutureActivity.this, "📌 已钉住当前壁纸，将不会自动换图", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SisterFutureActivity.this, "已取消钉住，31分钟后会自动换图", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private boolean hasPermission()
    {
      boolean result=false; //结果。

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //安卓6.
      {
        ArrayList<String> articleInfoArrayList = new ArrayList<>(); // 权限列表。
        
        articleInfoArrayList.add(PERMISSION_STORAGE);
        articleInfoArrayList.add(PERMISSION_RECORD_AUDIO);
        articleInfoArrayList.add(PERMISSION_FINE_LOCATIN);
        // articleInfoArrayList.add(PERMISSION_INSTALL_PACKAGE); // 安装应用程序的权限。
        
        for(String permissionString: articleInfoArrayList) // 一个个检查
        {
          // Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString); // Debug.
          result=(checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED); //录音权限。
          
          if (!result) // 没有权限
          {
            // Log.d(TAG, CodePosition.newInstance().toString() + ", permission: " + permissionString + ", no permission"); // Debug.
            break; // 没有权限。
          } // if (!result) // 没有权限
        } // for(String permissionString: articleInfoArrayList) // 一个个检查
      } //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //安卓6.
      else //旧版本。
      {
        result=true; //有权限。
      } //else //旧版本。

      return result;
    } //private boolean hasPermission()

    /**
     * 请求获取权限
     */
    private void requestPermission()
    {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //动态权限
      {
        if ( shouldShowRequestPermissionRationale(PERMISSION_STORAGE)  || shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO) || shouldShowRequestPermissionRationale(PERMISSION_FINE_LOCATIN)  || shouldShowRequestPermissionRationale(PERMISSION_INSTALL_PACKAGE)) //应当告知原因。
        {
          Toast.makeText(this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
        } //if ( shouldShowRequestPermissionRationale(PERMISSION_STORAGE)  || shouldShowRequestPermissionRationale(PERMISSION_RECORD_AUDIO)) //应当告知原因。
        // Log.d(TAG, CodePosition.newInstance().toString() ); // Debug.

        // requestPermissions(new String[] {PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO, PERMISSION_FINE_LOCATIN, PERMISSION_INSTALL_PACKAGE}, PERMISSIONS_REQUEST);
        requestPermissions(new String[] {PERMISSION_STORAGE, PERMISSION_RECORD_AUDIO, PERMISSION_FINE_LOCATIN}, PERMISSIONS_REQUEST);

        } //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //动态权限
    } //private void requestPermission()


    
    /**
     * 检查权限。
     */
    private void checkPermission()
    {
      if (hasPermission()) 
      {
      }
      else 
      {
        requestPermission();
      }
    } //private void checkPermission()


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allGranted = true;
        for (int grantResult : grantResults) {
            if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            loadRandomWallpaper(); // 权限授予后自动加载壁纸到预览
        }
    }

    /**
     * 注册广播事件接收器。
     */
    private void registerBroadcastReceiver()
    {
        IntentFilter filter = new IntentFilter();

        filter.addAction(Constants.Operation.CommitText); //提交文本内容。
        filter.addAction(Constants.Operation.CommitControlCharacter); //提交控制字符。
        filter.addAction(Constants.NativeMessage.NOTIFY_CALLBACK_IP); //报告回调IP。
        filter.addAction(Constants.Operation.HideKeyboard);
        filter.addAction("com.stupidbeauty.dynamicwallpaper.WALLPAPER_CHANGED"); //隐藏软键盘。

        // LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(this); //Get the local broadcast manager instance.
        registerReceiver(mBroadcastReceiver, filter); //注册接收器。


    } //private void registerBroadcastReceiver()

    /**
     * 广播接收器。
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        /*
          接收到广播。
         */
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction(); //获取广播中带的动作字符串。

            if ("com.stupidbeauty.dynamicwallpaper.WALLPAPER_CHANGED".equals(action)) {
                loadCurrentWallpaperPreview();
            } else if (Constants.Operation.CommitText.equals(action)) {
                Bundle extras=intent.getExtras(); //获取参数包。

                voiceRecognizeResultString= extras.getString("text"); //记录识别结果。


                recognizeResulttextView.setText(voiceRecognizeResultString); //显示结果。

                sendChatRequest(); //发送闲聊请求。


                startFriendShutDownAt2100Service(); //启动友军"21点关机"的服务。
            }
        } //public void onReceive(Context context, Intent intent)
    }; //private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()

    /**
     * 启动友军"21点关机"的服务。
     */
    protected void startFriendShutDownAt2100Service()
    {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.stupidbeauty.shutdownat2100androidnative", "com.stupidbeauty.shutdownat2100androidnative.TimeCheckService")); //设置组件。
        startService(intent); //启动服务。

    } //protected void startFriendShutDownAt2100Service()
}