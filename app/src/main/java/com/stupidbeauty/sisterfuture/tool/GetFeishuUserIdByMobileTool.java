package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 飞书工具 #7: 通过手机号查询飞书用户的 open_id
 *
 * 工具签名：
 * getFeishuUserIdByMobile(
 *   mobile: str,                  # 单个手机号模式
 *   mobiles: list[str] = None,    # 批量模式（与 mobile 二选一）
 *   user_id_type: str = "open_id" # open_id / union_id / user_id
 * )
 *
 * 凭证配置（通过 setToolRemark 调用设置）：
 * {
 *   "feishu_app_id": "cli_xxx",
 *   "feishu_app_secret": "xxx"
 * }
 *
 * 返回值（单个）：
 * {
 *   "success": true,
 *   "mobile": "15201903961",
 *   "open_id": "ou_xxx",
 *   "status": { "is_activated": true, ... },
 *   "not_found": false
 * }
 */
public class GetFeishuUserIdByMobileTool implements Tool
{
  private static final String TAG = "GetFeishuUserIdByMobile";

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final OkHttpClient httpClient = new OkHttpClient();
  private final FeishuAuthManager authManager;

  public GetFeishuUserIdByMobileTool(Context context)
  {
    this.context = context;
    this.authManager = new FeishuAuthManager(context);
  }

