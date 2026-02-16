package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.Tool;

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
    public Object execute(Object... arguments) {
        return application.getSystemPromptManager().getCurrentPrompt();
    }
}
