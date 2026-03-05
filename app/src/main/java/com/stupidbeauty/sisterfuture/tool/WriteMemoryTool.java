// WriteMemoryTool.java
package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;
import com.stupidbeauty.sisterfuture.tool.SwitchAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentAccessPointInfoTool;
import com.stupidbeauty.sisterfuture.tool.DeveloperInfoTool;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class WriteMemoryTool implements Tool 
{
  private static final String TAG = "WriteMemoryTool";
  private final Context context;
  private MemoryManager memoryManager;

  public WriteMemoryTool(MemoryManager memoryManager, Context context) 
  {
    this.context = context;
    this.memoryManager = memoryManager;
  }

  @Override
  public String getName() 
  {
    return "write_memory";
  }

  @Override
  public JSONObject getDefinition() 
  {
    try 
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "write_memory");
      functionDef.put("description", "写入长期记忆。用于让未来姐姐将自认为重要的内容写入。主要是用户提供的一些估计会在日后有用处的信息，例如喜好，以及一些系统的访问参数，还有一些人际关系等。必要的情况下还需要结合已有的上下文对内容做点标注才会有意义。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      JSONObject properties = new JSONObject();
      properties.put("key", new JSONObject()
        .put("type", "string")
        .put("description", "记忆的唯一键，建议使用语义化命名，如\"user_preference\", \"system_access\", \"relationship\""));
      properties.put("content", new JSONObject()
        .put("type", "string")
        .put("description", "要存储的原始内容"));
      properties.put("tags", new JSONObject()
        .put("type", "array")
        .put("items", new JSONObject().put("type", "string"))
        .put("description", "用于分类和搜索的标签，例如[\"preference\", \"system\", \"relationship\"]"));

      parameters.put("properties", properties);
      parameters.put("required", new JSONArray().put("key").put("content"));

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
      String content = arguments.getString("content");
      JSONArray tagsArray = arguments.getJSONArray("tags");
      java.util.List<String> tags = new java.util.ArrayList<>();
      for (int i = 0; i < tagsArray.length(); i++) 
      {
        tags.add(tagsArray.getString(i));
      }

      // 智能处理：如果内容是偏好，自动添加"preference"标签
      if (content.toLowerCase().contains("like") || content.toLowerCase().contains("dislike") ||
        content.toLowerCase().contains("prefer") || content.toLowerCase().contains("hate")) 
        {
          if (!tags.contains("preference")) 
          {
            tags.add("preference");
          }
        }

      // 智能处理：如果内容是系统信息，自动添加"system"标签
      if (content.toLowerCase().contains("password") || content.toLowerCase().contains("token") ||
        content.toLowerCase().contains("access") || content.toLowerCase().contains("login")) {
          if (!tags.contains("system")) 
          {
            tags.add("system");
          }
        }

      // 智能处理：如果内容是人际关系，自动添加"relationship"标签
      if (content.toLowerCase().contains("friend") || content.toLowerCase().contains("family") ||
        content.toLowerCase().contains("colleague") || content.toLowerCase().contains("relationship")) 
        {
          if (!tags.contains("relationship")) 
          {
            tags.add("relationship");
          }
        }

      // 写入记忆
      memoryManager.saveMemory(key, content, tags);

      JSONObject result = new JSONObject();
      result.put("status", "success");
      result.put("message", "已成功写入长期记忆：key=" + key);
      result.put("tags", tags);
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
    return "用于写入长期记忆。会自动识别内容类型并添加相应标签，如偏好、系统信息、人际关系等。" +
      "请使用语义化命名的唯一键，避免使用过于宽泛的键如\"user_preference\"。" +
      "建议使用更详细的键，例如\"user_dislikes_kotlin\"或\"user_code_style_java\"。" +
      "如果用户多次提及同一主题，应使用不同的键来区分，如\"user_preference_kotlin_dislike\"。";
  }
}
