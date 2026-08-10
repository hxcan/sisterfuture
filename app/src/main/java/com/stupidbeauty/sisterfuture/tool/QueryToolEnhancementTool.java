package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import android.util.Log;

public class QueryToolEnhancementTool implements Tool
{
  private Context context;
  private static final String TAG = "QueryToolEnh"; // 修复：缩短TAG长度以满足Lint要求（23字符以内）
  private ToolManager toolManager;
  private HashMap<String, String> lastQueryResult = new HashMap<>(); //!< The last query result.

  public QueryToolEnhancementTool(ToolManager toolManager, Context context)
  {
    this.toolManager = toolManager;
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "queryToolEnhancement";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "queryToolEnhancement");
      functionDef.put("description", "查询特定工具的系统增强提示词，用于指导大模型如何根据用户要求融合增强提示词。如果工具没有提供增强提示词，则返回空字符串。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");
      parameters.put("properties", new JSONObject()
        .put("toolName", new JSONObject()
          .put("type", "string")
          .put("description", "要查询的工具名称")
        )
      );
      parameters.put("required", new JSONArray().put("toolName"));

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
    String toolName = arguments.optString("toolName", "").trim();
    if (toolName.isEmpty())
    {
      throw new IllegalArgumentException("toolName 参数不能为空。目前这个工具本身仍然在调试中，遇到本错误之后，妳可以向用户报告之后，忽略它，继续根据聊天消息流来进行逻辑对话。");
    }

    Tool tool = toolManager.getTool(toolName);
    if (tool == null)
    {
      throw new IllegalArgumentException("未找到名为 " + toolName + " 的工具");
    }

    String enhancement = tool.getSystemPromptEnhancement(context);
    if (enhancement == null)
    {
      enhancement = "";
    }

    JSONObject result = new JSONObject();
    result.put("toolName", toolName);
    result.put("enhancement", enhancement);
    result.put("has_enhancement", !enhancement.isEmpty());

    String lastResult = lastQueryResult.get(toolName); // Get the last result.


    if (enhancement.equals(lastResult)) // Not changed.
    {
      result.put("warning", "该工具的系统增强提示词从妳上次查询以来还没有变动过，到现在为止用户也没有要求要修改该工具的系统增强提示词。在该工具的系统增强提示词发生真正改变之前，禁止妳再继续查询该工具的系统增强提示词了。");
    } // if (lastResult.equals(enhancement)) // Not changed.


    lastQueryResult.put(toolName, enhancement); // Remember last query result.
    Log.d(TAG, CodePosition.newInstance().toString() + ", result: " + enhancement); // Debug.


    return result;
  }

  // 🔥 新增：返回对该工具的系统提示增强语句（可选）
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    String enhancementString = "必须在用户明确要求查询工具增强提示词时才调用此工具，不可以自作主张地调用。这个工具会查询特定工具的系统增强提示词，用于指导大模型如何根据用户要求融合增强提示词。";
    return enhancementString;
  }
}