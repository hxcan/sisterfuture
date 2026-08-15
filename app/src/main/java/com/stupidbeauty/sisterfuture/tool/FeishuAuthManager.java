package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 飞书 API 认证管理器
 *
 * 功能：
 * 1. 获取 tenant_access_token（自动缓存，避免重复请求）
 * 2. token 即将过期时自动刷新
 * 3. 从工具备注（getNote）读取 app_id / app_secret
 *
 * 凭证格式（写入 setNote 的 JSON）：
 * {
 *   "feishu_app_id": "cli_xxx",
 *   "feishu_app_secret": "xxx"
 * }
 */
public class FeishuAuthManager
{
  private static final String TAG = "FeishuAuthManager";

  // token 缓存
  private static volatile String cachedToken = null;
  private static volatile long tokenExpireTime = 0; // 单位：毫秒

  private final Context context;
  private final OkHttpClient httpClient = new OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build();

  public FeishuAuthManager(Context context)
  {
    this.context = context;
  }

  /**
   * 获取飞书 tenant_access_token，自动管理缓存
   */
  public synchronized String getTenantAccessToken() throws IOException, JSONException
  {
    long now = System.currentTimeMillis();

    // 缓存有效（提前5分钟刷新）
    if (cachedToken != null && now < tokenExpireTime - 5 * 60 * 1000L)
    {
      FileLogger.d(TAG, "使用缓存的 tenant_access_token");
      return cachedToken;
    }

    // 读取凭证
    String[] credentials = loadCredentials();
    if (credentials == null)
    {
      throw new IllegalArgumentException(
        "Missing feishu_app_id or feishu_app_secret in tool note. " +
        "请先调用 setToolRemark 配置飞书应用凭证。"
      );
    }

    String appId = credentials[0];
    String appSecret = credentials[1];

    FileLogger.d(TAG, "请求新的 tenant_access_token，app_id=" + appId);

    // 调用飞书 API 获取 token
    JSONObject body = new JSONObject();
    body.put("app_id", appId);
    body.put("app_secret", appSecret);

    RequestBody requestBody = RequestBody.create(
      body.toString(),
      MediaType.get("application/json; charset=utf-8")
    );

    Request request = new Request.Builder()
      .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
      .post(requestBody)
      .build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful())
    {
      String errorBody = response.body() != null ? response.body().string() : "";
      throw new IOException(
        "获取 tenant_access_token 失败：HTTP " + response.code() +
        " " + response.message() + "\n" + errorBody
      );
    }

    JSONObject result = new JSONObject(response.body().string());

    if (result.getInt("code") != 0)
    {
      throw new IOException(
        "飞书 API 返回错误：code=" + result.getInt("code") +
        ", msg=" + result.getString("msg")
      );
    }

    cachedToken = result.getString("tenant_access_token");
    int expireSeconds = result.getInt("expire");
    tokenExpireTime = now + expireSeconds * 1000L;

    FileLogger.d(TAG, "token 获取成功，过期时间=" + expireSeconds + " 秒");

    return cachedToken;
  }

  /**
   * 从工具备注读取飞书凭证
   * @return [app_id, app_secret]，未找到返回 null
   */
  private String[] loadCredentials()
  {
    try
    {
      // 尝试从一个标准工具名读取（用于多工具共享）
      String noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
        .getString("note_feishu_shared", "");

      if (noteJson.isEmpty())
      {
        // 回退方案：从 addFeishuBitableRecord 工具备注读取
        noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
          .getString("note_addFeishuBitableRecord", "");
      }

      if (noteJson.isEmpty())
      {
        return null;
      }

      JSONObject saved = new JSONObject(noteJson);
      String appId = saved.optString("feishu_app_id", "").trim();
      String appSecret = saved.optString("feishu_app_secret", "").trim();

      if (appId.isEmpty() || appSecret.isEmpty())
      {
        return null;
      }

      return new String[]{appId, appSecret};
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "读取飞书凭证失败", e);
      return null;
    }
  }

  /**
   * 清除 token 缓存（用于 token 过期后强制刷新）
   */
  public synchronized void invalidateToken()
  {
    cachedToken = null;
    tokenExpireTime = 0;
    FileLogger.d(TAG, "token 缓存已清除");
  }
}