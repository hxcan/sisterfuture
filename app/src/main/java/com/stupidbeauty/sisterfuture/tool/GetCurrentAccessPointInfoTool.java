package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;

public class GetCurrentAccessPointInfoTool implements Tool
{
  private final ModelAccessPointManager accessPointManager;

  public GetCurrentAccessPointInfoTool(ModelAccessPointManager accessPointManager)
  {
    this.accessPointManager = accessPointManager;
  }

  @Override
  public String getName()
  {
    return "get_current_access_point_info";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "get_current_access_point_info");
      functionDef.put("description", "获取当前实际正在使用的接入点信息。用于确认当前模型接入点是否有效，避免因自动切换导致信息不一致。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", new JSONObject());
      parameters.put("required", new JSONObject());

      functionDef.put("parameters", parameters);

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
    return true; // 始终可用
  }

  @Override
  public JSONObject execute(JSONObject arguments)
  {
    try
    {
      ModelAccessPoint current = accessPointManager.getCurrentAccessPoint();
      if (current == null)
      {
        JSONObject result = new JSONObject();
        result.put("error", "无法获取当前接入点信息，管理器未初始化或索引越界");
        return result;
      }

      JSONObject result = new JSONObject();
      result.put("current_access_point", current.getName());
      result.put("base_url", current.getBaseUrl());
      result.put("chat_endpoint", current.getChatEndpoint());
      result.put("model_name", current.getModelName());
      result.put("status", "active");

      return result;
    }
    catch (Exception e)
    {
      JSONObject errorResult = new JSONObject();
      try
      {
        errorResult.put("error", "Failed to retrieve current access point info");
      }
      catch (Exception ignored)
      {}
      return errorResult;
    }
  }
}
