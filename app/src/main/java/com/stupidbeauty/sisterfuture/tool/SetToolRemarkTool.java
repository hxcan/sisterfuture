// com.stupidbeauty.sisterfuture.tool.SetToolRemarkTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class SetToolRemarkTool implements Tool
{
  private ToolManager toolManager;
  private Context context;

  public SetToolRemarkTool(ToolManager toolManager, Context context)
  {
    this.toolManager = toolManager;
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "set_tool_remark";
  }

  @Override
  public JSONObject getDefinition()
  {
    JSONObject def = new JSONObject();
    try
    {
      def.put("name", "set_tool_remark");
      def.put("description", "用于设置特定工具的备注信息。必须在用户明确要求修改备注时才调用此工具，不可擅自执行。"); // ✅ 修复：添加 description

      JSONObject params = new JSONObject();
      params.put("type", "object");

      JSONObject props = new JSONObject();
      props.put("tool_name", new JSONObject()
        .put("type", "string")
        .put("description", "要设置备注的工具名称"));
      props.put("remark", new JSONObject()
        .put("type", "string")
        .put("description", "要设置的备注内容"));

      params.put("properties", props);
      params.put("required", new JSONArray().put("tool_name").put("remark"));
      def.put("parameters", params);

      // ✅ 必须保留 type: "function"，否则工具无法被识别
      return new JSONObject()
        .put("type", "function")
        .put("function", def);
    }
    catch (Exception e)
    {
      // 严格来说，JSONObject 的 put 不会抛出异常，但为了安全，还是捕获
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
    return false;
  }

  @Override
  public JSONObject execute(JSONObject arguments) throws Exception
  {
    String toolName = arguments.getString("tool_name");
    String remark = arguments.getString("remark");

    Tool targetTool = toolManager.getTool(toolName);
    if (targetTool == null)
    {
      JSONObject error = new JSONObject();
      error.put("error", "找不到工具：" + toolName);
      return error;
    }

    // 调用默认方法写入备注
    targetTool.setNote(remark, context);

    JSONObject result = new JSONObject();
    result.put("status", "success");
    result.put("message", "已成功为工具 " + toolName + " 设置备注");
    return result;
  }

  // 🔥 可选：提供系统增强提示
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    return "用于设置特定工具的备注信息。必须在用户明确要求修改备注时才调用此工具，不可擅自执行。";
  }
}
