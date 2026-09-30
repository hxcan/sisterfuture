package com.stupidbeauty.sisterfuture;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CompressionStorageTest {
    @Test public void versionOneMigrationRetainsHistoryAndSummaryIsSessionScoped() throws Exception {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir = new File(base.getCacheDir(),"compression-test-"+UUID.randomUUID());
        assertTrue(dir.mkdirs());
        Context isolated = new ContextWrapper(base) {
            @Override public File getFilesDir() { return dir; }
        };
        File path = new File(dir,"conversations.db");
        String original = "{\"id\":\"original\",\"role\":\"user\",\"content\":\"keep everything\"}";
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path,null)) {
            db.execSQL("CREATE TABLE sessions(session_id TEXT PRIMARY KEY NOT NULL,max_rounds INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE messages(session_id TEXT NOT NULL,position INTEGER NOT NULL,message_json TEXT NOT NULL,PRIMARY KEY(session_id,position))");
            db.execSQL("INSERT INTO sessions VALUES('default',5)");
            db.execSQL("INSERT INTO messages VALUES('default',0,?)",new Object[]{original});
            db.setVersion(1);
        }
        SqliteConversationStore store = new SqliteConversationStore(isolated);
        List<JSONObject> history = store.loadHistory();
        assertEquals("keep everything",history.get(0).getString("content"));
        JSONObject summary = new JSONObject().put("summary","kept").put("coveredCount",1)
            .put("fingerprint",RequestContext.fingerprint(history,1));
        store.saveCompression(summary);
        SqliteConversationStore reopened = new SqliteConversationStore(isolated);
        assertTrue(RequestContext.valid(reopened.loadHistory(),reopened.loadCompression()));
        assertNull(new SqliteConversationStore(isolated,"another").loadCompression());
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(path.getPath(),null,0);
             android.database.Cursor cursor = db.rawQuery("SELECT message_json FROM messages WHERE session_id='default'",null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(original,cursor.getString(0));
            assertEquals(2,db.getVersion());
        }
    }

    @Test public void projectionAndCompressionNeverReplaceArchive() throws Exception {
        final List<JSONObject>[] disk = new List[]{new ArrayList<JSONObject>()};
        final JSONObject[] saved = new JSONObject[1];
        ConversationStore store = new ConversationStore() {
            public List<JSONObject> loadHistory() { return RequestContext.copy(disk[0]); }
            public void saveHistory(List<JSONObject> value) { disk[0] = RequestContext.copy(value); }
            public int loadMaxRounds(int fallback) { return fallback; }
            public void saveMaxRounds(int value) { }
            public JSONObject loadCompression() { return saved[0]; }
            public void saveCompression(JSONObject value) { saved[0] = value; }
        };
        ContextManager context = new ContextManager(store);
        for (int i = 0; i < 12; i++) {
            context.addUserMessage("question " + i);
            context.addAssistantMessage("answer " + i);
        }
        assertEquals(24, context.getHistory().size());
        String original = new org.json.JSONArray(context.getHistory()).toString();
        List<JSONObject> snapshot = context.beginCompression();
        try { context.beginCompression(); fail("Duplicate compression must fail"); }
        catch (IllegalStateException expected) { }
        int count = RequestContext.compressionBoundary(snapshot);
        JSONObject summary = new JSONObject().put("summary","previous answers").put("coveredCount",count)
            .put("fingerprint",RequestContext.fingerprint(snapshot,count));
        context.installCompression(summary);
        context.endCompression();
        assertTrue(context.getRequestMessages().size() < 24);
        assertEquals(original,new org.json.JSONArray(context.getHistory()).toString());
        assertEquals(original,new org.json.JSONArray(disk[0]).toString());
        ContextManager restored = new ContextManager(store);
        assertEquals(24,restored.getHistory().size());
        assertTrue(restored.getRequestMessages().size() < 24);
        assertTrue(restored.decreaseMaxRounds());
        assertEquals(original,new org.json.JSONArray(restored.getHistory()).toString());
    }
}
