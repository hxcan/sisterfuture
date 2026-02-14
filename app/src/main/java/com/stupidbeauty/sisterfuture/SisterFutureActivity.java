package com.stupidbeauty.sisterfuture; 

// ... 其他import ...

import com.stupidbeauty.sisterfuture.manager.SystemPromptManager;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 */
public class SisterFutureActivity extends Activity implements TextToSpeech.OnInitListener {
    private SystemPromptManager systemPromptManager;
    
    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ... 其他初始化 ...
        
        // 初始化系统提示词管理器
        systemPromptManager = SystemPromptManager.getInstance(this);
    }
    
    /**
     * 构造增强版系统提示词，从每个工具的 getDefinition() 中提取 description。
     */
    private static String buildEnhancedSystemPrompt(ToolManager toolManager, Context context) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(systemPromptManager.getCurrentPrompt()); // 从管理器获取
        
        // ... 其他逻辑保持不变 ...
        
        return promptBuilder.toString();
    }
}
