package com.stupidbeauty.sisterfuture;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestContextTest {
    private JSONObject msg(String role, String content) throws Exception {
        return new JSONObject().put("role", role).put("content", content);
    }
    private JSONObject state(List<JSONObject> history, int count) throws Exception {
        return new JSONObject().put("summary", "Earlier decisions").put("coveredCount", count)
            .put("fingerprint", RequestContext.fingerprint(history, count));
    }
    @Test public void projectionPreservesArchiveAndRecentTurns() throws Exception {
        List<JSONObject> history = Arrays.asList(msg("user","old"), msg("assistant","answer"),
            msg("user","recent"), msg("assistant","reply"), msg("user","compress"));
        String before = new JSONArray(history).toString();
        assertEquals(2, RequestContext.compressionBoundary(history));
        List<JSONObject> projected = RequestContext.project(history, state(history,2));
        assertEquals(4, projected.size());
        assertEquals("recent", projected.get(1).getString("content"));
        projected.get(1).put("content","modified projection");
        assertEquals(before, new JSONArray(history).toString());
    }
    @Test public void staleStateFallsBackToFullHistory() throws Exception {
        List<JSONObject> history = new ArrayList<>(Arrays.asList(msg("user","old"), msg("assistant","answer")));
        JSONObject state = state(history,1);
        history.get(0).put("content","edited");
        assertFalse(RequestContext.valid(history,state));
        assertEquals(2,RequestContext.project(history,state).size());
        history.clear();
        assertFalse(RequestContext.valid(history,state));
    }
    @Test public void appendsAndUsageDoNotInvalidateButEditsDo() throws Exception {
        List<JSONObject> history = new ArrayList<>(Arrays.asList(msg("user","old")));
        JSONObject state = state(history,1);
        history.get(0).put("_local_usage",new JSONObject().put("tokens",100));
        history.add(msg("user","new"));
        assertTrue(RequestContext.valid(history,state));
        history.get(0).put("content","changed");
        assertFalse(RequestContext.valid(history,state));
    }
    @Test public void pendingToolBatchCannotBeSplit() throws Exception {
        JSONObject call = msg("assistant","").put("tool_calls",new JSONArray()
            .put(new JSONObject().put("id","a")).put(new JSONObject().put("id","b")));
        List<JSONObject> history = new ArrayList<>(Arrays.asList(msg("user","old"),call,
            msg("tool","one").put("tool_call_id","a"), msg("user","recent"), msg("user","compress")));
        assertEquals(0,RequestContext.compressionBoundary(history));
        history.add(3,msg("tool","two").put("tool_call_id","b"));
        assertEquals(4,RequestContext.compressionBoundary(history));
    }
    @Test public void fingerprintIgnoresJsonObjectKeyOrder() throws Exception {
        assertEquals(RequestContext.fingerprint(Arrays.asList(new JSONObject("{\"a\":1,\"b\":2}")),1),
            RequestContext.fingerprint(Arrays.asList(new JSONObject("{\"b\":2,\"a\":1}")),1));
    }
}
