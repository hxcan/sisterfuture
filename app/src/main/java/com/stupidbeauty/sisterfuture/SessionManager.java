package com.stupidbeauty.sisterfuture;

import android.content.Context;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Session ownership boundary. Phase one deliberately exposes only the existing session.
 * Activity-scoped, like the previous ContextManager; not a process singleton.
 */
public final class SessionManager {
    public static final String DEFAULT_SESSION_ID = "default";
    private final Session currentSession;
    private final List<Session> sessions;

    public SessionManager(Context context) {
        // Keep the legacy file/preferences and startup recovery behavior unchanged.
        // Do not create another ContextManager in UI, tools or request callbacks.
        currentSession = new Session(DEFAULT_SESSION_ID, "默认会话",
                new ContextManager(new FileConversationStore(Objects.requireNonNull(context, "context"))));
        sessions = Collections.singletonList(currentSession);
    }

    public Session getCurrentSession() { return currentSession; }

    /** Read-only inventory; no create/switch/delete until storage and callbacks are isolated. */
    public List<Session> getSessions() { return sessions; }

    public ContextManager getCurrentContextManager() { return currentSession.getContextManager(); }

    public static final class Session {
        private final String id;
        private final String title;
        private final ContextManager contextManager;

        private Session(String id, String title, ContextManager contextManager) {
            this.id = id;
            this.title = title;
            this.contextManager = contextManager;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public ContextManager getContextManager() { return contextManager; }
    }
}
