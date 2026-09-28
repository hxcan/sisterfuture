package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResetConversationContextToolTest {
    @Test public void successStopsButFailureContinuesWithoutSharedState() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ResetConversationContextTool tool = new ResetConversationContextTool(null, source -> {
            if (calls.getAndIncrement() == 0) return "new-session";
            throw new IllegalStateException("stale reset");
        });
        JSONObject success = tool.execute(new JSONObject());
        JSONObject failure = tool.execute(new JSONObject());
        assertEquals("new-session", success.getString("newSessionId"));
        assertFalse(tool.shouldContinueAfterResult(success));
        assertTrue(tool.shouldContinueAfterResult(failure));
        assertFalse(tool.shouldContinueAfterResult(success));
    }

    @Test public void ordinaryToolsContinueByDefault() throws Exception {
        Tool tool = new Tool() {
            public String getName() { return "ordinary"; }
            public JSONObject getDefinition() { return new JSONObject(); }
            public boolean shouldInclude() { return true; }
        };
        assertTrue(tool.shouldContinueAfterResult(new JSONObject().put("continueAfterResult", false)));
    }
}
