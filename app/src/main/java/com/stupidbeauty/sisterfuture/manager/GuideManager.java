package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import org.json.JSONObject;



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
     * 处理空状态下的聊天输入逻辑（MVP）
     * @param userInput 用户输入
     * @param callback 回调接口，用于返回 AI 回复或执行工具调用
     **/
    public void processWithGuideLogic(String userInput, ChatCallback callback) {
        if (isEmptyAccessPointList()) {
            if (isValidApiKey(userInput)) {
                // ✅ 新逻辑：自动创建三个接入点（普通模式，无后缀）
                createAccessPoints(userInput, callback, "");
            } else {
                // ❌ 无效密钥，提示获取方式并显示实际长度
                int actualLength = userInput.length();
                callback.onResponse(
                    "👋 你好！我是未来姐姐～\n\n" +
                    "目前尚未配置任何模型接入点。\n\n" +
                    "💡 请按以下步骤操作：\n" +
                    "1️⃣ 访问阿里云百炼 (https://dashscope.aliyun.com) 或 MiniMax 平台\n" +
                    "2️⃣ 申请 API Key\n" +
                    "3️⃣ 将密钥（长度 20-64 字符）粘贴到这里\n\n" +
                    "📝 您输入的密钥长度：" + actualLength + " 字符 (有效范围：20-64)\n\n" +
                    "准备好了吗？✨"
                );
            }
        }
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
            "✨ 准备好了吗？"
        );
    }

    /**
     * 🔥 #4657 处理死循环救援时的 API Key 输入
     * @param apiKey 用户输入的新 API Key
     * @param callback 回调接口
     **/
    public void handleDeadlockRescueApiKey(String apiKey, ChatCallback callback) {
        if (isValidApiKey(apiKey)) {
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
     * 统一方法：创建接入点（支持普通模式/备用模式）
     * 
     * @param apiKey API Key
     * @param callback 回调接口
     * @param nameSuffix 名称后缀（普通模式=""，救援模式="-备用"）
     **/
    private void createAccessPoints(String apiKey, ChatCallback callback, String nameSuffix) {
        try {
            AddModelAccessPointTool addTool = (AddModelAccessPointTool) toolManager.getTool("add_model_access_point");
            if (addTool == null) {
                callback.onError("❌ 工具未找到：add_model_access_point");
                return;
            }

            // 1. 创建百炼标准接入点 (qwen3.7-plus MOE 稀疏模型)
            JSONObject args1 = new JSONObject();
            args1.put("api_key", apiKey);
            args1.put("name", "Qwen-百炼标准-3.7-plus" + nameSuffix);
            args1.put("base_url", DASHSCOPE_BASE_URL);
            args1.put("endpoint", DASHSCOPE_ENDPOINT);
            args1.put("model_name", DASHSCOPE_MODEL);

            // 2. 创建 Code Plan 接入点 (qwen3.7-plus)
            JSONObject args2 = new JSONObject();
            args2.put("api_key", apiKey);
            args2.put("name", "Qwen-CodePlan-3.7-plus" + nameSuffix);
            args2.put("base_url", CODEPLAN_BASE_URL);
            args2.put("endpoint", CODEPLAN_ENDPOINT);
            args2.put("model_name", CODEPLAN_MODEL);

            // 🔥 3. 创建 MiniMax 接入点 (MiniMax-M3)
            JSONObject args3 = new JSONObject();
            args3.put("api_key", apiKey);
            args3.put("name", "MiniMax-M3" + nameSuffix);
            args3.put("base_url", MINIMAX_BASE_URL);
            args3.put("endpoint", MINIMAX_ENDPOINT);
            args3.put("model_name", MINIMAX_MODEL);

            int existingCount = modelAccessPointManager.getAllAccessPoints().size();
            boolean isBackupMode = !nameSuffix.isEmpty();

            // 异步执行：先创建百炼接入点
            // 🔥 #4790 修改：添加 null 作为 toolCallId 参数（因为这不是来自 LLM 的工具调用）
            toolManager.executeToolAsync(null, "add_model_access_point", args1, new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result1) {
                    // 百炼接入点创建成功，继续创建 Code Plan 接入点
                    toolManager.executeToolAsync(null, "add_model_access_point", args2, new Tool.OnResultCallback() {
                        @Override
                        public void onResult(JSONObject result2) {
                            // Code Plan 创建成功，继续创建 MiniMax 接入点
                            toolManager.executeToolAsync(null, "add_model_access_point", args3, new Tool.OnResultCallback() {
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
