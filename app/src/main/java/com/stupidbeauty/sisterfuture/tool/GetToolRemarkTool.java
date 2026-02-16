package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;


public class GetToolRemarkTool implements Tool
{
  private ToolManager toolManager;
  private Context context;

  public GetToolRemarkTool(ToolManager toolManager, Context context)
  {
    this.toolManager = toolManager;
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "get_tool_remark";
  }

  @Override
  public boolean shouldInclude()
  {
    return true; // 默认包含
  }

  @Override
  public JSONObject getDefinition()
  {
    JSONObject def = new JSONObject();
    try
    {
      def.put("name", "get_tool_remark");

      JSONObject params = new JSONObject();
      params.put("type", "object");

      JSONObject props = new JSONObject();
      props.put("tool_name", new JSONObject()
        .put("type", "string")
        .put("description", "要获取备注的工具名称"));

      params.put("properties", props);
      params.put("required", new JSONArray().put("tool_name"));
      def.put("parameters", params);

      return new JSONObject()
        .put("type", "function")
        .put("function", def);
    }
    catch (Exception e)
    {
      return new JSONObject();
    }
  }

  @Override
  public JSONObject execute(JSONObject arguments) throws Exception
  {
    String toolName = arguments.getString("tool_name");
    Tool targetTool = toolManager.getTool(toolName);

    if (targetTool == null)
    {
      JSONObject error = new JSONObject();
      error.put("error", "找不到工具：" + toolName);
      return error;
    }

    String remark = targetTool.getNote(context);

    JSONObject result = new JSONObject();
    result.put("remark", remark != null ? remark : "");
    return result;
  }
}
