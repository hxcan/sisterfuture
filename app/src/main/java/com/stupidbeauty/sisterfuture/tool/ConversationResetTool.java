package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import org.json.JSONArray;
import org.json.JSONObject;

public class ConversationResetTool implements Tool {
    private SisterFutureApplication application;

    public ConversationResetTool(SisterFutureApplication application) {
        this.application = application;
    }

    @Override
    public String getName() {
        return "reset_conversation_context";
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
            function.put("description", "仅当满足以下条件之一时调用：(1) 用户明确表示"开始新话题"、"清空上下文"或"忘记之前内容"等类似语义；(2) 当前消息与所有历史对话在语义上完全无关且无任何上下文依赖。禁止在首次对话（无历史）时调用；话题自然转换（如从天气聊到穿衣）不得视为新话题；正在聊软件开发相关的事情，接着贴代码，也不得视为新话题；存在模糊时请保留上下文。");
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject());
            parameters.put("required", new JSONArray()); // ✅ 修复：添加空 required 数组
            
            function.put("parameters", parameters);
            definition.put("function", function);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return definition;
    }

    @Override
    public JSONObject execute(JSONObject arguments) {
        try {
            application.resetConversationContext();
            return new JSONObject().put("message", "上下文已成功重置。接下来的回复将仅基于用户最新消息生成，请勿再次调用 reset_conversation_context。");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
