package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONObject;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;

public class SwitchAccessPointTool implements Tool
{
  private static final String TAG = "SwitchAccessPointTool";
  private ModelAccessPointManager accessPointManager;



  // 🔥 新增：返回对该工具的系统提示增强语句（可选）
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    String enhancementString = "当用户明确要求切换模型接入点时调用此工具。支持两种模式：\n\n1. **顺序切换模式（默认）**：当用户未指定目标名称时，轮转到下一个候选接入点。\n2. **精准切换模式**：当用户提供 `target_name` 参数时，直接切换到指定名称的接入点（如 \"Qwen3.5-397B-A17B-专业版\"）。\n\n**参数说明：**\n- `target_name`（可选）：目标接入点名称。若不提供，则执行顺序切换。\n\n**错误处理：**\n- 若目标接入点不存在，返回友好提示：\"未找到名为 [XXX] 的接入点，当前可用接入点包括：[列表]\"\n- 若当前已是目标接入点，提示：\"当前已在使用 [XXX] 接入点\"\n\n**重要约束：**\n- 必须是在用户用直接语言明确要求切换接入点时才调用此工具，不可以自作主张地调用，以免引起死循环。\n- 切换成功后，建议调用 `get_current_access_point_info` 确认新接入点已生效。";
    return enhancementString; // 默认不提供增强
  }




  @Override
  public String getName()
  {
    return "switch_access_point";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "switch_access_point");
      functionDef.put("description", "当用户明确要求切换模型接入点时调用。支持顺序切换到下一个接入点，或通过 target_name 参数精准切换到指定接入点。");

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
    // 只在用户明确要求切换接入点时才包含此工具
    return true;
  }

  // 🔒 通过构造函数注入 ModelAccessPointManager 实例
  public SwitchAccessPointTool(ModelAccessPointManager accessPointManager)
  {
    this.accessPointManager = accessPointManager;
  }

  @Override
  public JSONObject execute(JSONObject arguments)
  {
    try
    {
      // 检查是否提供了 target_name 参数
      String targetName = null;
      if (arguments != null && arguments.has("target_name"))
      {
        targetName = arguments.getString("target_name");
      }

      if (targetName != null && !targetName.isEmpty())
      {
        // 精准切换模式：根据名称查找并切换到指定接入点
        return switchToTargetAccessPoint(targetName);
      }
      else
      {
        // 顺序切换模式：切换到下一个接入点
        return switchToNextAccessPoint();
      }
    }
    catch (Exception e)
    {
      // 安全构造错误对象
      JSONObject errorResult = new JSONObject();
      try
      {
        errorResult.put("error", "Failed to switch access point: " + e.getMessage());
      }
      catch (Exception ignored)
      {
        // 忽略
      }
      return errorResult;
    }
  }

  /**
   * 顺序切换到下一个接入点
   */
  private JSONObject switchToNextAccessPoint() throws Exception
  {
    // 切换到下一个接入点
    accessPointManager.reportCurrentAccessPointUnavailable();

    // 获取当前切换后的接入点信息
    ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();

    // 构造返回结果
    JSONObject result = new JSONObject();
    result.put("message", "已成功切换到下一个接入点");
    result.put("current_access_point", currentAccessPoint.getName());
    result.put("base_url", currentAccessPoint.getBaseUrl());
    result.put("chat_endpoint", currentAccessPoint.getChatEndpoint());
    result.put("model_name", currentAccessPoint.getModelName());

    return result;
  }

  /**
   * 精准切换到指定名称的接入点
   */
  private JSONObject switchToTargetAccessPoint(String targetName) throws Exception
  {
    // 获取所有接入点列表
    java.util.List<ModelAccessPoint> allAccessPoints = accessPointManager.getAllAccessPoints();
    
    // 查找目标接入点
    ModelAccessPoint targetAccessPoint = null;
    for (ModelAccessPoint ap : allAccessPoints)
    {
      if (ap.getName().equals(targetName))
      {
        targetAccessPoint = ap;
        break;
      }
    }

    // 如果未找到目标接入点，返回错误提示
    if (targetAccessPoint == null)
    {
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
      errorResult.put("error", "未找到名为 \"" + targetName + "\" 的接入点，当前可用接入点包括：" + availableNames.toString());
      return errorResult;
    }

    // 检查当前是否已经是目标接入点
    ModelAccessPoint currentAccessPoint = accessPointManager.getCurrentAccessPoint();
    if (currentAccessPoint.getName().equals(targetName))
    {
      JSONObject result = new JSONObject();
      result.put("message", "当前已在使用 \"" + targetName + "\" 接入点");
      result.put("current_access_point", currentAccessPoint.getName());
      result.put("base_url", currentAccessPoint.getBaseUrl());
      result.put("chat_endpoint", currentAccessPoint.getChatEndpoint());
      result.put("model_name", currentAccessPoint.getModelName());
      return result;
    }

    // 切换到目标接入点
    accessPointManager.reportCurrentAccessPointUnavailable();

    // 构造返回结果
    JSONObject result = new JSONObject();
    result.put("message", "已成功切换到接入点: " + targetName);
    result.put("current_access_point", targetAccessPoint.getName());
    result.put("base_url", targetAccessPoint.getBaseUrl());
    result.put("chat_endpoint", targetAccessPoint.getChatEndpoint());
    result.put("model_name", targetAccessPoint.getModelName());

    return result;
  }
}