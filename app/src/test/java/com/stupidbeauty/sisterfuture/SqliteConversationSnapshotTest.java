package com.stupidbeauty.sisterfuture;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SqliteConversationSnapshotTest {
    @Test public void freezesNestedMessageDataBeforeAsyncWrite() throws Exception {
        JSONObject nested = new JSONObject().put("id", "call1");
        JSONObject message = new JSONObject().put("content", "中文").put("tool_calls", new JSONArray().put(nested));
        List<JSONObject> source = new ArrayList<>(Collections.singletonList(message));
        List<String> snapshot = SqliteConversationStore.snapshot(source);
        nested.put("id", "changed"); source.clear();
        JSONObject saved = new JSONObject(snapshot.get(0));
        assertEquals("中文", saved.getString("content"));
        assertEquals("call1", saved.getJSONArray("tool_calls").getJSONObject(0).getString("id"));
    }
    @Test public void preservesOrderAndDuplicateMessageIds() throws Exception {
        List<String> snapshot = SqliteConversationStore.snapshot(Arrays.asList(
                new JSONObject().put("id", "same").put("content", "first"),
                new JSONObject().put("id", "same").put("content", "second")));
        assertEquals("first", new JSONObject(snapshot.get(0)).getString("content"));
        assertEquals("second", new JSONObject(snapshot.get(1)).getString("content"));
        assertTrue(SqliteConversationStore.snapshot(Collections.emptyList()).isEmpty());
    }
}
