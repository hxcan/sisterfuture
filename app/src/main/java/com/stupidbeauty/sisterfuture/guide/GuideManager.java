package com.stupidbeauty.sisterfuture.guide;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import java.util.List;

/**
 * 引导管理器
 * 负责首次启动时的引导流程，包括空状态检测与 API Key 识别
 */
public class GuideManager {
    private static final String TAG = "GuideManager";
    
    /**
     * 检查接入点列表是否为空
     * @return 如果列表为空返回 true，否则返回 false
     */
    public boolean isEmptyAccessPointList(ModelAccessPointManager modelAccessPointManager) {
        List accessPoints = modelAccessPointManager.getAllAccessPoints();
        boolean result = accessPoints.isEmpty();
        Log.d(TAG, "isEmptyAccessPointList: " + result + ", count: " + accessPoints.size());
        return result;
    }
    
    /**
     * 验证 API Key 是否有效
     * ✅ 修复：放宽长度校验至 20-64 字符（兼容阿里云、OpenAI 等平台）
     * 
     * @param input 用户输入的字符串
     * @return 如果是有效的 API Key 返回 true，否则返回 false
     */
    public boolean isValidApiKey(String input) {
        if (input == null || !input.startsWith("sk-")) {
            Log.d(TAG, "Invalid API key format: prefix check failed");
            return false;
        }
        
        int length = input.length();
        if (length < 20 || length > 64) {
            Log.d(TAG, "Invalid API key format: length=" + length + ", expected 20-64");
            return false;
        }
        
        Log.i(TAG, "Valid API key detected: length=" + length);
        return true;
    }
    
    /**
     * 处理引导逻辑
     * @param userInput 用户输入的内容
     * @param callback 回调接口，用于向用户显示响应信息
     * @param modelAccessPointManager 模型接入点管理器实例
     * @param context 应用上下文
     */
    public void processWithGuideLogic(String userInput, ChatCallback callback, 
                                      ModelAccessPointManager modelAccessPointManager, 
                                      Context context) {
        if (isEmptyAccessPointList(modelAccessPointManager)) {
            if (isValidApiKey(userInput)) {
                // ✅ 密钥有效，触发添加逻辑
                addModelAccessPoint(userInput, modelAccessPointManager, context, callback);
            } else {
                // ❌ 密钥无效，提示获取有效 Key
                callback.onResponse("请先访问 https://dashscope.aliyun.com 获取 API Key，然后粘贴在这里。\n\n当前密钥长度：" + userInput.length() + "字符 (建议 20-64 字符)");
            }
        } else {
            // 已有接入点，跳过引导
            Log.i(TAG, "Access points already configured, skipping guide logic");
            callback.onResponse("系统已配置接入点，可以直接开始聊天！");
        }
    }
    
    /**
     * 添加新的模型接入点
     * @param apiKey 用户输入的 API Key
     * @param modelAccessPointManager 模型接入点管理器
     * @param context 应用上下文
     * @param callback 回调接口
     */
    private void addModelAccessPoint(String apiKey, ModelAccessPointManager modelAccessPointManager, 
                                     Context context, ChatCallback callback) {
        try {
            // 这里需要解析 Base URL 和 Endpoint
            // 默认使用阿里云 DashScope 的配置
            String defaultBaseUrl = "https://dashscope.aliyuncs.com";
            String defaultEndpoint = "/compatible-mode/v1/chat/completions";
            String defaultModel = "qwen3.5-35b-a3b";
            
            // 动态添加接入点
            modelAccessPointManager.addAccessPoint(
                "Aliyun Qwen3.5-35b-a3b (用户添加)",
                defaultBaseUrl,
                defaultEndpoint,
                defaultModel,
                apiKey
            );
            
            callback.onResponse("✅ API Key 验证成功！\n已将接入点添加到系统中。\n\n现在可以开始聊天了～");
            Log.i(TAG, "Successfully added new access point with user's API key");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to add access point", e);
            callback.onResponse("❌ 添加失败：" + e.getMessage());
        }
    }
}

/**
 * 回调接口定义
 */
interface ChatCallback {
    void onResponse(String response);
}