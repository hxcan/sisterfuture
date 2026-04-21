package com.stupidbeauty.sisterfuture.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import android.util.Log;

/**
 * 🔥 #761200615112 工具参数历史记录管理器
 */
public class ToolManager
{
  private static final String TAG = "ToolManager";
  private Map<String, Tool> toolRegistry = new HashMap<>();
  private ToolCallTracker callTracker = new ToolCallTracker();
  private ToolParameterHistory parameterHistory = new ToolParameterHistory();

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

    if (!tool.isAsync())
    {
      try
      {
        JSONObject result = executeTool(toolName, arguments);
        recordToolSuccess(toolName, arguments);
        callback.onResult(result);
      }
      catch (IllegalArgumentException e)
      {
        Log.e(TAG, "同步工具参数错误 (IllegalArgumentException)：" + e.getMessage(), e);
        handleParameterError(e, toolName, callback);
      }
      catch (JSONException e)
      {
        Log.e(TAG, "同步工具参数错误 (JSONException)：" + e.getMessage(), e);
        handleParameterError(e, toolName, callback);
      }
      catch (Exception e)
      {
        callback.onError(e);
      }
    }
    else
    {
      tool.executeAsync(arguments, new Tool.OnResultCallback()
      {
        @Override
        public void onResult(JSONObject result)
        {
          recordToolSuccess(toolName, arguments);
          callback.onResult(result);
        }

        @Override
        public void onError(Exception e)
        {
          Log.e(TAG, ">>> [ASYNC] 异步工具出错！tool=" + toolName + ", error=" + e.getMessage(), e);
          Log.d(TAG, ">>> [ASYNC] 错误类型：" + e.getClass().getName());
          
          if (e instanceof IllegalArgumentException || e instanceof JSONException)
          {
            Log.d(TAG, ">>> [ASYNC] 进入智能引导处理流程...");
            handleParameterError(e, toolName, callback);
          }
          else
          {
            Log.d(TAG, ">>> [ASYNC] 返回原始错误：" + e.getMessage());
            callback.onError(e);
          }
        }
      });
    }
  }

  private void handleParameterError(Exception e, String toolName, Tool.OnResultCallback callback)
  {
    try
    {
      String missingParam = extractMissingParamName(e.getMessage());
      Log.d(TAG, ">>> [HANDLE] 提取到缺失参数：" + missingParam);
      
      String guide = parameterHistory.generateGuideMessage(toolName, missingParam);
      String guidePreview = guide != null ? guide.substring(0, Math.min(100, guide.length())) + "..." : "null";
      Log.d(TAG, ">>> [HANDLE] 生成的引导信息：" + guidePreview);
      
      JSONObject error = new JSONObject();
      error.put("status", "error");
      
      if (missingParam != null)
      {
        error.put("message", guide);
        error.put("missing_parameter", missingParam);
      }
      else
      {
        error.put("message", e.getMessage());
      }
      
      error.put("type", e.getClass().getSimpleName());
      
      // 🔥 新增：统一添加参数历史候选值推荐
      try {
        JSONObject suggestedValues = parameterHistory.getSuggestedValues(toolName);
        if (suggestedValues != null && suggestedValues.length() > 0) {
          error.put("suggested_values", suggestedValues);
          Log.d(TAG, ">>> [HANDLE] 已添加历史参数值推荐：" + suggestedValues.toString());
        }
      } catch (Exception ex) {
        Log.w(TAG, "获取历史参数值失败", ex);
      }
      
      Log.d(TAG, ">>> [HANDLE] 准备返回智能引导错误...");
      callback.onResult(error);
      Log.d(TAG, ">>> [HANDLE] 已返回智能引导错误！");
    }
    catch (Exception ex)
    {
      Log.w(TAG, "生成引导信息失败，返回原始错误", ex);
      callback.onError(e);
    }
  }

  public boolean tryMarkToolCallAsReplied(String toolCallId)
  {
    return callTracker.tryMarkAsReplied(toolCallId);
  }

  public void clearTrackedCalls()
  {
    callTracker.clearAll();
    Log.d(TAG, "已清空所有追踪的 tool_call_id");
  }

  public void clearTrackedCall(String toolId)
  {
    callTracker.clearRepliedCallId(toolId);
  }

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

  public ToolCallTracker getCallTracker()
  {
    return callTracker;
  }

  public ToolParameterHistory getParameterHistory()
  {
    return parameterHistory;
  }

  public void recordToolSuccess(String toolName, JSONObject arguments)
  {
    Log.d(TAG, ">>> [RECORD] 记录工具成功调用：tool=" + toolName + ", args=" + arguments);
    parameterHistory.recordSuccess(toolName, arguments);
  }

  private String extractMissingParamName(String message)
  {
    if (message == null)
    {
      return null;
    }
    
    if (message.contains("No value for "))
    {
      int start = message.indexOf("No value for ") + "No value for ".length();
      int end = message.indexOf("'", start);
      if (end == -1)
      {
        end = message.length();
      }
      String param = message.substring(start, end).trim().replace("'", "");
      Log.d(TAG, ">>> [EXTRACT] 匹配到 'No value for' 格式，参数名：" + param);
      return param;
    }
    
    if (message.contains("Missing required parameter"))
    {
      int start = message.indexOf(": ") + 2;
      String param = message.substring(start).trim();
      Log.d(TAG, ">>> [EXTRACT] 匹配到 'Missing required parameter' 格式，参数名：" + param);
      return param;
    }
    
    if (message.contains("Required parameter") && message.contains("is missing"))
    {
      int start = message.indexOf("'") + 1;
      int end = message.indexOf("'", start);
      if (start > 0 && end > start)
      {
        String param = message.substring(start, end).trim();
        Log.d(TAG, ">>> [EXTRACT] 匹配到 'Required parameter ... is missing' 格式，参数名：" + param);
        return param;
      }
    }
    
    Log.d(TAG, ">>> [EXTRACT] 未匹配到任何已知格式，message=" + message);
    return null;
  }
}