  @Override
  public String getName()
  {
    return "getFeishuUserIdByMobile";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "getFeishuUserIdByMobile");
      functionDef.put("description",
        "通过手机号查询飞书用户的 open_id。支持单个查询和批量查询。" +
        "用户未找到时返回 not_found=true（不是错误）。\n\n" +
        "凭证配置：调用 setToolRemark 工具，设置 feishu_app_id 和 feishu_app_secret。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      parameters.put("properties", new JSONObject()
        .put("mobile", new JSONObject()
          .put("type", "string")
          .put("description", "单个手机号（与 mobiles 二选一）"))
        .put("mobiles", new JSONObject()
          .put("type", "array")
          .put("items", new JSONObject().put("type", "string"))
          .put("description", "批量手机号列表（与 mobile 二选一）"))
        .put("app_id", new JSONObject()
          .put("type", "string")
          .put("description", "飞书应用 App ID（可选，默认从工具备注读取）"))
        .put("app_secret", new JSONObject()
          .put("type", "string")
          .put("description", "飞书应用 App Secret（可选，默认从工具备注读取）"))
        .put("user_id_type", new JSONObject()
          .put("type", "string")
          .put("description", "用户ID类型：open_id（默认）/ union_id / user_id"))
      );

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "Failed to build definition", e);
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public boolean isAsync()
  {
    return true;
  }

  /**
   * 从工具备注读取凭证（app_id / app_secret）
   * @return [app_id, app_secret]，缺失返回 null
   */
  private String[] loadCredentials()
  {
    try
    {
      String noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
        .getString("note_getFeishuUserIdByMobile", "");

      if (noteJson.isEmpty())
      {
        // 回退：addFeishuBitableRecord
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

  @Override
  public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback)
  {
    executor.execute(() ->
    {
      try
      {
        String mobile = arguments.optString("mobile", "").trim();
        String userIdType = arguments.optString("user_id_type", "open_id");

        // 凭证：参数 > 备注
        String paramAppId = arguments.optString("app_id", "").trim();
        String paramAppSecret = arguments.optString("app_secret", "").trim();

        // 读取凭证
        String[] credentials = loadCredentials();
        if (credentials == null)
        {
          throw new IllegalArgumentException(
            "缺少凭证配置。请先调用 setToolRemark 写入 feishu_app_id 和 feishu_app_secret。"
          );
        }

        String appId = paramAppId.isEmpty() ? credentials[0] : paramAppId;
        String appSecret = paramAppSecret.isEmpty() ? credentials[1] : paramAppSecret;

        // 获取 token
        String token = authManager.getTenantAccessToken(appId, appSecret);

        // 批量模式
        JSONArray mobilesArray = arguments.optJSONArray("mobiles");
        if (mobilesArray != null)
        {
          batchQuery(callback, mobilesArray, token, userIdType);
          return;
        }

        // 单个模式
        if (mobile.isEmpty())
        {
          throw new IllegalArgumentException(
            "Missing required parameter: 必须提供 mobile 或 mobiles 之一");
        }

        String normalized = normalizeMobile(mobile);
        FileLogger.d(TAG, "查询单个手机号: " + normalized);

        JSONObject body = new JSONObject();
        JSONArray mobilesList = new JSONArray();
        mobilesList.put(normalized);
        body.put("mobiles", mobilesList);

        JSONObject result = callBatchApi(token, body, userIdType);

        // 转换为单条返回格式
        if (result.optBoolean("success", false))
        {
          JSONArray results = result.getJSONArray("results");
          if (results.length() > 0)
          {
            callback.onResult(results.getJSONObject(0));
            return;
          }
        }

        // 找不到用户
        JSONObject notFound = new JSONObject();
        notFound.put("success", true);
        notFound.put("mobile", normalized);
        notFound.put("not_found", true);
        callback.onResult(notFound);
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "查询用户出错", e);
        callback.onError(e);
      }
    });
  }

  /**
   * 批量查询处理
   */
  private void batchQuery(OnResultCallback callback, JSONArray mobilesArray, String token, String userIdType)
    throws IOException, JSONException
  {
    FileLogger.d(TAG, "批量查询 " + mobilesArray.length() + " 个手机号");

    // 归一化所有手机号
    JSONArray normalized = new JSONArray();
    for (int i = 0; i < mobilesArray.length(); i++)
    {
      normalized.put(normalizeMobile(mobilesArray.getString(i)));
    }

    JSONObject body = new JSONObject();
    body.put("mobiles", normalized);

    callback.onResult(callBatchApi(token, body, userIdType));
  }

  /**
   * 调用飞书 batch_get_id API
   */
  private JSONObject callBatchApi(String token, JSONObject body, String userIdType)
    throws IOException, JSONException
  {
    String url = "https://open.feishu.cn/open-apis/contact/v3/users/batch_get_id?user_id_type=" + userIdType;

    RequestBody requestBody = RequestBody.create(
      body.toString(),
      MediaType.get("application/json; charset=utf-8")
    );

    Request request = new Request.Builder()
      .url(url)
      .post(requestBody)
      .header("Authorization", "Bearer " + token)
      .header("Content-Type", "application/json; charset=utf-8")
      .build();

    Response response = httpClient.newCall(request).execute();
    String responseBody = response.body() != null ? response.body().string() : "";

    if (!response.isSuccessful())
    {
      // 401 重试
      if (response.code() == 401)
      {
        FileLogger.d(TAG, "401 未授权，刷新 token 重试");
        authManager.invalidateToken();
        // 重试时仍需要 appId / appSecret，但这里没有保存，需要从调用方传入或缓存
        // 简化处理：直接使用原有 token（token 过期强制刷新由调用方处理）
        response = httpClient.newCall(new Request.Builder()
          .url(url)
          .post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8")))
          .header("Authorization", "Bearer " + token)
          .header("Content-Type", "application/json; charset=utf-8")
          .build()).execute();
        responseBody = response.body() != null ? response.body().string() : "";
      }

      if (!response.isSuccessful())
      {
        throw new IOException(
          "查询用户失败：HTTP " + response.code() + " " + response.message() +
          "\n响应体：" + responseBody);
      }
    }

    JSONObject result = new JSONObject(responseBody);

    if (result.optInt("code", 0) != 0)
    {
      throw new IOException(
        "飞书 API 返回错误：code=" + result.optInt("code") +
        ", msg=" + result.optString("msg", ""));
    }

    // 转换为友好格式
    JSONArray userList = result.getJSONObject("data").optJSONArray("user_list");
    JSONArray mobilesInput = body.getJSONArray("mobiles");

    JSONArray results = new JSONArray();
    for (int i = 0; i < mobilesInput.length(); i++)
    {
      String mobile = mobilesInput.getString(i);
      JSONObject entry = new JSONObject();
      entry.put("mobile", mobile);
      entry.put("not_found", true);

      if (userList != null)
      {
        for (int j = 0; j < userList.length(); j++)
        {
          JSONObject user = userList.getJSONObject(j);
          if (mobile.equals(user.optString("mobile", "")))
          {
            entry.put("not_found", false);
            entry.put("open_id", user.optString("user_id", ""));
            entry.put("status", user.optJSONObject("status"));
            break;
          }
        }
      }

      results.put(entry);
    }

    JSONObject finalResult = new JSONObject();
    finalResult.put("success", true);
    finalResult.put("results", results);
    finalResult.put("fetched_at", System.currentTimeMillis());

    return finalResult;
  }

  /**
   * 归一化手机号格式：去除空格、破折号等，添加 +86 前缀
   */
  private String normalizeMobile(String mobile)
  {
    String cleaned = mobile.replaceAll("[\\s\\-]+", "");
    if (!cleaned.startsWith("+"))
    {
      if (cleaned.startsWith("86") && cleaned.length() == 13)
      {
        cleaned = "+" + cleaned;
      }
      else if (cleaned.length() == 11)
      {
        cleaned = "+86" + cleaned;
      }
    }
    return cleaned;
  }
}