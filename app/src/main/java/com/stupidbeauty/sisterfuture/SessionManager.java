package com.stupidbeauty.sisterfuture;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Owns session identity; a context always stays bound to its original session. */
public final class SessionManager {
    public static final String DEFAULT_SESSION_ID = "default";
    private final Context context;
    private final SharedPreferences navigation;
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private Session currentSession;

    public SessionManager(Context context) {
        this.context = Objects.requireNonNull(context, "context");
        navigation = context.getSharedPreferences("session_navigation", Context.MODE_PRIVATE);
        SqliteConversationStore catalog = new SqliteConversationStore(context);
        for (String id : catalog.listSessionIds()) sessions.put(id, new Session(id));
        String activeId = navigation.getString("current_session_id", DEFAULT_SESSION_ID);
        currentSession = sessions.get(activeId);
        if (currentSession == null) throw new IllegalStateException("当前会话不存在，未自动覆盖会话选择");
    }

    public synchronized Session getCurrentSession() { return currentSession; }
    public synchronized List<Session> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }
    public synchronized ContextManager getCurrentContextManager() { return currentSession.getContextManager(); }

    public synchronized Session switchSession(String id) {
        Session target = sessions.get(id);
        if (target == null) throw new IllegalArgumentException("会话不存在");
        if (target == currentSession) return target;
        target.getContextManager(); // Load successfully before changing the durable selection.
        if (!navigation.edit().putString("current_session_id", id).commit())
            throw new IllegalStateException("保存当前会话失败");
        currentSession = target;
        return target;
    }

    /** Called on the UI thread; rejects late reset tools belonging to a different session. */
    public synchronized Session startNewSession(ContextManager expectedSource) {
        if (currentSession.getContextManager() != expectedSource)
            throw new IllegalStateException("原会话已不再是当前会话，忽略过期重置");
        String id = UUID.randomUUID().toString();
        Session next = new Session(id);
        next.getContextManager(); // Creates the durable DB row before publishing selection.
        if (!navigation.edit().putString("current_session_id", id).commit())
            throw new IllegalStateException("保存当前会话失败，仍保留原会话");
        sessions.put(id, next);
        currentSession = next;
        return next;
    }

    public final class Session {
        private final String id;
        private ContextManager contextManager;
        private Session(String id) { this.id = id; }
        public String getId() { return id; }
        public String getTitle() {
            String preview = new SqliteConversationStore(context, id).loadFirstUserText();
            String prefix = DEFAULT_SESSION_ID.equals(id) ? "默认会话" : "会话 " + id.substring(0, 8);
            return preview.isEmpty() ? prefix : prefix + " · " + preview;
        }
        public synchronized ContextManager getContextManager() {
            if (contextManager == null) contextManager = new ContextManager(new SqliteConversationStore(context, id));
            return contextManager;
        }
    }
}
