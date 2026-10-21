package com.stupidbeauty.sisterfuture;

import android.content.Context;

/**
 * A lightweight, read-only row for the session picker list.
 * Carries only what the list renders, so listing sessions never loads message bodies.
 */
public final class SessionSummary {
    public final String id;
    /** First user message, collapsed to a short single-line preview. Empty when the session has none. */
    public final String preview;
    /** Latest message time in milliseconds, or 0 when the session has no messages yet. */
    public final long lastActivityAt;

    public SessionSummary(String id, String preview, long lastActivityAt) {
        this.id = id;
        this.preview = preview;
        this.lastActivityAt = lastActivityAt;
    }

    /** Human-facing label: the default session keeps its fixed prefix; others show the id prefix. */
    public String prefix() {
        return SessionManager.DEFAULT_SESSION_ID.equals(id) ? "默认会话" : "会话 " + id.substring(0, 8);
    }

    public String label() {
        return preview.isEmpty() ? prefix() : prefix() + " · " + preview;
    }
}