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
