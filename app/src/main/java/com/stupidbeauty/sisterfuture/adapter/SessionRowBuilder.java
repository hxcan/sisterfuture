package com.stupidbeauty.sisterfuture.adapter;

import com.stupidbeauty.sisterfuture.SessionListGrouping;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides the group header of each session row.
 *
 * <p>The grouping rule itself lives in {@code SessionListGrouping}; this class only resolves
 * it for a whole page at once, so that a page containing several sessions of the same group
 * yields one header instead of one per session.
 *
 * <p>Pure Java on purpose: the previous implementation asked the adapter for the group of the
 * last committed row, which did not see the rows of the page being built yet. That made every
 * session in a page open its own group, and the mistake was invisible to the compiler and to
 * the linter. Keeping the rule here makes it testable without an emulator.
 */
public final class SessionRowBuilder {

    private SessionRowBuilder() { }

    /**
     * Group titles for one page of sessions.
     *
     * <p>The result has the same size as {@code activityTimes} and holds the group of the
     * corresponding session. A {@code null} entry means the session has no activity time and
     * therefore no group.
     *
     * @param activityTimes activity time of each session in the page, in order
     * @return one group title per session, in the same order
     */
    public static List<String> groupHeaders(List<Long> activityTimes) {
        List<String> headers = new ArrayList<>();
        if (activityTimes == null) return headers;
        for (Long activityAt : activityTimes) {
            headers.add(SessionListGrouping.groupTitle(activityAt == null ? 0L : activityAt));
        }
        return headers;
    }

    /**
     * Whether a group header must be inserted before a session.
     *
     * <p>A header is needed when the session has a group and that group differs from the group
     * of the row that precedes it. The caller tracks the current group while it builds the
     * page, so consecutive sessions of one group share a single header.
     *
     * @param group group of the session, or {@code null} when it has none
     * @param currentGroup group of the row that precedes it, or {@code null} when there is none
     * @return true when a header must be inserted
     */
    public static boolean needsHeader(String group, String currentGroup) {
        if (group == null) return false;
        return !group.equals(currentGroup);
    }
}
