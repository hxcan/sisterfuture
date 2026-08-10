// RemoveMemoryTool.java
package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class RemoveMemoryTool implements Tool 
{
  private static final String TAG = "RemoveMemoryTool";
  private final Context context;
  private MemoryManager memoryManager;

  public RemoveMemoryTool(MemoryManager memoryManager, Context context) 
  {
    this.context = context;
    this.memoryManager = memoryManager;
  }

  @Override
  public String getName() 
  {
    return "removeMemory";
  }

  @Override
  public JSONObject getDefinition() 
  {
    try 
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "removeMemory");
      functionDef.put("description", "删除长期记忆条目。用于清理过期或错误的记忆，支持根据记忆的唯一键（key）进行删除。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      JSONObject properties = new JSONObject();
      properties.put("key", new JSONObject()
        .put("type", "string")
        .put("description", "要删除的记忆的唯一键，例如\"github_tool_credentials\""));
      properties.put("confirm", new JSONObject()
        .put("type", "boolean")
        .put("description", "是否确认删除，默认 false（需要二次确认）"));

      parameters.put("properties", properties);
      parameters.put("required", new JSONArray().put("key"));

      functionDef.put("parameters", parameters);
      return new JSONObject().put("type", "function").put("function", functionDef);
    }
    catch (Exception e) 
    {
      Log.e(TAG, "Failed to build definition", e);
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
    try 
    {
      String key = arguments.getString("key");
      boolean confirm = arguments.optBoolean("confirm", false);

      // 检查是否需要二次确认
      if (!confirm) 
      {
        JSONObject warning = new JSONObject();
        warning.put("status", "warning");
        warning.put("message", "删除记忆需要确认。请再次调用并设置 confirm=true 以确认删除 key=\"" + key + "\"");
        warning.put("key", key);
        return warning;
      }

      // 执行删除
      boolean deleted = memoryManager.removeMemory(key);

      JSONObject result = new JSONObject();
      if (deleted) 
      {
        result.put("status", "success");
        result.put("deleted_key", key);
        result.put("message", "记忆已成功删除");
      }
      else 
      {
        result.put("status", "error");
        result.put("message", "未找到 key 为 \"" + key + "\" 的记忆条目");
      }
      return result;

    }
    catch (Exception e) 
    {
      Log.e(TAG, "执行出错", e);
      JSONObject error = new JSONObject();
      error.put("status", "error");
      error.put("message", e.getMessage());
      return error;
    }
  }

  @Override
  public String getDefaultSystemPromptEnhancement() 
  {
    return "用于删除长期记忆条目。调用时需要提供记忆的唯一键（key）。" +
      "首次调用会返回警告要求确认，需要再次调用并设置 confirm=true 才能真正删除。" +
      "删除操作不可恢复，请谨慎使用。";
  }
}