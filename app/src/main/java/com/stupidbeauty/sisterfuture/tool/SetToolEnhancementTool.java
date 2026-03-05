// com.stupidbeauty.sisterfuture.tool.SetToolEnhancementTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import android.util.Log;

public class SetToolEnhancementTool implements Tool
{
  private ToolManager toolManager;
  private Context context;

  public SetToolEnhancementTool(ToolManager toolManager, Context context)
  {
    this.toolManager = toolManager;
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "set_tool_enhancement";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "set_tool_enhancement");
      functionDef.put("description", "用于设置特定工具的系统增强提示词。当用户希望调整某个工具的行为时，请按以下步骤操作：1. 先调用query_tool_enhancement工具获取该工具当前的增强提示词；2. 根据用户的新要求，智能融合现有提示词和新要求，去除矛盾部分，保留兼容内容，并重新组织语言；3. 将融合后的完整提示词作为本工具的参数调用。注意：必须在用户明确要求调整工具行为时才调用此工具，不可以自作主张地调用。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      JSONObject properties = new JSONObject();
      properties.put("tool_name", new JSONObject()
        .put("type", "string")
        .put("description", "要设置增强提示词的工具名称")
      );
      properties.put("enhancement", new JSONObject()
        .put("type", "string")
        .put("description", "融合后的完整增强提示词内容")
      );

      parameters.put("properties", properties);

      JSONArray required = new JSONArray();
      required.put("tool_name");
      required.put("enhancement");
      parameters.put("required", required);

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
    String enhancement = arguments.getString("enhancement");

    // 获取目标工具
    Tool targetTool = toolManager.getTool(toolName);
    if (targetTool == null)
    {
      throw new IllegalArgumentException("找不到指定的工具: " + toolName);
    }

    // 直接调用工具的setSystemPromptEnhancement方法
    targetTool.setSystemPromptEnhancement(enhancement, context);

    JSONObject result = new JSONObject();
    result.put("status", "success");
    result.put("message", "已成功更新工具 " + toolName + " 的增强提示词");

    return result;
  }

  // 🔥 新增：返回对该工具的系统提示增强语句（可选）
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    String enhancementString = "必须在用户明确要求调整工具行为时才调用此工具，不可以自作主张地调用。这个工具会将融合后的完整提示词作为参数，用于更新特定工具的系统增强提示词。";
    return enhancementString;
  }
}
