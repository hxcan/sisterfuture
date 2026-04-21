package com.stupidbeauty.sisterfuture.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

/**
 * 🔥 #761200615112 工具参数历史记录管理器
 */
public class ToolManager
{
  private static final String TAG = "ToolManager";
  private Map<String, Tool> toolRegistry = new HashMap<>();
  private ToolCallTracker callTracker = new ToolCallTracker();  // 🔥 幂等性追踪器
  private ToolParameterHistory parameterHistory = new ToolParameterHistory();  // 🔥 参数历史记录

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

  // 🔥 修改：移除入口处的幂等检查，改为在回复时检查
  // 原因：工具可能回调多次（onResult + onError），入口检查无法阻止重复回复
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

    // 🔥 不再在这里做幂等检查，改为在 postProcessToolResults 中回复前检查

    if (!tool.isAsync())
    {
      // 同步工具包装成异步返回
      try
      {
        JSONObject result = executeTool(toolName, arguments);
        
        // 🔥 #761200615112 记录成功调用的参数
        recordToolSuccess(toolName, arguments);
        
        callback.onResult(result);
      }
      catch (IllegalArgumentException e)
      {
        // 🔥 #761200615112 捕获参数缺失错误，生成智能引导
        Log.e(TAG, "同步工具参数错误：" + e.getMessage(), e);
        
        try
        {
          String missingParam = extractMissingParamName(e.getMessage());
          Log.d(TAG, ">>> [SYNC] 提取到缺失参数: " + missingParam);
          
          String guide = parameterHistory.generateGuideMessage(toolName, missingParam);
          Log.d(TAG, ">>> [SYNC] 生成的引导信息: " + (guide != null ? guide.substring(0, Math.min(100, guide.length())) + "..." : "null");
          
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
          
          error.put("type", "IllegalArgumentException");
          callback.onResult(error); // 返回友好错误，而不是抛出异常
        }
        catch (Exception ex)
        {
          Log.w(TAG, "生成引导信息失败，返回原始错误", ex);
          callback.onError(e); // 如果生成引导失败，返回原始错误
        }
      }
      catch (Exception e)
      {
        callback.onError(e);
      }
    }
    else
    {
      // 异步工具直接调用（在回调中记录）
      tool.executeAsync(arguments, new Tool.OnResultCallback()
      {
        @Override
        public void onResult(JSONObject result)
        {
          // 🔥 #761200615112 记录成功调用的参数
          recordToolSuccess(toolName, arguments);
          callback.onResult(result);
        }

        @Override
        public void onError(Exception e)
        {
          // 🔥 #761200615112 为异步工具也添加参数缺失智能引导 + 调试日志
          Log.e(TAG, ">>> [ASYNC] 异步工具出错！tool=" + toolName + ", error=" + e.getMessage(), e);
          Log.d(TAG, ">>> [ASYNC] 错误类型: " + e.getClass().getName());
          Log.d(TAG, ">>> [ASYNC] 是否为 IllegalArgumentException: " + (e instanceof IllegalArgumentException));
          
          if (e instanceof IllegalArgumentException)
          {
            Log.d(TAG, ">>> [ASYNC] 进入智能引导处理流程...");
            
            try
            {
              String missingParam = extractMissingParamName(e.getMessage());
              Log.d(TAG, ">>> [ASYNC] 提取到缺失参数: " + missingParam);
              
              String guide = parameterHistory.generateGuideMessage(toolName, missingParam);
              Log.d(TAG, ">>> [ASYNC] 生成的引导信息: " + (guide != null ? guide.substring(0, Math.min(100, guide.length())) + "..." : "null"));
              
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
              
              error.put("type", "IllegalArgumentException");
              Log.d(TAG, ">>> [ASYNC] 准备返回智能引导错误...");
              callback.onResult(error); // 返回友好错误，而不是抛出异常
              Log.d(TAG, ">>> [ASYNC] 已返回智能引导错误！");
              return;
            }
            catch (Exception ex)
            {
              Log.w(TAG, ">>> [ASYNC] 生成引导信息失败，准备返回原始错误", ex);
            }
          }
          
          // 如果不是 IllegalArgumentException 或生成引导失败，返回原始错误
          Log.d(TAG, ">>> [ASYNC] 返回原始错误: " + e.getMessage());
          callback.onError(e);
        }
      });
    }
  }

  // 🔥 新增：在回复前检查是否已回复过（正确的时机！）
  public boolean tryMarkToolCallAsReplied(String toolCallId)
  {
    return callTracker.tryMarkAsReplied(toolCallId);
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

  // 🔥 #761200615112 新增：获取参数历史记录管理器
  public ToolParameterHistory getParameterHistory()
  {
    return parameterHistory;
  }

  // 🔥 #761200615112 新增：记录成功调用的参数
  public void recordToolSuccess(String toolName, JSONObject arguments)
  {
    Log.d(TAG, ">>> [RECORD] 记录工具成功调用: tool=" + toolName + ", args=" + arguments);
    parameterHistory.recordSuccess(toolName, arguments);
  }

  // 🔥 #761200615112 新增：从错误信息中提取缺失的参数名（通用方法）
  private String extractMissingParamName(String message)
  {
    if (message == null)
    {
      return null;
    }
    
    // 匹配 "No value for [paramName]" 格式（org.json 抛出的异常）
    if (message.contains("No value for "))
    {
      int start = message.indexOf("No value for ") + "No value for ".length();
      int end = message.indexOf("'", start);
      if (end == -1)
      {
        end = message.length();
      }
      String param = message.substring(start, end).trim().replace("'", "");
      Log.d(TAG, ">>> [EXTRACT] 匹配到 'No value for' 格式, 参数名: " + param);
      return param;
    }
    
    // 匹配 "Missing required parameter: [paramName]" 格式
    if (message.contains("Missing required parameter"))
    {
      int start = message.indexOf(": ") + 2;
      String param = message.substring(start).trim();
      Log.d(TAG, ">>> [EXTRACT] 匹配到 'Missing required parameter' 格式, 参数名: " + param);
      return param;
    }
    
    // 匹配 "Required parameter '[paramName]' is missing" 格式
    if (message.contains("Required parameter") && message.contains("is missing"))
    {
      int start = message.indexOf("'") + 1;
      int end = message.indexOf("'", start);
      if (start > 0 && end > start)
      {
        String param = message.substring(start, end).trim();
        Log.d(TAG, ">>> [EXTRACT] 匹配到 'Required parameter ... is missing' 格式, 参数名: " + param);
        return param;
      }
    }
    
    Log.d(TAG, ">>> [EXTRACT] 未匹配到任何已知格式, message=" + message);
    return null;
  }
}