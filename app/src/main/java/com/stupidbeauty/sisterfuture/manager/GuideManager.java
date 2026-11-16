package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;



/**
 * 向导管理器 - 专门管理引导流程的核心协调者
 **/
public class GuideManager { 
    private final ModelAccessPointManager modelAccessPointManager; 
    private final ToolManager toolManager; 
    private final Context context;

    // 百炼标准接入点配置 (qwen3.7-plus MOE 稀疏模型，主人已确认是 3.7 中唯一公开的 MOE)
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String DASHSCOPE_ENDPOINT = "/compatible-mode/v1/chat/completions";
    private static final String DASHSCOPE_MODEL = "qwen3.7-plus"; // 🔥 已升级：从 qwen3.5-397b-a17b 升级到 qwen3.7-plus

    // Code Plan 接入点配置
    private static final String CODEPLAN_BASE_URL = "https://coding.dashscope.aliyuncs.com/v1";
    private static final String CODEPLAN_ENDPOINT = "/chat/completions";
    private static final String CODEPLAN_MODEL = "qwen3.7-plus"; // 🔥 已升级：从 qwen3.5-plus 升级到 qwen3.7-plus

    // 🔥 MiniMax 接入点配置
    private static final String MINIMAX_BASE_URL = "https://api.minimaxi.com/v1";
    private static final String MINIMAX_ENDPOINT = "/chat/completions";
    private static final String MINIMAX_MODEL = "MiniMax-M3"; // 🔥 已升级：从 MiniMax-M2.5 升级到 MiniMax-M3

    // 🔥 #940963136747 JSON 接入点配置相关常量
    // JSON 配置中 apiKey 的最小长度校验（第三方服务的 key 前缀五花八门，不套用纯文本那套白名单）
    private static final int JSON_API_KEY_MIN_LENGTH = 8;

    public GuideManager(Context context, ModelAccessPointManager modelAccessPointManager, ToolManager toolManager) { 
        this.context = context; 
        this.modelAccessPointManager = modelAccessPointManager; 
        this.toolManager = toolManager; 
    }

    /**
     * 检查当前接入点列表是否为空（MVP 核心逻辑）
     * @return true 如果列表为空
     **/
    public boolean isEmptyAccessPointList() {
        return modelAccessPointManager.getAllAccessPoints().isEmpty();
    }

    /**
     * 验证 API Key 格式：支持百炼标准密钥 (sk-)、Code Plan 密钥 (cp_/plan_/sf_等) 和 MiniMax 密钥
     * 
     * ✅ 支持格式：
     * - 百炼标准：sk- 开头，长度 20-64 字符
     * - Code Plan：cp_/plan_/sf_ 等前缀，长度 20-64 字符
     * - MiniMax: 任意格式，长度 20-64 字符
     * 
     * @param input 用户输入内容
     * @return true 如果是有效的 API Key
     **/
    public boolean isValidApiKey(String input) 
    {
      if (input == null || input.isEmpty()) 
      {
        return false;
      }
      
      int length = input.length();
      
      // 长度校验：20-64 字符
      if (length < 20 || length > 164) 
      {
        return false;
      }
      
      // 百炼标准密钥：sk- 开头
      if (input.startsWith("sk-")) 
      {
        return true;
      }
      
      // Code Plan 密钥：cp_/plan_/sf_ 等前缀
      if (input.startsWith("cp_") || input.startsWith("plan_") || input.startsWith("sf_")) 
      {
        return true;
      }
      
      // 🔥 MiniMax 密钥：任意格式，只要长度符合即可
      return true;
    }

