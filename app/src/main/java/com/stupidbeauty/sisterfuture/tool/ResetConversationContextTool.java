package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.ContextManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opens a new empty session; the source context and its tool results are retained. */
public class ResetConversationContextTool implements Tool {
    public interface ResetAction { String startNewSession(ContextManager source); }
    private final ContextManager source;
    private final ResetAction action;
    public static final String RESET_TOOL_DESCRIPTION =
        "仅当用户明确要求开始新会话或重置上下文时调用。创建新的空会话并切换，保留旧历史。"
        + "本次工具回复写入旧会话，随后停止自动续接；新会话等待用户下一条输入。不是删除历史。";

    public ResetConversationContextTool(ContextManager source, ResetAction action) {
        this.source = source;
        this.action = action;
    }
    public static String getFewShotExamples() {
        return "用户明确说重置或开启新会话时调用；普通话题变化、首次问候不调用。";
    }
    @Override public String getName() { return "resetConversationContext"; }
    @Override public boolean shouldInclude() {
        int users = 0;
        for (JSONObject message : source.getHistory())
            if (message != null && "user".equals(message.optString("role"))) users++;
        return users > 1;
    }
    @Override public JSONObject getDefinition() {
        try {
            return new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", getName()).put("description", RESET_TOOL_DESCRIPTION)
                .put("parameters", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject()).put("required", new JSONArray())));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Override public JSONObject execute(JSONObject arguments) throws Exception {
        JSONObject result = new JSONObject();
        try {
            String id = action.startNewSession(source);
            result.put("status", "success").put("newSessionId", id)
                .put("message", "已开启新会话，旧历史保留。本次回复归属旧会话，停止自动续接，等待下一条用户输入。");
        } catch (Exception e) {
            result.put("status", "error").put("message", "创建新会话失败或此重置已过期；未清空旧历史。");
        }
        return result;
    }
    @Override public boolean shouldContinueAfterResult(JSONObject result) {
        return result == null || !"success".equals(result.optString("status"));
    }
}
