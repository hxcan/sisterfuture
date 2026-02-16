package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import com.stupidbeauty.sisterfuture.manager.SystemPromptManager;
import org.json.JSONObject;

/**
 * 系统提示词融合工具：仅作为setter，将LLM已融合好的新提示词设置到SystemPromptManager中。
 * 不进行任何智能处理，纯粹的机械式操作。
 */
public class FuseSystemPromptTool extends BaseTool {
    private SystemPromptManager promptManager;

    public FuseSystemPromptTool(Context context) {
        super(context);
        this.promptManager = SystemPromptManager.getInstance(context);
    }

    @Override
    public String getName() {
        return "fuse_system_prompt";
    }

    @Override
    public String getDescription() {
        return "用于更新系统提示词。接收大模型已经融合好的新提示词，并将其设置到SystemPromptManager中。本工具不进行任何智能处理，仅为机械式设置操作。";
    }

    @Override
    public JSONObject execute(JSONObject arguments) {
        JSONObject result = new JSONObject();
        try {
            // 1. 验证输入参数
            if (!arguments.has("new_prompt") || arguments.isNull("new_prompt")) {
                result.put("error", "缺少必要参数: new_prompt");
                return result;
            }

            String newPrompt = arguments.getString("new_prompt");

            // 2. 验证新提示词内容
            if (newPrompt.trim().isEmpty()) {
                result.put("error", "新提示词不能为空");
                return result;
            }

            // 3. 通过SystemPromptManager设置新提示词
            boolean success = promptManager.updatePrompt(newPrompt);

            // 4. 构造返回结果
            if (success) {
                result.put("success", true);
                result.put("message", "系统提示词已成功更新");
                result.put("updated_at", System.currentTimeMillis());
            } else {
                result.put("success", false);
                result.put("error", "系统提示词更新失败");
            }

        } catch (Exception e) {
            try {
                result.put("success", false);
                result.put("error", "执行异常: " + e.getMessage());
                result.put("stack_trace", android.util.Log.getStackTraceString(e));
            } catch (Exception ignored) {}
        }

        return result;
    }

    @Override
    protected void defineParameters(JSONObject params) {
        params.put("type", "object");
        
        JSONObject properties = new JSONObject();
        params.put("properties", properties);
        
        // new_prompt - 必填
        JSONObject newPrompt = new JSONObject();
        newPrompt.put("type", "string");
        newPrompt.put("description", "LLM已经融合好的完整新系统提示词");
        properties.put("new_prompt", newPrompt);
        
        // 设置必填字段
        JSONArray required = new JSONArray();
        required.put("new_prompt");
        params.put("required", required);
    }
}
