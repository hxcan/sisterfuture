package com.stupidbeauty.sisterfuture;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileWriter;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SessionManagerTest {
    @Test public void newSessionIsEmptyAndSelectionSurvivesRestart() throws Exception {
        Context context = isolatedContext();
        SessionManager manager = new SessionManager(context);
        ContextManager old = manager.getCurrentContextManager();
        old.addUserMessage("old question");
        old.addRawMessage(new org.json.JSONObject("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"reset-call\",\"type\":\"function\",\"function\":{\"name\":\"resetConversationContext\",\"arguments\":\"{}\"}}]}"));
        String id = manager.startNewSession(old).getId();
        assertTrue(manager.getCurrentContextManager().getHistory().isEmpty());
        old.addToolMessage("reset-call", "resetConversationContext", "{\"status\":\"success\"}");
        assertTrue(manager.getCurrentContextManager().getHistory().isEmpty());
        try { manager.startNewSession(old); fail("Stale reset must be rejected"); }
        catch (IllegalStateException expected) { }
        SessionManager restored = new SessionManager(context);
        assertEquals(id, restored.getCurrentSession().getId());
        assertTrue(restored.getCurrentContextManager().getHistory().isEmpty());
        assertEquals(2, restored.getSessions().size());
        java.util.List<org.json.JSONObject> previous = restored.getSessions().get(0).getContextManager().getHistory();
        assertEquals(3, previous.size());
        assertEquals("reset-call", previous.get(2).getString("tool_call_id"));
    }

    /** Never load, clear or overwrite the user's actual conversation/preferences. */
    private Context isolatedContext() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String namespace = "session-test-" + UUID.randomUUID();
        File directory = new File(base.getCacheDir(), namespace);
        assertTrue(directory.mkdirs());
        return new ContextWrapper(base) {
            @Override public File getFilesDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences(namespace + "-" + name, mode);
            }
        };
    }

    @Test public void oneStableSessionAndSharedHistoryOwner() {
        SessionManager manager = new SessionManager(isolatedContext());
        assertEquals(1, manager.getSessions().size());
        assertSame(manager.getCurrentSession(), manager.getSessions().get(0));
        assertEquals("default", manager.getCurrentSession().getId());
        ContextManager history = manager.getCurrentContextManager();
        assertSame(history, manager.getCurrentContextManager());
        assertSame(history, manager.getCurrentSession().getContextManager());
        history.addUserMessage("session test");
        assertEquals(1, manager.getCurrentContextManager().getHistory().size());
        history.clearHistory();
        assertTrue(manager.getCurrentContextManager().getHistory().isEmpty());
        assertEquals("default", manager.getCurrentSession().getId());
    }

    @Test public void legacyHistoryFileIsLoadedWithoutMigration() throws Exception {
        Context context = isolatedContext();
        try (FileWriter writer = new FileWriter(new File(context.getFilesDir(), "conversation_context.json"))) {
            writer.write("{\"history\":[{\"id\":\"legacy-id\",\"role\":\"user\",\"content\":\"old message\"}]}");
        }
        SessionManager manager = new SessionManager(context);
        assertEquals(1, manager.getCurrentContextManager().getHistory().size());
        assertEquals("legacy-id", manager.getCurrentContextManager().getHistory().get(0).getString("id"));
        assertEquals("old message", manager.getCurrentContextManager().getHistory().get(0).getString("content"));
    }

    @Test public void sessionInventoryCannotBeModified() {
        SessionManager manager = new SessionManager(isolatedContext());
        try { manager.getSessions().clear(); fail("Session inventory must be immutable"); }
        catch (UnsupportedOperationException expected) { }
        assertEquals(1, manager.getSessions().size());
    }
}
