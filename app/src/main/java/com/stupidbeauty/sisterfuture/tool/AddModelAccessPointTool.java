// AddModelAccessPointTool.java (最终版 - apiKey 集成)
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;

public class AddModelAccessPointTool implements Tool 
{
  private static final String TAG = "AddModelAccessPointTool";
  private final Context context;
  private final ModelAccessPointManager modelAccessPointManager;

  // 阿里云百炼平台 OpenAI 兼容接口的默认配置
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
      functionDef.put("description", "动态添加新的模型接入点，支持智能默认值。仅 API 密钥为必填项，其余参数自动使用阿里云百炼平台 OpenAI 兼容接口的默认值。新增：apiKey 字段用于独立认证管理。");

      JSONObject parameters = new JSONObject();
      parameters.put("type", "object");

      JSONObject properties = new JSONObject();
      
      // apiKey 是必填字段
      properties.put("api_key", new JSONObject()
        .put("type", "string")
        .put("description", "API 密钥，唯一必填参数（用于 AccessPoint 独立认证）"));
        
      properties.put("name", new JSONObject()
        .put("type", "string")
        .put("description", "接入点名称，可选，不填则自动生成"));
      properties.put("base_url", new JSONObject()
        .put("type", "string")
        .put("description", "基础 URL，可选，不填则使用阿里云百炼默认值"));
      properties.put("endpoint", new JSONObject()
        .put("type", "string")
        .put("description", "API 端点路径，可选，不填则使用阿里云百炼默认值"));
      properties.put("model_name", new JSONObject()
        .put("type", "string")
        .put("description", "模型名称，可选，不填则使用默认值 qwen3-30b-a3b-instruct-2507"));

      parameters.put("properties", properties);
      parameters.put("required", new JSONArray().put("api_key")); // 只有 api_key 是必填的

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
      // ✅ 唯一必填参数
      String apiKey = arguments.getString("api_key");
      
      // ✅ 智能默认值逻辑
      String name = arguments.optString("name", "Aliyun Qwen3-30B"); // 默认名称
      String baseUrl = arguments.optString("base_url", ALIYUN_BASE_URL); // 默认 URL
      String endpoint = arguments.optString("endpoint", ALIYUN_ENDPOINT); // 默认端点
      String modelName = arguments.optString("model_name", DEFAULT_MODEL_NAME); // 默认模型名

      // 如果用户只提供了 api_key，使用最简化的默认配置
      if (!arguments.has("name") && !arguments.has("base_url") && !arguments.has("endpoint") && !arguments.has("model_name")) {
        name = "Quick Access Point"; // 极简模式下的名称
      }

      // ✅ 调用 Manager 时传入 apiKey（注意：这里需要 Manager 也支持接收 apiKey）
      // 由于 Manager 目前构造函数不支持 apiKey，我们手动创建并保存
      addAccessPointWithApiKey(name, baseUrl, endpoint, modelName, apiKey);

      JSONObject result = new JSONObject();
      result.put("status", "success");
      result.put("message", "成功添加新接入点：" + name);
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

  /**
   * 辅助方法：直接调用 Manager 的底层逻辑保存带 apiKey 的 AccessPoint
   */
  private void addAccessPointWithApiKey(String name, String baseUrl, String chatEndpoint, String modelName, String apiKey) {
    com.stupidbeauty.sisterfuture.manager.ModelAccessPoint newPoint = 
      new com.stupidbeauty.sisterfuture.manager.ModelAccessPoint(name, baseUrl, chatEndpoint, modelName, apiKey);
    
    modelAccessPointManager.addAccessPointInternal(newPoint);
    
    Log.i(TAG, "Added access point with apiKey: " + name);
  }

  @Override
  public String getDefaultSystemPromptEnhancement() 
  {
    return "智能模型接入点添加工具。仅需提供 api_key 即可完成配置。" +
      "如果只提供 api_key，则自动使用阿里云百炼平台的 OpenAI 兼容接口：" +
      "基础 URL=https://dashscope.aliyuncs.com，端点=/compatible-mode/v1/chat/completions，" +
      "模型名=qwen3-30b-a3b-instruct-2507。" +
      "所有其他参数都是可选的，会自动填充默认值。" +
      "特别适用于快速配置阿里云服务。" +
      "✅ 每个接入点现在都拥有独立的 apiKey 字段，实现更安全的认证管理！";
  }
}
