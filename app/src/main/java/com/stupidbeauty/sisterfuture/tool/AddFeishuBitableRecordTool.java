package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
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
 * 飞书多维表格添加记录工具
 *
 * 工具签名：
 * addFeishuBitableRecord(
 *   table_name: str,               # 多维表格的表名（或直接传 table_id）
 *   fields: dict,                  # 字段值字典
 *   app_id: str = None,            # 可选，默认从工具备注读取
 *   app_secret: str = None,        # 可选，默认从工具备注读取
 *   app_token: str = None,         # 可选，默认从工具备注读取
 *   user_id_type: str = "open_id"  # 默认 open_id
 * )
 *
 * 凭证配置（通过 setToolRemark 调用设置）：
 * {
 *   "feishu_app_id": "cli_xxx",
 *   "feishu_app_secret": "xxx",
 *   "feishu_app_token": "DblBbSKDGaD8nOsEhSXcZpq0njg"
 * }
 *
 * 支持字段类型：
 * - User: [{"id": "ou_xxx"}]
 * - SingleSelect: "选项名"
 * - DateTime: ISO 8601 字符串 或 毫秒时间戳
 * - Text: 字符串
 * - Number: 整数 / 浮点数
 */
public class AddFeishuBitableRecordTool implements Tool
{
  private static final String TAG = "AddFeishuBitableRecord";

  private final Context context;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final OkHttpClient httpClient = new OkHttpClient();
  private final FeishuAuthManager authManager;

  public AddFeishuBitableRecordTool(Context context)
  {
    this.context = context;
    this.authManager = new FeishuAuthManager(context);
  }

  @Override
  public String getName()
  {
    return "addFeishuBitableRecord";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "addFeishuBitableRecord");
      functionDef.put("description",
        "向飞书多维表格添加一条记录。" +
        "支持字段类型：User（用户，传 [{\"id\": \"ou_xxx\"}]）、" +
        "SingleSelect（单选，传选项名字符串）、" +
        "DateTime（日期，传毫秒时间戳或 ISO 8601 字符串）、" +
        "Text（文本）、Number（数字）。\n\n" +
        "凭证配置：调用 setToolRemark 工具，设置 feishu_app_id、feishu_app_secret 和 feishu_app_token。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      parameters.put("properties", new JSONObject()
        .put("table_name", new JSONObject()
          .put("type", "string")
          .put("description", "多维表格的表名，例如 \"任务管理\""))
        .put("table_id", new JSONObject()
          .put("type", "string")
          .put("description", "多维表格的 table_id（可选，与 table_name 二选一）"))
        .put("fields", new JSONObject()
          .put("type", "object")
          .put("description",
            "字段值字典，键为字段中文名（如 \"任务描述\"），值为字段值。" +
            "User 类型传 [{\"id\": \"open_id\"}] 列表；" +
            "SingleSelect 传选项名字符串；" +
            "DateTime 传毫秒时间戳；其他直接传字符串/数字。"))
        .put("app_id", new JSONObject()
          .put("type", "string")
          .put("description", "飞书应用 App ID（可选，默认从工具备注读取）"))
        .put("app_secret", new JSONObject()
          .put("type", "string")
          .put("description", "飞书应用 App Secret（可选，默认从工具备注读取）"))
        .put("app_token", new JSONObject()
          .put("type", "string")
          .put("description", "飞书多维表格的 app_token（可选，默认从工具备注读取）"))
        .put("user_id_type", new JSONObject()
          .put("type", "string")
          .put("description", "用户ID类型：open_id（默认）/ union_id / user_id"))
      );

