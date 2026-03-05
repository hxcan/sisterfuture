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
    String enhancementString = "必须是在用户用直接语言明确要求切换接入点时才调用此工具，不可以自作主张地调用，以免引起死循环，那样妳将会被打屁股。";
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
      functionDef.put("description", "当用户明确要求切换模型接入点时调用。此工具会将接入点管理器轮转到下一个候选接入点，适用于需要手动切换模型的场景。");

      functionDef.put("parameters", new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject())
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
}
