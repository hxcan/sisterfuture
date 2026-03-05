package com.stupidbeauty.sisterfuture.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class ToolManager
{
  private static final String TAG = "ToolManager";
  private Map<String, Tool> toolRegistry = new HashMap<>(); // 字段名是 toolRegistry

  public void registerTool(Tool tool)
  {
    toolRegistry.put(tool.getName(), tool);
  }

  public Tool getTool(String name)
  {
    return toolRegistry.get(name);
  }

  public JSONArray buildToolsJsonArray()
  {
    JSONArray tools = new JSONArray();
    for (Tool tool : toolRegistry.values())
    {
      tools.put(tool.getDefinition());
    }
    return tools;
  }

  public JSONObject executeTool(String toolName, JSONObject arguments) throws Exception
  {
    Tool tool = getTool(toolName);
    if (tool == null)
    {
      throw new IllegalArgumentException("Unknown tool: " + toolName);
    }
    return tool.execute(arguments);
  }

  // 🔥 新增：判断是否为异步工具
  public boolean isToolAsync(String toolName)
  {
    Tool tool = getTool(toolName);
    return tool != null && tool.isAsync();
  }

  // 🔥 新增：异步执行入口
  public void executeToolAsync(String toolName, JSONObject arguments, Tool.OnResultCallback callback)
  {
    Tool tool = getTool(toolName);
    if (tool == null)
    {
      try
      {
        JSONObject error = new JSONObject();
        error.put("error", "Unknown tool: " + toolName);
        callback.onError(new IllegalArgumentException("Unknown tool: " + toolName));
      }
      catch (Exception e)
      {
        callback.onError(e);
      }
      return;
    }

    if (!tool.isAsync())
    {
      // 同步工具包装成异步返回
      try
      {
        JSONObject result = executeTool(toolName, arguments);
        callback.onResult(result);
      }
      catch (Exception e)
      {
        callback.onError(e);
      }
    }
    else
    {
      // 异步工具直接调用
      tool.executeAsync(arguments, callback);
    }
  }

  // ✅ 原有方法保持不变
  public List<Tool> getRegisteredTools()
  {
    return new ArrayList<>(toolRegistry.values());
  }

  public List<String> getRegisteredToolNames()
  {
    return new ArrayList<>(toolRegistry.keySet());
  }

  public JSONObject getToolDefinition(String toolName)
  {
    Tool tool = toolRegistry.get(toolName);
    return tool != null ? tool.getDefinition() : null;
  }
}
