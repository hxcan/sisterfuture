package com.stupidbeauty.sisterfuture;

import android.content.SharedPreferences;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FileConversationStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Map<String, Object> values = new HashMap<>();
    private int previousLogLevel;

    @Before public void quietAndroidLogging() throws Exception {
        java.lang.reflect.Field field = FileLogger.class.getDeclaredField("currentLevel");
        field.setAccessible(true);
        previousLogLevel = field.getInt(null);
        FileLogger.setLevel(Integer.MAX_VALUE);
    }
    @After public void restoreLogging() { FileLogger.setLevel(previousLogLevel); }

    private SharedPreferences preferences() {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{SharedPreferences.Editor.class}, (p, m, a) -> {
                    if (m.getName().equals("putInt")) { values.put((String) a[0], a[1]); return p; }
                    if (m.getName().equals("apply")) return null;
                    throw new AssertionError(m.getName());
                });
        return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{SharedPreferences.class}, (p, m, a) -> {
                    if (m.getName().equals("edit")) return editor;
                    if (m.getName().equals("getString") || m.getName().equals("getInt"))
                        return values.containsKey(a[0]) ? values.get(a[0]) : a[1];
                    throw new AssertionError(m.getName());
                });
    }
    private File file() { return new File(temporary.getRoot(), "conversation_context.json"); }
    private FileConversationStore store() { return new FileConversationStore(file(), preferences(), Runnable::run); }
    private void write(String text) throws Exception { Files.write(file().toPath(), text.getBytes(StandardCharsets.UTF_8)); }

    @Test public void readsExistingFormatAndPrefersFile() throws Exception {
        values.put("history", "[{\"content\":\"old\"}]");
        write("{\"history\":[{\"id\":\"stable\",\"role\":\"user\",\"content\":\"中文\"}]}");
        assertEquals("stable", store().loadHistory().get(0).getString("id"));
        assertEquals("中文", store().loadHistory().get(0).getString("content"));
    }
    @Test public void emptyFileHistoryDoesNotResurrectLegacyMessages() throws Exception {
        values.put("history", "[{\"content\":\"old\"}]");
        write("{\"history\":[]}");
        assertTrue(store().loadHistory().isEmpty());
    }
    @Test public void missingAndInvalidFileFallBackToLegacyPreferences() throws Exception {
        values.put("history", "[{\"content\":\"legacy\"}]");
        assertEquals("legacy", store().loadHistory().get(0).getString("content"));
        for (String invalid : new String[]{"", "broken", "{}", "{\"history\":null}", "{\"history\":[1]}"}) {
            write(invalid);
            assertEquals("legacy", store().loadHistory().get(0).getString("content"));
        }
    }
    @Test public void noUsableHistoryReturnsEmpty() {
        assertTrue(store().loadHistory().isEmpty());
        values.put("history", "broken");
        assertTrue(store().loadHistory().isEmpty());
    }
    @Test public void savesAndReopensWithoutLosingMessageFields() throws Exception {
        JSONObject message = new JSONObject().put("id", "m1").put("role", "tool")
                .put("tool_call_id", "call1").put("content", "结果");
        store().saveHistory(Collections.singletonList(message));
        assertEquals(message.toString(), store().loadHistory().get(0).toString());
        store().saveHistory(Collections.emptyList());
        assertTrue(store().loadHistory().isEmpty());
    }
    @Test public void queuedSavesSnapshotListAndKeepSubmissionOrder() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        FileConversationStore store = new FileConversationStore(file(), preferences(), pending::add);
        List<JSONObject> history = new ArrayList<>();
        history.add(new JSONObject().put("content", "first"));
        store.saveHistory(history);
        history.clear();
        store.saveHistory(history);
        assertFalse(file().exists());
        pending.get(0).run();
        assertEquals("first", store.loadHistory().get(0).getString("content"));
        pending.get(1).run();
        assertTrue(store.loadHistory().isEmpty());
    }
    @Test public void maxRoundsKeepsLegacyKeyAndDefault() {
        assertEquals(5, store().loadMaxRounds(5));
        store().saveMaxRounds(12);
        assertEquals(12, store().loadMaxRounds(5));
        assertEquals(12, values.get("current_max_rounds"));
    }
}
