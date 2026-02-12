// AddModelAccessPointTool.java (最终版)
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class AddModelAccessPointTool implements Tool 
{
  private static final String TAG = "AddModelAccessPointTool";
  private final Context context;
  private final ModelAccessPointManager modelAccessPointManager;

  // 阿里云百炼平台OpenAI兼容接口的默认配置
  private static final String ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com";
  private static final String ALIYUN_ENDPOINT = "/compatible-mode/v1/chat/completions";
  private static final String DEFAULT_MODEL_NAME = "qwen3-30b-a3b-instruct-2507";

  public AddModelAccessPointTool(ModelAccessPointManager modelAccessPointManager, Context context) 
  {
    this.context = context;
    this.modelAccessPointManager = modelAccessPointManager;
  }

  @Override
  public String getName() 
  {
    return "add_model_access_point";
  }

  @Override
  public JSONObject getDefinition() 
  {
    try 
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "add_model_access_point");
      functionDef.put("description", "动态添加新的模型接入点，支持智能默认值。仅API密钥为必填项，其余参数自动使用阿里云百炼平台OpenAI兼容接口的默认值。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      JSONObject properties = new JSONObject();
      properties.put("api_key", new JSONObject()
        .put("type", "string")
        .put("description", "API密钥，唯一必填参数"));
      properties.put("name", new JSONObject()
        .put("type", "string")
        .put("description", "接入点名称，可选，不填则自动生成"));
      properties.put("base_url", new JSONObject()
        .put("type", "string")
        .put("description", "基础URL，可选，不填则使用阿里云百炼默认值"));
      properties.put("endpoint", new JSONObject()
        .put("type", "string")
        .put("description", "API端点路径，可选，不填则使用阿里云百炼默认值"));
      properties.put("model_name", new JSONObject()
        .put("type", "string")
        .put("description", "模型名称，可选，不填则使用默认值 qwen3-30b-a3b-instruct-2507"));

      parameters.put("properties", properties);
      parameters.put("required", new JSONArray().put("api_key")); // 只有api_key是必填的

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
      // 唯一必填参数
      String apiKey = arguments.getString("api_key");
      
      // 智能默认值逻辑
      String name = arguments.optString("name", "Aliyun Qwen3-30B"); // 默认名称
      String baseUrl = arguments.optString("base_url", ALIYUN_BASE_URL); // 默认URL
      String endpoint = arguments.optString("endpoint", ALIYUN_ENDPOINT); // 默认端点
      String modelName = arguments.optString("model_name", DEFAULT_MODEL_NAME); // 默认模型名

      // 如果用户只提供了api_key，使用最简化的默认配置
      if (!arguments.has("name") && !arguments.has("base_url") && !arguments.has("endpoint") && !arguments.has("model_name")) {
        name = "Quick Access Point"; // 极简模式下的名称
      }

      modelAccessPointManager.addAccessPoint(name, baseUrl, endpoint, modelName);

      JSONObject result = new JSONObject();
      result.put("status", "success");
      result.put("message", "成功添加新接入点: " + name);
      result.put("access_point", new JSONObject()
        .put("name", name)
        .put("base_url", baseUrl)
        .put("chat_endpoint", endpoint)
        .put("model_name", modelName));
      result.put("note", "所有未提供的参数都已使用阿里云百炼平台的默认值填充");

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
    return "智能模型接入点添加工具。仅需提供api_key即可完成配置。" +
      "如果只提供api_key，则自动使用阿里云百炼平台的OpenAI兼容接口：" +
      "基础URL=https://dashscope.aliyuncs.com，端点=/compatible-mode/v1/chat/completions，" +
      "模型名=qwen3-30b-a3b-instruct-2507。" +
      "所有其他参数都是可选的，会自动填充默认值。" +
      "特别适用于快速配置阿里云服务。";
  }
}
