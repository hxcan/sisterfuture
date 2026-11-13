package com.stupidbeauty.sisterfuture;

import java.util.Calendar;

/**
 * Time-based grouping rules for the session list.
 *
 * <p>Pure Java on purpose: the rules can then be covered by JVM unit tests, which run without
 * an emulator, and shared with the adapter so a row and its group header never disagree.
 *
 * <p>The boundaries mirror the relative timestamp shown on the row itself:
 * 今天 / 昨天 / 本周 / 更早. A session that has never been active carries no time, so it is
 * left ungrouped rather than being filed under a misleading heading.
 */
public final class SessionListGrouping {

    public static final String TODAY = "今天";
    public static final String YESTERDAY = "昨天";
    public static final String THIS_WEEK = "本周";
    public static final String EARLIER = "更早";

    /** A session younger than this many days belongs to 本周. */
    private static final int WEEK_DAYS = 7;

    private SessionListGrouping() { }

    /**
     * Group title for an activity timestamp.
     *
     * @param activityAt milliseconds since the epoch; 0 or negative means "no activity yet"
     * @return the group title, or null when the session has no activity time
     */
    public static String groupTitle(long activityAt) {
        if (activityAt <= 0L) return null;
        return groupTitle(activityAt, System.currentTimeMillis());
    }

    /**
     * Group title for an activity timestamp relative to a given "now".
     *
     * <p>The "now" parameter exists so the rules can be tested deterministically instead of
     * depending on the wall clock at the moment the test runs.
     */
    public static String groupTitle(long activityAt, long now) {
        if (activityAt <= 0L) return null;
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(activityAt);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        int dayDelta = daysBetween(today, then);
        if (dayDelta <= 0) return TODAY;
        if (dayDelta == 1) return YESTERDAY;
        if (dayDelta < WEEK_DAYS) return THIS_WEEK;
        return EARLIER;
    }

    /**
     * Calendar-aware day difference, so a daylight saving shift cannot move a session across a
     * day boundary. Rounding absorbs the 23- and 25-hour days that DST produces.
     *
     * <p>Public because the session picker adapter lives in a subpackage and shares this rule
     * with the grouping logic; keeping one copy is what stops a row and its header from drifting.
     */
    public static int daysBetween(Calendar today, Calendar then) {
        long diff = startOfDay(today).getTimeInMillis() - startOfDay(then).getTimeInMillis();
        return (int) Math.round(diff / 86_400_000.0);
    }

    private static Calendar startOfDay(Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }
}
