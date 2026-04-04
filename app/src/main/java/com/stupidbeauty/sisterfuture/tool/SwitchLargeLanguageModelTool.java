package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONObject;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;

public class SwitchLargeLanguageModelTool implements Tool
{
  private static final String TAG = "SwitchLargeLanguageModelTool";
  private ModelAccessPointManager accessPointManager;

  // 新增：返回对该工具的系统提示增强语句
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    String enhancementString = "## 工具行为增强说明\n\n**工具名称：** switch_large_language_model\n\n**核心职责：**\n专门用于切换大语言模型（LLM）的接入点，当当前接入点出现持续性故障时自动或手动切换到备用接入点。\n\n**触发条件：**\n1. 当前 LLM 接入点持续返回 HTTP 429 (Rate Limit) 错误，且重试次数达到上限\n2. 接入点连续失败次数超过阈值（由 ModelAccessPointManager 管理）\n3. 用户明确要求切换到其他模型服务\n4. 检测到接入点不可用（HTTP 401/403/500/503 等状态码）\n\n**执行流程：**\n1. 调用 `modelAccessPointManager.reportCurrentAccessPointUnavailable()` 标记当前接入点不可用\n2. 自动递增连续失败计数器\n3. 切换到下一个可用接入点（循环切换）\n4. 保存新索引到 SharedPreferences\n5. 记录切换日志，包含 `[ACCESS_POINT_SWITCH]` 标记\n6. 返回新的接入点信息\n\n**错误处理：**\n- 如果所有接入点都已尝试且失败，触发救援模式（#4657）\n- 切换前验证目标接入点的有效性\n- 避免在短时间内频繁切换（防抖动）\n\n**日志输出：**\n- 🔄 [ACCESS_POINT_SWITCH] 限流重试失败，切换到下一个接入点\n- 🔥 [FAILURE_COUNT] 限流导致接入点标记为不可用，计数器：X\n- ✅ [FAILURE_RESET] 请求成功，重置连续失败计数器\n\n**注意事项：**\n- ⚠️ 此工具仅适用于大语言模型接入点切换\n- ⚠️ 不适用于其他类型的网络服务临时错误\n- ⚠️ 切换后需要重新发起请求以验证新接入点可用性\n- ⚠️ 避免与其他网络工具的错误处理逻辑混淆";
    return enhancementString;
  }

  @Override
  public String getName()
  {
    return "switch_large_language_model";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "switch_large_language_model");
      functionDef.put("description", "专门用于切换大语言模型（LLM）的接入点。支持顺序切换到下一个接入点，或通过 target_name 参数精准切换到指定接入点。仅在 LLM 接入点持续故障时使用，不适用于其他网络服务的临时错误。");

      JSONObject properties = new JSONObject();
      properties.put("target_name", new JSONObject()
        .put("type", "string")
        .put("description", "可选：目标接入点名称。若不提供，则执行顺序切换。")
      );

      functionDef.put("parameters", new JSONObject()
        .put("type", "object")
        .put("properties", properties)
        .put("required", new JSONArray())
      );

      return new JSONObject()
        .put("type", "function")
        .put("function", functionDef);
    }
    catch (Exception e)
    {
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  public SwitchLargeLanguageModelTool(ModelAccessPointManager accessPointManager)
  {
    this.accessPointManager = accessPointManager;
  }

  @Override
  public JSONObject execute(JSONObject arguments)
  {
    try
    {
      String targetName = null;
      if (arguments != null && arguments.has("target_name"))
      {
        targetName = arguments.getString("target_name");
      }

      if (targetName != null && !targetName.isEmpty())
      {
        return switchToTargetAccessPoint(targetName);
      }
      else
      {
        return switchToNextAccessPoint();
      }
    }
    catch (Exception e)
    {
      JSONObject errorResult = new JSONObject();
      try
      {
        errorResult.put("error", "Failed to switch access point: " + e.getMessage());
      }
      catch (Exception ignored) {}
      return errorResult;
    }
  }

  private JSONObject switchToNextAccessPoint() throws Exception
  {
    accessPointManager.reportCurrentAccessPointUnavailable();
    ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();

    JSONObject result = new JSONObject();
    result.put("message", "已成功切换到下一个大语言模型接入点");
    result.put("current_access_point", currentAccessPoint.getName());
    result.put("base_url", currentAccessPoint.getBaseUrl());
    result.put("chat_endpoint", currentAccessPoint.getChatEndpoint());
    result.put("model_name", currentAccessPoint.getModelName());

    return result;
  }

  private JSONObject switchToTargetAccessPoint(String targetName) throws Exception
  {
    ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
    if (currentAccessPoint != null && currentAccessPoint.getName().equals(targetName))
    {
      JSONObject result = new JSONObject();
      result.put("message", "当前已在使用 \"" + targetName + "\" 大语言模型接入点");
      result.put("current_access_point", currentAccessPoint.getName());
      result.put("base_url", currentAccessPoint.getBaseUrl());
      result.put("chat_endpoint", currentAccessPoint.getChatEndpoint());
      result.put("model_name", currentAccessPoint.getModelName());
      return result;
    }

    boolean success = accessPointManager.switchToAccessPointByName(targetName);
    
    if (!success)
    {
      java.util.List<ModelAccessPoint> allAccessPoints = accessPointManager.getAllAccessPoints();
      JSONObject errorResult = new JSONObject();
      StringBuilder availableNames = new StringBuilder();
      for (int i = 0; i < allAccessPoints.size(); i++)
      {
        availableNames.append(allAccessPoints.get(i).getName());
        if (i < allAccessPoints.size() - 1)
        {
          availableNames.append(", ");
        }
      }
      errorResult.put("error", "未找到名为 \"" + targetName + "\" 的大语言模型接入点，当前可用接入点包括：" + availableNames.toString());
      return errorResult;
    }

    ModelAccessPoint switchedAccessPoint = accessPointManager.getCurrentAccessPoint();

    JSONObject result = new JSONObject();
    result.put("message", "已成功切换到大语言模型接入点：" + targetName);
    result.put("current_access_point", switchedAccessPoint.getName());
    result.put("base_url", switchedAccessPoint.getBaseUrl());
    result.put("chat_endpoint", switchedAccessPoint.getChatEndpoint());
    result.put("model_name", switchedAccessPoint.getModelName());

    return result;
  }
}