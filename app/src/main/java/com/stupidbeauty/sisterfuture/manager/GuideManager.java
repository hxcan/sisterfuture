package com.stupidbeauty.sisterfuture.manager;

import android.content.SharedPreferences;
import org.json.JSONObject;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import androidx.recyclerview.widget.RecyclerView;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;


import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;


/** 
 * 向导管理器 - 专门管理引导流程的核心协调者 
 */ 
public class GuideManager { 
    private final ModelAccessPointManager modelAccessPointManager; 
    private final ToolManager toolManager; 
    private final Context context; 
    private boolean isGuideMode = false; // 当前是否处于引导模式 
    private static final String GUIDE_MODE_KEY = "guide_mode_enabled"; 

    public GuideManager(Context context, ModelAccessPointManager modelAccessPointManager, ToolManager toolManager) { 
        this.context = context; 
        this.modelAccessPointManager = modelAccessPointManager; 
        this.toolManager = toolManager; 
        // 检查是否需要启动引导模式 
        checkAndStartGuideMode(); 
    } 

    /** 
     * 检查并启动引导模式（如果需要） 
     */ 
    public void checkAndStartGuideMode() { 
        if (!modelAccessPointManager.hasAvailableAccessPoints()) { 
            enterGuideMode(); 
        } 
    } 

    /** 
     * 进入引导模式 
     */ 
    private void enterGuideMode() { 
        isGuideMode = true; 
        // 限制工具使用，仅保留必要的配置工具 
        // toolManager.limitToolsTo(new String[]{ 
        //     "add_model_access_point", 
        //     "get_current_access_point_info", 
        //     "query_tool_enhancement", 
        //     "set_tool_enhancement", 
        //     "get_tool_remark", 
        //     "set_tool_remark" 
        // }); 
         
        // 显示引导消息（通过TTS或UI） 
        // TtsManager.getInstance().speak("👋 你好！我是未来姐姐～\n看起来你还没有配置AI模型接入点。\n\n💡 只需一步：请提供一个阿里云百炼的API密钥\n👉 访问 https://dashscope.aliyun.com 获取你的密钥\n\n把密钥发给我，我来帮你自动配置！✨"); 
         
        // 保存状态 
        SharedPreferences prefs = context.getSharedPreferences("guide_prefs", Context.MODE_PRIVATE); 
        prefs.edit().putBoolean(GUIDE_MODE_KEY, true).apply(); 
    } 

    /** 
     * 退出引导模式（如果条件满足） 
     */ 
    public void exitGuideModeIfNeeded() { 
        if (isGuideMode && modelAccessPointManager.hasAvailableAccessPoints()) { 
            isGuideMode = false; 
            // 恢复完整工具集 
            // toolManager.restoreAllTools(); 
            // \n            // 通知用户已恢复完整功能 
            // TtsManager.getInstance().speak("🎉 恭喜！已成功配置接入点，现在可以使用全部功能啦！"); 
            // \n            // 清除状态 
            SharedPreferences prefs = context.getSharedPreferences("guide_prefs", Context.MODE_PRIVATE); 
            prefs.edit().putBoolean(GUIDE_MODE_KEY, false).apply(); 
        } 
    } 

    /** 
     * 检查当前是否处于引导模式 
     */ 
    public boolean isGuideMode() { 
        return isGuideMode; 
    } 

    /** 
     * 处理用户输入，决定是否继续执行聊天请求 
     */ 
    public boolean shouldProceedWithChatRequest(String userInput) { 
        // 如果在引导模式下，检查是否是有效的API密钥格式 
        if (isGuideMode && userInput != null && userInput.startsWith("sk-")) { 
            // 尝试添加接入点 
            try { 
                AddModelAccessPointTool addTool = (AddModelAccessPointTool) toolManager.getTool("add_model_access_point"); 
                if (addTool != null) { 
                    // 调用工具添加接入点（这里需要实际调用，但为简化示例） 
                    // addTool.execute(userInput); // 假设此方法存在 
                     
                    // 成功后退出引导模式 
                    exitGuideModeIfNeeded(); 
                    return true; // 允许继续执行聊天请求 
                } 
            } catch (Exception e) { 
                // 处理错误情况，保持在引导模式 
                // TtsManager.getInstance().speak("❌ 密钥格式不正确或配置失败，请重新尝试。"); 
            } 
        } 
         
        // 如果不在引导模式，或者不是有效的密钥，则允许正常执行聊天请求 
        return true; 
    } 
}
