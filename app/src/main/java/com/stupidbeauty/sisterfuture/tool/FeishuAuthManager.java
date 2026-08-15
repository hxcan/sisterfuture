package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 飞书 API 认证管理器
 *
 * 重要原则：所有凭证（app_id / app_secret）必须在调用时显式传入，
 * 不能依赖代码中硬编码的固定值。
 *
 * 功能：
 * 1. 获取 tenant_access_token（自动缓存，避免重复请求）
 * 2. token 即将过期时自动刷新
 * 3. 凭证由调用方传入（不要硬编码！）
 *
 * 调用方传入凭证的两种方式：
 * - 直接传值：getTenantAccessToken(appId, appSecret)
 * - 从工具备注读取：getTenantAccessToken(noteKey)
 */
public class FeishuAuthManager
{
  private static final String TAG = "FeishuAuthManager";

  // token 缓存（按 appId 区分）
  private static volatile String cachedToken = null;
  private static volatile String cachedAppId = null;
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
   * 获取 token（凭证由调用方直接传入）
   *
   * @param appId 飞书应用 App ID（必填）
   * @param appSecret 飞书应用 App Secret（必填）
   * @return tenant_access_token
   */
  public synchronized String getTenantAccessToken(@NonNull String appId, @NonNull String appSecret)
    throws IOException, JSONException
  {
    if (appId.isEmpty() || appSecret.isEmpty())
    {
      throw new IllegalArgumentException(
        "app_id 和 app_secret 不能为空。请通过 setToolRemark 配置或调用时传入。"
      );
    }

    long now = System.currentTimeMillis();

    // 缓存有效（提前5分钟刷新）
    if (cachedToken != null && appId.equals(cachedAppId) &&
        now < tokenExpireTime - 5 * 60 * 1000L)
    {
      FileLogger.d(TAG, "使用缓存的 tenant_access_token");
      return cachedToken;
    }

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
    cachedAppId = appId;
    int expireSeconds = result.getInt("expire");
    tokenExpireTime = now + expireSeconds * 1000L;

    FileLogger.d(TAG, "token 获取成功，过期时间=" + expireSeconds + " 秒");

    return cachedToken;
  }

  /**
   * 从工具备注读取凭证后获取 token
   *
   * @param noteKey 工具备注的 key（例如 "note_addFeishuBitableRecord"）
   * @return tenant_access_token
   */
  public synchronized String getTenantAccessTokenFromNote(@NonNull String noteKey)
    throws IOException, JSONException
  {
    String[] credentials = loadCredentialsFromNote(noteKey);
    if (credentials == null)
    {
      throw new IllegalArgumentException(
        "从工具备注 " + noteKey + " 读取凭证失败。请先调用 setToolRemark 配置。"
      );
    }
    return getTenantAccessToken(credentials[0], credentials[1]);
  }

  /**
   * 从指定 key 的工具备注读取凭证
   * @return [app_id, app_secret]，未找到返回 null
   */
  private String[] loadCredentialsFromNote(String noteKey)
  {
    try
    {
      String noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
        .getString(noteKey, "");

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
    cachedAppId = null;
    tokenExpireTime = 0;
    FileLogger.d(TAG, "token 缓存已清除");
  }
}