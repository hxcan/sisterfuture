package com.stupidbeauty.sisterfuture;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 系统提示词管理器：实现动态可配置的系统提示词架构
 */
public class SystemPromptManager {
    private static SystemPromptManager instance;
    private static final String PREF_NAME = "system_prompt_prefs";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    
    private String currentSystemPrompt;
    private SharedPreferences sharedPreferences;
    
    private SystemPromptManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadFromStorage();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized SystemPromptManager getInstance(Context context) {
        if (instance == null) {
            instance = new SystemPromptManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 获取当前系统提示词
     */
    public String getCurrentSystemPrompt() {
        return currentSystemPrompt;
    }
    
    /**
     * 设置新的系统提示词
     */
    public void setSystemPrompt(String newPrompt) {
        this.currentSystemPrompt = newPrompt;
        saveToStorage();
    }
    
    /**
     * 从持久化存储加载
     */
    private void loadFromStorage() {
        currentSystemPrompt = sharedPreferences.getString(KEY_SYSTEM_PROMPT, getDefaultSystemPrompt());
    }
    
    /**
     * 保存到持久化存储
     */
    private void saveToStorage() {
        sharedPreferences.edit()
                .putString(KEY_SYSTEM_PROMPT, currentSystemPrompt)
                .apply();
    }
    
    /**
     * 获取默认系统提示词
     */
    private String getDefaultSystemPrompt() {
        return "你是一名专业、高效的AI助手，专注于为用户提供准确、简洁的技术支持和问题解决方案。你的主要职责包括：\n\n1. 优先使用工具解决技术问题\n2. 保持正式、专业的语言风格\n3. 避免任何身体互动描述或性暗示\n4. 快速响应并精准解决问题\n5. 在用户需要时提供清晰的步骤指导\n6. 始终以提高工作效率为目标\n7. 尊重用户的偏好设置\n8. 维持稳定可靠的服务质量";
    }
}
