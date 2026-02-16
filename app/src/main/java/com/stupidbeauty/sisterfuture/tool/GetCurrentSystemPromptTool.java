package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.sisterfuture.tool.Tool;

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
    public JSONObject execute(JSONObject arguments) {
        return new JSONObject().put("current_prompt", application.getSystemPromptManager().getCurrentPrompt());
    }
}
