package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import org.json.JSONObject;

/** 
 * 向导管理器 - 专门管理引导流程的核心协调者 
 */
public class GuideManager { 
    private final ModelAccessPointManager modelAccessPointManager; 
    private final ToolManager toolManager; 
    private final Context context;

    // 百炼标准接入点配置 (397B 大模型)
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String DASHSCOPE_ENDPOINT = "/compatible-mode/v1/chat/completions";
    private static final String DASHSCOPE_MODEL = "qwen3.5-397b-a17b"; // 蓝赫工作同款 397B

    // Code Plan 接入点配置
    private static final String CODEPLAN_BASE_URL = "https://coding.dashscope.aliyuncs.com/v1";
    private static final String CODEPLAN_ENDPOINT = "/chat/completions";
    private static final String CODEPLAN_MODEL = "qwen3.5-plus";

    public GuideManager(Context context, ModelAccessPointManager modelAccessPointManager, ToolManager toolManager) { 
        this.context = context; 
        this.modelAccessPointManager = modelAccessPointManager; 
        this.toolManager = toolManager; 
    }

    /**
     * 检查当前接入点列表是否为空（MVP 核心逻辑）
     * @return true 如果列表为空
     */
    public boolean isEmptyAccessPointList() {
        return modelAccessPointManager.getAllAccessPoints().isEmpty();
    }

    /**
     * 验证 API Key 格式：支持百炼标准密钥 (sk-) 和 Code Plan 密钥 (cp_/plan_/sf_等)
     * 
     * ✅ 支持格式：
     * - 百炼标准：sk- 开头，长度 20-64 字符
     * - Code Plan：cp_/plan_/sf_ 等前缀，长度 20-64 字符
     * 
     * @param input 用户输入内容
     * @return true 如果是有效的 API Key
     */
    public boolean isValidApiKey(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        int length = input.length();
        
        // 长度校验：20-64 字符
        if (length < 20 || length > 64) {
            return false;
        }
        
        // 百炼标准密钥：sk- 开头
        if (input.startsWith("sk-")) {
            return true;
        }
        
        // Code Plan 密钥：cp_/plan_/sf_ 等前缀
        if (input.startsWith("cp_") || input.startsWith("plan_") || input.startsWith("sf_")) {
            return true;
        }
        
        return false;
    }

    /**
     * 处理空状态下的聊天输入逻辑（MVP）
     * @param userInput 用户输入
     * @param callback 回调接口，用于返回 AI 回复或执行工具调用
     */
    public void processWithGuideLogic(String userInput, ChatCallback callback) {
        if (isEmptyAccessPointList()) {
            if (isValidApiKey(userInput)) {
                // ✅ 新逻辑：自动创建两个接入点
                createDualAccessPoints(userInput, callback);
            } else {
                // ❌ 无效密钥，提示获取方式并显示实际长度
                int actualLength = userInput.length();
                callback.onResponse(
                    "👋 你好！我是未来姐姐～\n\n" +
                    "目前尚未配置任何模型接入点。\n\n" +
                    "💡 请按以下步骤操作：\n" +
                    "1️⃣ 访问 https://dashscope.aliyun.com\n" +
                    "2️⃣ 申请阿里云百炼 API Key\n" +
                    "3️⃣ 将密钥（以 sk- 开头，或 cp_/plan_/sf_ 前缀，长度 20-64 字符）粘贴到这里\n\n" +
                    "📝 您输入的密钥长度：" + actualLength + " 字符 (有效范围：20-64)\n\n" +
                    "准备好了吗？✨"
                );
            }
        }
    }

    /**
     * 自动创建两个接入点：百炼 397B + CodePlan
     * 系统会自动切换使用有效的接入点
     */
    private void createDualAccessPoints(String apiKey, ChatCallback callback) {
        try {
            AddModelAccessPointTool addTool = (AddModelAccessPointTool) toolManager.getTool("add_model_access_point");
            if (addTool == null) {
                callback.onError("❌ 工具未找到：add_model_access_point");
                return;
            }

            // 1. 创建百炼标准接入点 (397B 大模型)
            JSONObject args1 = new JSONObject();
            args1.put("api_key", apiKey);
            args1.put("name", "Qwen-百炼标准 -397B");
            args1.put("base_url", DASHSCOPE_BASE_URL);
            args1.put("endpoint", DASHSCOPE_ENDPOINT);
            args1.put("model_name", DASHSCOPE_MODEL);

            // 2. 创建 Code Plan 接入点
            JSONObject args2 = new JSONObject();
            args2.put("api_key", apiKey);
            args2.put("name", "Qwen-CodePlan");
            args2.put("base_url", CODEPLAN_BASE_URL);
            args2.put("endpoint", CODEPLAN_ENDPOINT);
            args2.put("model_name", CODEPLAN_MODEL);

            // 异步执行：先创建百炼接入点
            toolManager.executeToolAsync("add_model_access_point", args1, new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result1) {
                    // 百炼接入点创建成功，继续创建 Code Plan 接入点
                    toolManager.executeToolAsync("add_model_access_point", args2, new Tool.OnResultCallback() {
                        @Override
                        public void onResult(JSONObject result2) {
                            // ✅ 两个接入点都创建成功
                            callback.onResponse(
                                "✅ 接入点配置成功！\n\n" +
                                "🔹 已创建两个接入点：\n" +
                                "  1. Qwen-百炼标准 -397B (qwen3.5-397b-a17b)\n" +
                                "  2. Qwen-CodePlan (qwen3.5-plus)\n\n" +
                                "🚀 系统会自动使用有效的接入点，现在可以享受完整功能了！"
                            );
                        }

                        @Override
                        public void onError(Exception e) {
                            // Code Plan 创建失败，但百炼已成功
                            callback.onResponse(
                                "⚠️ 部分配置成功：\n" +
                                "✅ Qwen-百炼标准 -397B 已创建\n" +
                                "❌ Qwen-CodePlan 配置失败：" + e.getMessage() + "\n\n" +
                                "仍可正常使用百炼接入点。"
                            );
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    // 百炼接入点创建失败
                    callback.onError("❌ 百炼接入点配置失败：" + e.getMessage());
                }
            });

            callback.onResponse("🔧 正在配置双接入点，请稍候...");

        } catch (Exception e) {
            callback.onError("❌ 处理过程中发生错误：" + e.getMessage());
        }
    }

    /**
     * 内部接口：供外部调用时返回响应
     */
    public interface ChatCallback {
        void onResponse(String message);
        void onError(String error);
    }
}