package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import androidx.recyclerview.widget.RecyclerView;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.tool.ToolCall;
import com.stupidbeauty.sisterfuture.tool.ToolManager;
import com.stupidbeauty.sisterfuture.tool.AddModelAccessPointTool;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** 
 * 向导管理器 - 专门管理引导流程的核心协调者 
 */
public class GuideManager { 
    private final ModelAccessPointManager modelAccessPointManager; 
    private final ToolManager toolManager; 
    private final Context context; 

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
        return modelAccessPointManager.getCurrentAccessPoints().isEmpty();
    }

    /**
     * 验证 API Key 格式：sk- 开头且长度 >= 60
     * @param input 用户输入内容
     * @return true 如果是有效的阿里云 API Key
     */
    public boolean isValidApiKey(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return input.startsWith("sk-") && input.length() >= 60;
    }

    /**
     * 处理空状态下的聊天输入逻辑（MVP）
     * @param userInput 用户输入
     * @param callback 回调接口，用于返回 AI 回复或执行工具调用
     */
    public void processWithGuideLogic(String userInput, ChatCallback callback) {
        if (isEmptyAccessPointList()) {
            if (isValidApiKey(userInput)) {
                // 调用 add_model_access_point 工具添加接入点
                try {
                    AddModelAccessPointTool addTool = (AddModelAccessPointTool) toolManager.getTool("add_model_access_point");
                    if (addTool != null) {
                        JSONObject args = new JSONObject();
                        args.put("api_key", userInput);
                        // 异步执行工具，等待结果后通知用户
                        toolManager.executeToolAsync("add_model_access_point", args, new Tool.OnResultCallback() {
                            @Override
                            public void onResult(JSONObject result) {
                                callback.onResponse("✅ 接入点配置成功！现在可以享受完整功能了。");
                            }

                            @Override
                            public void onError(Exception e) {
                                callback.onError("❌ 密钥无效或配置失败：" + e.getMessage());
                            }
                        });
                        callback.onResponse("🔧 正在配置接入点，请稍候...");
                        return;
                    }
                } catch (Exception e) {
                    callback.onError("❌ 处理过程中发生错误：" + e.getMessage());
                    return;
                }
            } else {
                // 非 sk- 格式，提示获取方式
                callback.onResponse(
                    "👋 你好！我是未来姐姐～\n\n" +
                    "目前尚未配置任何模型接入点。\n\n" +
                    "💡 请按以下步骤操作：\n" +
                    "1️⃣ 访问 https://dashscope.aliyun.com\n" +
                    "2️⃣ 申请阿里云百炼 API Key\n" +
                    "3️⃣ 将密钥（以 sk- 开头，长度为 60+ 字符）粘贴到这里\n\n" +
                    "准备好了吗？✨"
                );
            }
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