      parameters.put("required", new JSONArray(new String[]{"fields"}));

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
   * 从工具备注读取完整凭证（app_id / app_secret / app_token）
   * @return String[]{app_id, app_secret, app_token}，缺失返回 null
   */
  private String[] loadCredentials()
  {
    try
    {
      String noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
        .getString("note_addFeishuBitableRecord", "");

      if (noteJson.isEmpty())
      {
        // 回退方案：尝试从共享的 feishu 凭证读取
        noteJson = context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE)
          .getString("note_feishu_shared", "");
      }

      if (noteJson.isEmpty())
      {
        return null;
      }

      JSONObject saved = new JSONObject(noteJson);
      String appId = saved.optString("feishu_app_id", "").trim();
      String appSecret = saved.optString("feishu_app_secret", "").trim();
      String appToken = saved.optString("feishu_app_token", "").trim();

      if (appId.isEmpty() || appSecret.isEmpty() || appToken.isEmpty())
      {
        return null;
      }

      return new String[]{appId, appSecret, appToken};
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
        // 1. 解析参数
        String tableName = arguments.optString("table_name", "").trim();
        String tableId = arguments.optString("table_id", "").trim();
        JSONObject fields = arguments.getJSONObject("fields");
        String userIdType = arguments.optString("user_id_type", "open_id");

        // 凭证：参数 > 备注
        String paramAppId = arguments.optString("app_id", "").trim();
        String paramAppSecret = arguments.optString("app_secret", "").trim();
        String paramAppToken = arguments.optString("app_token", "").trim();

        // === Phase 1: 先校验业务参数 ===
        if (tableName.isEmpty() && tableId.isEmpty())
        {
          throw new IllegalArgumentException(
            "Missing required parameter: 必须提供 table_name 或 table_id 之一"
          );
        }

        if (fields.length() == 0)
        {
          throw new IllegalArgumentException(
            "Missing required parameter: fields 不能为空"
          );
        }

        FileLogger.d(TAG, "开始添加记录: table=" + (tableId.isEmpty() ? tableName : tableId) +
          ", fields_count=" + fields.length());

        // === Phase 2: 业务参数都通过后，才检查凭证 ===
        String[] credentials = null;
        // 只有当参数没传凭证时，才去读备注
        if (paramAppId.isEmpty() || paramAppSecret.isEmpty() || paramAppToken.isEmpty())
        {
          credentials = loadCredentials();
        }

        if (credentials == null &&
            (paramAppId.isEmpty() || paramAppSecret.isEmpty() || paramAppToken.isEmpty()))
        {
          throw new IllegalArgumentException(
            "缺少凭证配置。请通过以下任一方式提供凭证：" +
            "(1) 在参数中传入 app_id/app_secret/app_token；" +
            "(2) 调用 setToolRemark 写入 feishu_app_id、feishu_app_secret 和 feishu_app_token。"
          );
        }

        // 3. 决定最终凭证值（参数优先，缺失则用备注）
        String appId = paramAppId.isEmpty() ? credentials[0] : paramAppId;
        String appSecret = paramAppSecret.isEmpty() ? credentials[1] : paramAppSecret;
        String appToken = paramAppToken.isEmpty() ? credentials[2] : paramAppToken;

        // 4. 获取 token（传入 app_id 和 app_secret）
        String token = authManager.getTenantAccessToken(appId, appSecret);

        // 5. 如果只有 table_name，查询得到 table_id
        if (tableId.isEmpty())
        {
          tableId = resolveTableId(token, appToken, tableName);
          FileLogger.d(TAG, "表名解析: " + tableName + " -> " + tableId);
        }

        // 6. 调用 API 添加记录
        JSONObject recordBody = new JSONObject();
        recordBody.put("fields", fields);

        RequestBody requestBody = RequestBody.create(
          recordBody.toString(),
          MediaType.get("application/json; charset=utf-8")
        );

        String url = "https://open.feishu.cn/open-apis/bitable/v1/apps/" +
          appToken + "/tables/" + tableId + "/records" +
          "?user_id_type=" + userIdType;

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
          // token 可能过期，强制刷新后重试一次
          if (response.code() == 401)
          {
            FileLogger.d(TAG, "401 未授权，强制刷新 token 后重试");
            authManager.invalidateToken();
            token = authManager.getTenantAccessToken(appId, appSecret);

            Request retryRequest = new Request.Builder()
              .url(url)
              .post(RequestBody.create(
                recordBody.toString(),
                MediaType.get("application/json; charset=utf-8")))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json; charset=utf-8")
              .build();

            response = httpClient.newCall(retryRequest).execute();
            responseBody = response.body() != null ? response.body().string() : "";
          }

          if (!response.isSuccessful())
          {
            throw new IOException(
              "添加记录失败：HTTP " + response.code() + " " + response.message() +
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

        // 7. 返回成功结果
        JSONObject record = result.optJSONObject("data") != null
          ? result.getJSONObject("data").optJSONObject("record")
          : null;

        String recordId = record != null ? record.optString("record_id", "") : "";

        JSONObject responseObj = new JSONObject();
        responseObj.put("success", true);
        responseObj.put("record_id", recordId);
        responseObj.put("table_id", tableId);
        responseObj.put("table_name", tableName);
        responseObj.put("record", record);
        responseObj.put("message", "记录添加成功");

        FileLogger.d(TAG, "记录添加成功: record_id=" + recordId);

        callback.onResult(responseObj);
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "添加记录出错", e);
        callback.onError(e);
      }
    });
  }

  /**
   * 通过表名查询 table_id（需要 app_token）
   */
  private String resolveTableId(String token, String appToken, String tableName)
    throws IOException, JSONException
  {
    Request request = new Request.Builder()
      .url("https://open.feishu.cn/open-apis/bitable/v1/apps/" + appToken + "/tables")
      .get()
      .header("Authorization", "Bearer " + token)
      .build();

    Response response = httpClient.newCall(request).execute();

    if (!response.isSuccessful())
    {
      throw new IOException("查询表列表失败：HTTP " + response.code());
    }

    JSONObject result = new JSONObject(response.body().string());

    if (result.optInt("code", 0) != 0)
    {
      throw new IOException("查询表列表失败：" + result.optString("msg", ""));
    }

    JSONArray items = result.getJSONObject("data").optJSONArray("items");
    if (items == null)
    {
      throw new IOException("表列表为空");
    }

    for (int i = 0; i < items.length(); i++)
    {
      JSONObject item = items.getJSONObject(i);
      if (tableName.equals(item.optString("name", "")))
      {
        return item.getString("table_id");
      }
    }

    throw new IOException("未找到表名: " + tableName);
  }
}