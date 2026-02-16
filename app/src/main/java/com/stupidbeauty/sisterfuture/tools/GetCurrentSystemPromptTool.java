package com.stupidbeauty.sisterfuture.tools;

import android.content.Context;

import com.stupidbeauty.sisterfuture.SystemPromptManager;

/**
 * 获取当前系统提示词的工具类
 */
public class GetCurrentSystemPromptTool {
    private Context context;
    
    public GetCurrentSystemPromptTool(Context context) {
        this.context = context;
    }
    
    /**
     * 执行获取当前系统提示词的操作
     * @return 返回当前系统的完整提示词内容
     */
    public String execute() {
        try {
            // 通过SystemPromptManager获取当前提示词
            SystemPromptManager manager = SystemPromptManager.getInstance(context);
            String currentPrompt = manager.getCurrentSystemPrompt();
            
            // 格式化输出
            StringBuilder result = new StringBuilder();
            result.append("# 系统提示词\n\n");
            result.append("当前系统运行的核心规则如下：\n\n");
            result.append("```")
            result.append(currentPrompt);
            result.append("```\n\n");
            result.append("*本内容由SystemPromptManager动态提供*\n");
            
            return result.toString();
        } catch (Exception e) {
            return "错误：无法获取系统提示词 - " + e.getMessage();
        }
    }
}
