package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GetCurrentTimeTool implements Tool
{
  @Override
  public String getName()
  {
    return "get_current_time";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "get_current_time");
      functionDef.put("description", "获取当前的日期和时间（北京时间）。仅在当前确实需要得知时间信息的情况下才应当调用。");

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
    return true;
  }

  @Override
  public JSONObject execute(JSONObject arguments)
  {
    try
    {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA);
      sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
      String currentTime = sdf.format(new Date());

      JSONObject result = new JSONObject();
      result.put("current_time", currentTime);
      return result;
    }
    catch (Exception e)
    {
      // 安全构造错误对象，避免 JSONException
      JSONObject errorResult = new JSONObject();
      try
      {
        errorResult.put("error", "Failed to get time");
      }
      catch (Exception ignored)
      {
        // 忽略，返回空对象
      }
      return errorResult;
    }
  }
}