    /**
     * 🔥 #940963136747 判断用户输入是否为 JSON 格式的接入点配置
     * 
     * 与纯文本 API Key 的区分采用双重判定，避免误判：
     * 1. trim 后必须以 "{" 开头（纯文本 key 一定不是）
     * 2. 必须能解析为 JSON，且含 apiKey 或 accessPoints 字段
     * 
     * ⚠️ 注意：isValidApiKey() 的兜底分支会放行任意 20-164 字符的字符串，
     * 一段 JSON 文本同样会被它判为"合法 key"。因此调用方必须先做本判断，
     * 再走纯文本分支，否则 JSON 会被误送到"创建三个默认接入点"的路径。
     * 
     * @param input 用户输入内容
     * @return true 如果是 JSON 格式的接入点配置
     **/
    public static boolean isJsonConfig(String input)
    {
      if (input == null)
      {
        return false;
      }
      
      String trimmed = input.trim();
      if (!trimmed.startsWith("{"))
      {
        return false;
      }
      
      try
      {
        JSONObject obj = new JSONObject(trimmed);
        return obj.has("apiKey") || obj.has("accessPoints");
      }
      catch (JSONException e)
      {
        return false;
      }
    }

    /**
     * 🔥 #940963136747 解析 JSON 格式的接入点配置
     * 
     * 支持两种格式：
     * 格式 A（单接入点）：
     *   {"apiKey":"...","baseUrl":"...","endpoint":"...","modelName":"...","name":"..."}
     * 格式 B（多接入点）：
     *   {"accessPoints":[{...},{...}]}
     * 
     * 必填字段：apiKey / baseUrl / endpoint / modelName
     * 可选字段：name（缺省时用 modelName 兜底）
     * 
     * @param input 用户输入的 JSON 文本
     * @return 解析出的接入点参数列表，每个元素可直接作为 addModelAccessPoint 工具的参数
     * @throws JSONException 解析或校验失败时抛出，异常信息中指出具体问题字段
     **/
    public static List<JSONObject> parseAccessPointConfig(String input) throws JSONException
    {
      List<JSONObject> result = new ArrayList<JSONObject>();
      
      if (input == null)
      {
        throw new JSONException("配置内容为空");
      }
      
      String trimmed = input.trim();
      
      JSONObject root;
      try
      {
        root = new JSONObject(trimmed);
      }
      catch (JSONException e)
      {
        throw new JSONException("JSON 格式错误，无法解析：" + e.getMessage());
      }
      
      if (root.has("accessPoints"))
      {
        JSONArray array = root.optJSONArray("accessPoints");
        if (array == null || array.length() == 0)
        {
          throw new JSONException("accessPoints 数组为空，请至少配置一个接入点");
        }
        
        for (int i = 0; i < array.length(); i++)
        {
          JSONObject item = array.optJSONObject(i);
          if (item == null)
          {
            throw new JSONException("accessPoints 第 " + (i + 1) + " 项不是合法的 JSON 对象");
          }
          result.add(validateAccessPoint(item, "accessPoints 第 " + (i + 1) + " 项"));
        }
      }
      else
      {
        result.add(validateAccessPoint(root, "接入点配置"));
      }
      
      return result;
    }

