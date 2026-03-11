package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.sisterfuture.manager.SystemPromptManager;
import com.stupidbeauty.sisterfuture.tool.Tool;

import org.json.JSONArray;
import org.json.JSONObject;

public class GetCurrentSystemPromptTool implements Tool {
    private SisterFutureApplication application;

    public GetCurrentSystemPromptTool(SisterFutureApplication application) {
        this.application = application;
    }

    @Override
    public String getName() {
        return "get_current_system_prompt";
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public JSONObject getDefinition() {
        JSONObject definition = new JSONObject();
        try {
            definition.put("type", "function");
            JSONObject function = new JSONObject();
            function.put("name", getName());
            function.put("description", "获取当前系统提示词，用于调试或基于现有提示进行增强调教");
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            
            // 无参数
            parameters.put("properties", new JSONObject());
            parameters.put("required", new JSONArray()); // ✅ 修复：添加空 required 数组
            
            function.put("parameters", parameters); // ✅ 修复：将 parameters 放入 function
            definition.put("function", function);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return definition;
    }

    @Override
    public JSONObject execute(JSONObject arguments) {
        try {
            SystemPromptManager promptManager = SystemPromptManager.getInstance(application);
            return new JSONObject().put("current_prompt", promptManager.getCurrentPrompt());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
