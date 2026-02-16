package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.sisterfuture.SystemPromptManager;
import com.stupidbeauty.sisterfuture.tool.Tool;

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
            
            definition.put("function", function);
            definition.put("parameters", parameters);
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