    /**
     * 🔥 #940963136747 校验单个接入点配置，返回补全后的参数对象
     * 
     * @param item 原始配置对象
     * @param location 位置描述（用于错误信息）
     * @return 补全 name 之后的参数对象
     * @throws JSONException 校验失败时抛出
     **/
    private static JSONObject validateAccessPoint(JSONObject item, String location) throws JSONException
    {
      String apiKey = item.optString("apiKey", "").trim();
      if (apiKey.isEmpty())
      {
        throw new JSONException(location + "缺少必填字段 apiKey");
      }
      if (apiKey.length() < JSON_API_KEY_MIN_LENGTH)
      {
        throw new JSONException(location + "的 apiKey 太短（至少需要 " + JSON_API_KEY_MIN_LENGTH + " 个字符）");
      }
      
      String baseUrl = item.optString("baseUrl", "").trim();
      if (baseUrl.isEmpty())
      {
        throw new JSONException(location + "缺少必填字段 baseUrl");
      }
      if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))
      {
        throw new JSONException(location + "的 baseUrl 不是合法 URL（应以 http:// 或 https:// 开头）：" + baseUrl);
      }
      String host = baseUrl.substring(baseUrl.indexOf("://") + 3);
      int slashIndex = host.indexOf('/');
      if (slashIndex >= 0)
      {
        host = host.substring(0, slashIndex);
      }
      if (host.isEmpty())
      {
        throw new JSONException(location + "的 baseUrl 缺少主机名：" + baseUrl);
      }
      
      String endpoint = item.optString("endpoint", "").trim();
      if (endpoint.isEmpty())
      {
        throw new JSONException(location + "缺少必填字段 endpoint");
      }
      
      String modelName = item.optString("modelName", "").trim();
      if (modelName.isEmpty())
      {
        throw new JSONException(location + "缺少必填字段 modelName");
      }
      
      // name 可选，缺省时用 modelName 兜底
      String name = item.optString("name", "").trim();
      if (name.isEmpty())
      {
        name = modelName;
      }
      
      JSONObject normalized = new JSONObject();
      try
      {
        normalized.put("apiKey", apiKey);
        normalized.put("baseUrl", baseUrl);
        normalized.put("endpoint", endpoint);
        normalized.put("modelName", modelName);
        normalized.put("name", name);
      }
      catch (JSONException e)
      {
        throw new JSONException(location + "字段处理失败：" + e.getMessage());
      }
      
      return normalized;
    }

    /**
     * 处理空状态下的聊天输入逻辑（MVP）
     * @param userInput 用户输入
     * @param callback 回调接口，用于返回 AI 回复或执行工具调用
     **/
    public void processWithGuideLogic(String userInput, ChatCallback callback) {
        if (isEmptyAccessPointList()) {
            // 🔥 #940963136747 先判断是否为 JSON 配置（必须放在 isValidApiKey 之前，
            // 因为 isValidApiKey 的兜底分支会放行 JSON 文本）
            if (isJsonConfig(userInput)) {
                handleJsonConfigInput(userInput, callback, "");
            } else if (isValidApiKey(userInput)) {
                // ✅ 新逻辑：自动创建三个接入点（普通模式，无后缀）
                createAccessPoints(userInput, callback, "");
            } else {
                // ❌ 无效密钥，提示获取方式并显示实际长度
                int actualLength = userInput.length();
                callback.onResponse(
                    "👋 你好！我是未来姐姐～\n\n" +
                    "目前尚未配置任何模型接入点。\n\n" +
                    "💡 **两种配置方式，任选其一**：\n\n" +
                    "**① 纯文本 API Key**（自动配置百炼 / CodePlan / MiniMax 三个接入点）\n" +
                    "1️⃣ 访问阿里云百炼 (https://dashscope.aliyun.com) 或 MiniMax 平台\n" +
                    "2️⃣ 申请 API Key\n" +
                    "3️⃣ 将密钥（长度 20-64 字符）粘贴到这里\n\n" +
                    "**② JSON 配置**（接入任意第三方 API 或你自己的算力中枢）\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"apiKey\": \"sk-你的密钥\",\n" +
                    "  \"baseUrl\": \"https://api.deepseek.com\",\n" +
                    "  \"endpoint\": \"/chat/completions\",\n" +
                    "  \"modelName\": \"deepseek-chat\"\n" +
                    "}\n" +
                    "```\n" +
                    "多个接入点时用 accessPoints 数组：\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"accessPoints\": [\n" +
                    "    { \"name\": \"我的算力中枢\", \"apiKey\": \"sk-xxx\", \"baseUrl\": \"https://my-hub.example.com\", \"endpoint\": \"/v1/chat/completions\", \"modelName\": \"qwen3.7-plus\" }\n" +
                    "  ]\n" +
                    "}\n" +
                    "```\n\n" +
                    "📌 必填字段：apiKey / baseUrl / endpoint / modelName\n" +
                    "📌 可选字段：name（不填则用 modelName）\n\n" +
                    "📝 您输入的密钥长度：" + actualLength + " 字符 (有效范围：20-64)\n\n" +
                    "准备好了吗？✨"
                );
            }
        }
    }

    /**
     * 🔥 #940963136747 处理 JSON 格式的接入点配置输入
     * 
     * @param userInput 用户输入的 JSON 文本
     * @param callback 回调接口
     * @param nameSuffix 名称后缀（普通模式=""，救援模式="-备用"）
     **/
    private void handleJsonConfigInput(String userInput, ChatCallback callback, String nameSuffix)
    {
        List<JSONObject> points;
        try
        {
            points = parseAccessPointConfig(userInput);
        }
        catch (JSONException e)
        {
            callback.onResponse(
                "❌ **配置格式有误**\n\n" +
                "🔍 问题：" + e.getMessage() + "\n\n" +
                "📋 **正确格式示例**：\n\n" +
                "单接入点：\n" +
                "```json\n" +
                "{\n" +
                "  \"apiKey\": \"sk-你的密钥\",\n" +
                "  \"baseUrl\": \"https://api.deepseek.com\",\n" +
                "  \"endpoint\": \"/chat/completions\",\n" +
                "  \"modelName\": \"deepseek-chat\",\n" +
                "  \"name\": \"DeepSeek 官方\"\n" +
                "}\n" +
                "```\n\n" +
                "多接入点（用 accessPoints 数组）：\n" +
                "```json\n" +
                "{\n" +
                "  \"accessPoints\": [\n" +
                "    { \"name\": \"我的算力中枢\", \"apiKey\": \"sk-xxx\", \"baseUrl\": \"https://my-hub.example.com\", \"endpoint\": \"/v1/chat/completions\", \"modelName\": \"qwen3.7-plus\" },\n" +
                "    { \"name\": \"DeepSeek 兜底\", \"apiKey\": \"sk-yyy\", \"baseUrl\": \"https://api.deepseek.com\", \"endpoint\": \"/chat/completions\", \"modelName\": \"deepseek-chat\" }\n" +
                "  ]\n" +
                "}\n" +
                "```\n\n" +
                "📌 必填字段：apiKey / baseUrl / endpoint / modelName\n" +
                "📌 可选字段：name（不填则用 modelName）\n\n" +
                "请修正后重新发送："
            );
            return;
        }
        
        createCustomAccessPoints(points, callback, nameSuffix);
    }

    /**
     * 🔥 #4657 在接入点死循环时触发添加新接入点的向导
     * 不删除现有接入点，只是引导用户添加新的备用接入点
     * @param callback 回调接口
     **/
    public void showAddAccessPointGuideForDeadlock(ChatCallback callback) {
        int existingCount = modelAccessPointManager.getAllAccessPoints().size();
        
        callback.onResponse(
            "⚠️ **检测到所有接入点连续失败！**\n\n" +
            "当前已配置的 " + existingCount + " 个接入点可能暂时不可用（例如：欠费、云端算力不足等）。\n\n" +
            "💡 **建议操作**：\n" +
            "1️⃣ 输入新的 API Key 添加备用接入点\n" +
            "2️⃣ 系统会在新旧接入点间自动切换\n" +
            "3️⃣ 原有接入点保留，恢复后可继续使用\n\n" +
            "📝 **请直接粘贴新的 API Key**（长度 20-64 字符）：\n\n" +
            "💡 也可以粘贴 JSON 配置来自定义接入点（DeepSeek、Kimi、自己的算力中枢等）：\n\n" +
            "✨ 准备好了吗？"
        );
    }

    /**
     * 🔥 #4657 处理死循环救援时的 API Key 输入
     * @param apiKey 用户输入的新 API Key
     * @param callback 回调接口
     **/
    public void handleDeadlockRescueApiKey(String apiKey, ChatCallback callback) {
        // 🔥 #940963136747 先判断是否为 JSON 配置（必须放在 isValidApiKey 之前）
        if (isJsonConfig(apiKey)) {
            handleJsonConfigInput(apiKey, callback, "-备用");
        } else if (isValidApiKey(apiKey)) {
            // 创建新的备用接入点（带"-备用"后缀）
            createAccessPoints(apiKey, callback, "-备用");
        } else {
            callback.onResponse(
                "❌ **无效的 API Key 格式**\n\n" +
                "📝 您输入的密钥长度：" + apiKey.length() + " 字符 (有效范围：20-64)\n\n" +
                "✅ **有效格式**：\n" +
                "- 百炼标准：`sk-` 开头\n" +
                "- Code Plan：`cp_` / `plan_` / `sf_` 开头\n" +
                "- MiniMax: 任意格式\n\n" +
                "请重新输入正确的 API Key："
            );
        }
    }

    /**
     * 🔥 #940963136747 创建自定义接入点（JSON 配置路径）
     * 
     * 复用现有的 addModelAccessPoint 工具链路，不另写一套创建逻辑。
     * - JSON 模式下不追加百炼/CodePlan/MiniMax 三个默认接入点（用户指定什么就用什么）
     * - 单项失败不中断其余项，最后输出成功/失败清单
     * - 不做最小请求验证（避免网络往返拖慢向导），创建成功即提示用户可直接试聊
     * 
     * @param points 已解析校验通过的接入点参数列表
     * @param callback 回调接口
     * @param nameSuffix 名称后缀（普通模式=""，救援模式="-备用"）
     **/
    private void createCustomAccessPoints(List<JSONObject> points, ChatCallback callback, String nameSuffix)
    {
      try
      {
        if (toolManager.getTool("addModelAccessPoint") == null)
        {
          callback.onError("❌ 工具未找到：addModelAccessPoint");
          return;
        }
        
        final int existingCount = modelAccessPointManager.getAllAccessPoints().size();
        
        final List<String> succeeded = new ArrayList<String>();
        final List<String> failed = new ArrayList<String>();
        
        // 逐个创建：串行执行，避免并发写持久化文件
        createCustomAccessPointAt(points, 0, nameSuffix, succeeded, failed, new Runnable()
        {
          @Override
          public void run()
          {
            StringBuilder message = new StringBuilder();
            if (failed.isEmpty())
            {
              message.append("✅ **自定义接入点配置成功！**\n\n");
              message.append("🔹 已添加 ").append(succeeded.size()).append(" 个接入点：\n");
              for (String name : succeeded)
              {
                message.append("  - ").append(name).append("\n");
              }
              message.append("\n📊 当前共有 ").append(existingCount + succeeded.size()).append(" 个接入点\n");
              message.append("🚀 现在可以直接开始对话了！若某个接入点连不上，可在接入点列表中切换到其他可用项。");
            }
            else
            {
              message.append("⚠️ **部分配置成功**\n\n");
              if (!succeeded.isEmpty())
              {
                message.append("✅ 已成功 ").append(succeeded.size()).append(" 个：\n");
                for (String name : succeeded)
                {
                  message.append("  - ").append(name).append("\n");
                }
              }
              message.append("❌ 失败 ").append(failed.size()).append(" 个：\n");
              for (String item : failed)
              {
                message.append("  - ").append(item).append("\n");
              }
              message.append("\n仍可正常使用已创建的接入点。");
            }
            callback.onResponse(message.toString());
          }
        });
        
        callback.onResponse("🔧 正在配置 " + points.size() + " 个自定义接入点，请稍候...");
      }
      catch (Exception e)
      {
        callback.onError("❌ 处理过程中发生错误：" + e.getMessage());
      }
    }

    /**
     * 🔥 #940963136747 递归创建自定义接入点（串行，逐个等待上一个完成）
     * 
     * @param points 接入点参数列表
     * @param index 当前处理到的索引
     * @param nameSuffix 名称后缀
     * @param succeeded 成功名单
     * @param failed 失败名单
     * @param onComplete 全部处理完毕后的回调
     **/
    private void createCustomAccessPointAt(final List<JSONObject> points, final int index, final String nameSuffix,
                                           final List<String> succeeded, final List<String> failed, final Runnable onComplete)
    {
      if (index >= points.size())
      {
        onComplete.run();
        return;
      }
      
      JSONObject point = points.get(index);
      JSONObject args = new JSONObject();
      try
      {
        args.put("apiKey", point.getString("apiKey"));
        args.put("name", point.getString("name") + nameSuffix);
        args.put("baseUrl", point.getString("baseUrl"));
        args.put("endpoint", point.getString("endpoint"));
        args.put("modelName", point.getString("modelName"));
      }
      catch (JSONException e)
      {
        failed.add(point.optString("name", "第 " + (index + 1) + " 项") + "（参数处理失败：" + e.getMessage() + "）");
        createCustomAccessPointAt(points, index + 1, nameSuffix, succeeded, failed, onComplete);
        return;
      }
      
      final String displayName = point.optString("name", "第 " + (index + 1) + " 项");
      
      // 🔥 #4790 修改：添加 null 作为 toolCallId 参数（因为这不是来自 LLM 的工具调用）
      toolManager.executeToolAsync(null, "addModelAccessPoint", args, new Tool.OnResultCallback()
      {
        @Override
        public void onResult(JSONObject result)
        {
          succeeded.add(displayName + nameSuffix);
          createCustomAccessPointAt(points, index + 1, nameSuffix, succeeded, failed, onComplete);
        }

        @Override
        public void onError(Exception e)
        {
          failed.add(displayName + nameSuffix + "（" + e.getMessage() + "）");
          createCustomAccessPointAt(points, index + 1, nameSuffix, succeeded, failed, onComplete);
        }
      });
    }

    /**
     * 统一方法：创建接入点（支持普通模式/备用模式）
     * 
     * @param apiKey API Key
     * @param callback 回调接口
     * @param nameSuffix 名称后缀（普通模式=""，救援模式="-备用"）
     **/
    private void createAccessPoints(String apiKey, ChatCallback callback, String nameSuffix) {
        try {
            AddModelAccessPointTool addTool = (AddModelAccessPointTool) toolManager.getTool("addModelAccessPoint");
            if (addTool == null) {
                callback.onError("❌ 工具未找到：addModelAccessPoint");
                return;
            }

            // 1. 创建百炼标准接入点 (qwen3.7-plus MOE 稀疏模型)
            JSONObject args1 = new JSONObject();
            args1.put("apiKey", apiKey);
            args1.put("name", "Qwen-百炼标准-3.7-plus" + nameSuffix);
            args1.put("baseUrl", DASHSCOPE_BASE_URL);
            args1.put("endpoint", DASHSCOPE_ENDPOINT);
            args1.put("modelName", DASHSCOPE_MODEL);

            // 2. 创建 Code Plan 接入点 (qwen3.7-plus)
            JSONObject args2 = new JSONObject();
            args2.put("apiKey", apiKey);
            args2.put("name", "Qwen-CodePlan-3.7-plus" + nameSuffix);
            args2.put("baseUrl", CODEPLAN_BASE_URL);
            args2.put("endpoint", CODEPLAN_ENDPOINT);
            args2.put("modelName", CODEPLAN_MODEL);

            // 🔥 3. 创建 MiniMax 接入点 (MiniMax-M3)
            JSONObject args3 = new JSONObject();
            args3.put("apiKey", apiKey);
            args3.put("name", "MiniMax-M3" + nameSuffix);
            args3.put("baseUrl", MINIMAX_BASE_URL);
            args3.put("endpoint", MINIMAX_ENDPOINT);
            args3.put("modelName", MINIMAX_MODEL);

            int existingCount = modelAccessPointManager.getAllAccessPoints().size();
            boolean isBackupMode = !nameSuffix.isEmpty();

            // 异步执行：先创建百炼接入点
            // 🔥 #4790 修改：添加 null 作为 toolCallId 参数（因为这不是来自 LLM 的工具调用）
            toolManager.executeToolAsync(null, "addModelAccessPoint", args1, new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result1) {
                    // 百炼接入点创建成功，继续创建 Code Plan 接入点
                    toolManager.executeToolAsync(null, "addModelAccessPoint", args2, new Tool.OnResultCallback() {
                        @Override
                        public void onResult(JSONObject result2) {
                            // Code Plan 创建成功，继续创建 MiniMax 接入点
                            toolManager.executeToolAsync(null, "addModelAccessPoint", args3, new Tool.OnResultCallback() {
                                @Override
                                public void onResult(JSONObject result3) {
                                    // ✅ 三个接入点都创建成功
                                    if (isBackupMode) {
                                        // 备用模式
                                        callback.onResponse(
                                            "✅ **备用接入点配置成功！**\n\n" +
                                            "🔹 已添加三个新接入点：\n" +
                                            "  1. Qwen-百炼标准-3.7-plus" + nameSuffix + "\n" +
                                            "  2. Qwen-CodePlan-3.7-plus" + nameSuffix + "\n" +
                                            "  3. MiniMax-M3" + nameSuffix + "\n\n" +
                                            "📊 当前共有 " + (existingCount + 3) + " 个接入点\n" +
                                            "🚀 系统会自动在新旧接入点间切换，优先使用可用的接入点\n\n" +
                                            "💡 原有接入点已保留，恢复后可继续使用！"
                                        );
                                    } else {
                                        // 普通模式（首次配置）
                                        callback.onResponse(
                                            "✅ 接入点配置成功！\n\n" +
                                            "🔹 已创建三个接入点：\n" +
                                            "  1. Qwen-百炼标准-3.7-plus\n" +
                                            "  2. Qwen-CodePlan-3.7-plus\n" +
                                            "  3. MiniMax-M3\n\n" +
                                            "🚀 系统会自动使用有效的接入点，现在可以享受完整功能了！"
                                        );
                                    }
                                }

                                @Override
                                public void onError(Exception e) {
                                    // MiniMax 创建失败，但前两个已成功
                                    if (isBackupMode) {
                                        callback.onResponse(
                                            "⚠️ 部分配置成功：\n" +
                                            "✅ Qwen-百炼标准-3.7-plus" + nameSuffix + " 已创建\n" +
                                            "✅ Qwen-CodePlan-3.7-plus" + nameSuffix + " 已创建\n" +
                                            "❌ MiniMax-M3" + nameSuffix + " 配置失败：" + e.getMessage() + "\n\n" +
                                            "仍可正常使用已创建的两个接入点。"
                                        );
                                    } else {
                                        callback.onResponse(
                                            "⚠️ 部分配置成功：\n" +
                                            "✅ Qwen-百炼标准-3.7-plus 已创建\n" +
                                            "✅ Qwen-CodePlan-3.7-plus 已创建\n" +
                                            "❌ MiniMax-M3 配置失败：" + e.getMessage() + "\n\n" +
                                            "仍可正常使用已创建的两个接入点。"
                                        );
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            // Code Plan 创建失败，但百炼已成功
                            if (isBackupMode) {
                                callback.onResponse(
                                    "⚠️ 部分配置成功：\n" +
                                    "✅ Qwen-百炼标准-3.7-plus" + nameSuffix + " 已创建\n" +
                                    "❌ Qwen-CodePlan-3.7-plus" + nameSuffix + " 配置失败：" + e.getMessage() + "\n\n" +
                                    "仍可正常使用百炼接入点。"
                                );
                            } else {
                                callback.onResponse(
                                    "⚠️ 部分配置成功：\n" +
                                    "✅ Qwen-百炼标准-3.7-plus 已创建\n" +
                                    "❌ Qwen-CodePlan-3.7-plus 配置失败：" + e.getMessage() + "\n\n" +
                                    "仍可正常使用百炼接入点。"
                                );
                            }
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    // 百炼接入点创建失败
                    callback.onError("❌ " + (isBackupMode ? "备用" : "百炼") + "接入点配置失败：" + e.getMessage());
                }
            });

            callback.onResponse("🔧 正在配置" + (isBackupMode ? "备用" : "三") + "接入点，请稍候...");

        } catch (Exception e) {
            callback.onError("❌ 处理过程中发生错误：" + e.getMessage());
        }
    }

    /**
     * 内部接口：供外部调用时返回响应
     **/
    public interface ChatCallback {
        void onResponse(String message);
        void onError(String error);
    }
}
