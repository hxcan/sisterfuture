package com.stupidbeauty.sisterfuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The read-only row model behind the session picker list.
 */
public class SessionSummaryTest {

    @Test public void userTitleReplacesTheGeneratedPrefix() {
        SessionSummary summary = new SessionSummary(
            "0123456789abcdef", "你好", 1_000L, "我的重要会话", false);
        assertEquals("我的重要会话", summary.displayTitle());
    }

    @Test public void emptyTitleFallsBackToTheGeneratedPrefix() {
        SessionSummary summary = new SessionSummary("0123456789abcdef", "你好", 1_000L, "", false);
        assertEquals(summary.prefix(), summary.displayTitle());
    }

    @Test public void labelStillCarriesTheMessagePreview() {
        SessionSummary summary = new SessionSummary(
            "0123456789abcdef", "你好", 1_000L, "我的重要会话", false);
        assertEquals("我的重要会话 · 你好", summary.label());
    }

    @Test public void labelWithoutPreviewIsJustTheTitle() {
        SessionSummary summary = new SessionSummary(
            "0123456789abcdef", "", 1_000L, "我的重要会话", false);
        assertEquals("我的重要会话", summary.label());
    }

    @Test public void pinFlagIsCarriedThrough() {
        assertTrue(new SessionSummary("a", "", 1L, "", true).pinned);
        assertFalse(new SessionSummary("a", "", 1L, "", false).pinned);
    }

    @Test public void nullTitleIsTreatedAsEmpty() {
        SessionSummary summary = new SessionSummary("0123456789abcdef", "", 1L, null, false);
        assertEquals("", summary.title);
        assertEquals(summary.prefix(), summary.displayTitle());
    }

    @Test public void defaultSessionKeepsItsFixedLabel() {
        SessionSummary summary = new SessionSummary(SessionManager.DEFAULT_SESSION_ID, "", 1L);
        assertEquals("默认会话", summary.prefix());
    }
}
