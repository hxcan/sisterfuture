package com.stupidbeauty.sisterfuture.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

public class ToolManager
{
  private static final String TAG = "ToolManager";
  private Map<String, Tool> toolRegistry = new HashMap<>();
  private ToolCallTracker callTracker = new ToolCallTracker();  // 🔥 新增：幂等性追踪器

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

  public boolean isToolAsync(String toolName)
  {
    Tool tool = getTool(toolName);
    return tool != null && tool.isAsync();
  }

  // 🔥 新增：带幂等检查的异步执行入口
  public void executeToolAsync(String toolId, String toolName, JSONObject arguments, Tool.OnResultCallback callback)
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

    // 🔥 幂等性检查：如果该 toolId 已回复过，则忽略
    if (!callTracker.tryMarkAsReplied(toolId))
    {
      Log.w(TAG, "忽略重复的工具回复消息，toolId=" + toolId + ", toolName=" + toolName);
      return;  // 忽略第二次回复
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

  // 🔥 新增：清理已追踪的 toolId（在新一轮对话前调用）
  public void clearTrackedCalls()
  {
    callTracker.clearAll();
    Log.d(TAG, "已清空所有追踪的 tool_call_id");
  }

  // 🔥 新增：清理单个 toolId
  public void clearTrackedCall(String toolId)
  {
    callTracker.clearRepliedCallId(toolId);
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

  // 🔥 新增：获取追踪器（用于测试）
  public ToolCallTracker getCallTracker()
  {
    return callTracker;
  }
}
