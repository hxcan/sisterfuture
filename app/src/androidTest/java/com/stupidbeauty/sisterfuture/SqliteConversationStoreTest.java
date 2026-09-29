package com.stupidbeauty.sisterfuture;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SqliteConversationStoreTest {
    private String largeText() {
        StringBuilder value = new StringBuilder();
        // > 4 MiB, with supplementary Unicode crossing many chunk boundaries.
        for (int i = 0; i < 600000; i++) value.append("中文😀AB");
        return value.toString();
    }

    @Test public void hugeExistingRowsLoadWithoutChangingStoredJson() throws Exception {
        Context context = isolated();
        SqliteConversationStore store = new SqliteConversationStore(context);
        String huge = largeText();
        JSONObject large = new JSONObject().put("id", "large-user").put("role", "user")
            .put("content", new org.json.JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "巨大消息预览"))
                .put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", "data:image/png;base64," + huge))));
        String originalJson = large.toString();
        File database = new File(context.getFilesDir(), "conversations.db");
        // Seed the original schema directly, as if written by the affected app version.
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, 0)) {
            for (int i = 0; i < 80; i++) {
                db.execSQL("INSERT INTO messages VALUES (?, ?, ?)", new Object[]{
                    "default", i, new JSONObject().put("role", "assistant").put("content", "before-" + i).toString()});
            }
            db.execSQL("INSERT INTO messages VALUES (?, ?, ?)", new Object[]{"default", 80, originalJson});
            db.execSQL("INSERT INTO messages VALUES (?, ?, ?)", new Object[]{"default", 81,
                new JSONObject().put("role", "assistant").put("content", "after").toString()});
        }
        SqliteConversationStore reopened = new SqliteConversationStore(context);
        List<JSONObject> loaded = reopened.loadHistory();
        assertEquals(82, loaded.size());
        assertEquals("before-79", loaded.get(79).getString("content"));
        assertEquals("after", loaded.get(81).getString("content"));
        assertEquals("data:image/png;base64," + huge, loaded.get(80).getJSONArray("content")
            .getJSONObject(1).getJSONObject("image_url").getString("url"));
        assertEquals("巨大消息预览", reopened.loadFirstUserText());
        // SQL-side equality verifies that reads did not rewrite, truncate or replace the row.
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, 0);
             android.database.sqlite.SQLiteStatement query = db.compileStatement(
                 "SELECT count(*) FROM messages WHERE session_id='default' AND position=80 AND message_json=?")) {
            query.bindString(1, originalJson);
            assertEquals(1, query.simpleQueryForLong());
            assertEquals(1, db.getVersion());
        }
    }

    @Test public void hugeMessagesRoundTripAcrossSessionsAndSaves() throws Exception {
        Context context = isolated();
        SqliteConversationStore first = new SqliteConversationStore(context);
        SqliteConversationStore second = new SqliteConversationStore(context, "second");
        String huge = largeText();
        JSONObject message = new JSONObject().put("id", "big").put("role", "user")
            .put("content", huge).put("extra", new JSONObject().put("untouched", true));
        first.saveHistory(Collections.singletonList(message));
        second.saveHistory(messages("separate"));
        List<JSONObject> read = new SqliteConversationStore(context).loadHistory();
        assertEquals(huge, read.get(0).getString("content"));
        assertTrue(read.get(0).getJSONObject("extra").getBoolean("untouched"));
        first.saveHistory(read);
        assertEquals(huge, new SqliteConversationStore(context).loadHistory().get(0).getString("content"));
        assertEquals("separate", second.loadHistory().get(0).getString("content"));
    }

    @Test public void chunkBoundariesPreserveEscapesAndSupplementaryCharacters() throws Exception {
        SqliteConversationStore store = new SqliteConversationStore(isolated());
        List<JSONObject> data = new ArrayList<>();
        for (int length : new int[]{16383, 16384, 16385, 32768}) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < length; i++) text.append("😀");
            text.append("\n中文\\\"").append((char) 0).append("tail");
            data.add(new JSONObject().put("role", "user").put("content", text.toString()));
        }
        store.saveHistory(data);
        List<JSONObject> restored = store.loadHistory();
        assertEquals(data.size(), restored.size());
        for (int i = 0; i < data.size(); i++)
            assertEquals(data.get(i).getString("content"), restored.get(i).getString("content"));
    }

    private Context isolated() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id = "sqlite-test-" + UUID.randomUUID();
        File dir = new File(base.getCacheDir(), id);
        assertTrue(dir.mkdirs());
        return new ContextWrapper(base) {
            @Override public File getFilesDir() { return dir; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences(id + name, mode);
            }
        };
    }
    private void legacy(Context context, String text) throws Exception {
        try (FileWriter writer = new FileWriter(new File(context.getFilesDir(), "conversation_context.json"))) {
            writer.write(text);
        }
    }
    private List<JSONObject> messages(String... contents) throws Exception {
        List<JSONObject> result = new ArrayList<>();
        for (String content : contents) result.add(new JSONObject().put("id", content).put("content", content));
        return result;
    }

    @Test public void importsOnceAndClearNeverResurrectsLegacyHistory() throws Exception {
        Context context = isolated();
        legacy(context, "{\"history\":[{\"id\":\"old\",\"role\":\"user\",\"content\":\"中文\"}]}");
        context.getSharedPreferences("context_manager", 0).edit().putInt("current_max_rounds", 17).commit();
        SqliteConversationStore store = new SqliteConversationStore(context);
        assertEquals("old", store.loadHistory().get(0).getString("id"));
        assertEquals(17, store.loadMaxRounds(5));
        store.saveHistory(Collections.emptyList());
        SqliteConversationStore reopened = new SqliteConversationStore(context);
        assertTrue(reopened.loadHistory().isEmpty());
        assertTrue(new File(context.getFilesDir(), "conversation_context.json").exists());
    }

    @Test public void orderedSnapshotsAndNestedFieldsSurviveReopen() throws Exception {
        Context context = isolated();
        SqliteConversationStore store = new SqliteConversationStore(context);
        List<JSONObject> data = messages("a", "b");
        data.get(0).put("tool_calls", new org.json.JSONArray().put(new JSONObject().put("id", "call")));
        store.saveHistory(data);
        data.get(0).put("content", "mutated");
        data.clear();
        List<JSONObject> loaded = new SqliteConversationStore(context).loadHistory();
        assertEquals("a", loaded.get(0).getString("content"));
        assertEquals("b", loaded.get(1).getString("content"));
        assertEquals("call", loaded.get(0).getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        store.saveHistory(messages("latest"));
        store.saveMaxRounds(23);
        assertEquals("latest", store.loadHistory().get(0).getString("content"));
        assertEquals(23, new SqliteConversationStore(context).loadMaxRounds(5));
    }

    @Test public void sessionDataAndSettingsAreIsolated() throws Exception {
        Context context = isolated();
        legacy(context, "{\"history\":[{\"content\":\"legacy\"}]}");
        SqliteConversationStore second = new SqliteConversationStore(context, "second");
        assertTrue(second.loadHistory().isEmpty());
        SqliteConversationStore first = new SqliteConversationStore(context);
        second.saveHistory(messages("second"));
        second.saveMaxRounds(99);
        assertEquals("legacy", first.loadHistory().get(0).getString("content"));
        assertEquals(5, first.loadMaxRounds(5));
        first.saveHistory(Collections.emptyList());
        assertEquals("second", second.loadHistory().get(0).getString("content"));
        assertEquals(99, second.loadMaxRounds(5));
    }

    @Test public void preferencesImportOnlyWhenJsonAbsent() throws Exception {
        Context context = isolated();
        context.getSharedPreferences("context_manager", 0).edit()
                .putString("history", "[{\"content\":\"prefs\"}]").commit();
        assertEquals("prefs", new SqliteConversationStore(context).loadHistory().get(0).getString("content"));
        Context empty = isolated();
        empty.getSharedPreferences("context_manager", 0).edit().putString("history", "[{\"content\":\"stale\"}]").commit();
        legacy(empty, "{\"history\":[]}");
        assertTrue(new SqliteConversationStore(empty).loadHistory().isEmpty());
    }

    @Test public void brokenLegacyDoesNotCommitImportMarker() throws Exception {
        Context context = isolated();
        legacy(context, "broken");
        try { new SqliteConversationStore(context); fail(); }
        catch (IllegalStateException expected) { }
        legacy(context, "{\"history\":[{\"content\":\"repaired\"}]}");
        assertEquals("repaired", new SqliteConversationStore(context).loadHistory().get(0).getString("content"));
    }

    @Test public void failedReplacementRollsBackDeletedMessages() throws Exception {
        Context context = isolated();
        SqliteConversationStore store = new SqliteConversationStore(context);
        store.saveHistory(messages("keep"));
        assertEquals(1, store.loadHistory().size()); // drain serial writer
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(context.getFilesDir(), "conversations.db").getPath(), null, 0)) {
            db.execSQL("CREATE TRIGGER reject_message BEFORE INSERT ON messages BEGIN SELECT RAISE(ABORT, 'test'); END");
        }
        store.saveHistory(messages("replacement"));
        assertEquals("keep", store.loadHistory().get(0).getString("content"));
    }
}
