package com.stupidbeauty.sisterfuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Calendar;
import org.junit.Test;

/**
 * Grouping rules for the session list.
 *
 * <p>The "now" argument is passed explicitly so the assertions do not depend on the wall clock
 * at the moment the test runs.
 */
public class SessionListGroupingTest {

    /** A fixed reference point: 2026-05-15 12:00 local time. */
    private static final long NOW = fixedTime(2026, Calendar.MAY, 15, 12, 0);

    private static long fixedTime(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    @Test public void groupsTodaySessionsUnderToday() {
        assertEquals(SessionListGrouping.TODAY,
            SessionListGrouping.groupTitle(NOW - 60_000L, NOW));
        assertEquals(SessionListGrouping.TODAY,
            SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 15, 0, 5), NOW));
    }

    @Test public void groupsYesterdaySessionsUnderYesterday() {
        assertEquals(SessionListGrouping.YESTERDAY,
            SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 14, 23, 30), NOW));
    }

    @Test public void groupsTheRestOfTheWeekUnderThisWeek() {
        assertEquals(SessionListGrouping.THIS_WEEK,
            SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 13, 8, 0), NOW));
        assertEquals(SessionListGrouping.THIS_WEEK,
            SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 9, 8, 0), NOW));
    }

    @Test public void groupsOlderSessionsUnderEarlier() {
        assertEquals(SessionListGrouping.EARLIER,
            SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 8, 8, 0), NOW));
        assertEquals(SessionListGrouping.EARLIER,
            SessionListGrouping.groupTitle(fixedTime(2025, Calendar.JANUARY, 1, 8, 0), NOW));
    }

    @Test public void leavesSessionsWithoutActivityUngrouped() {
        assertNull(SessionListGrouping.groupTitle(0L, NOW));
        assertNull(SessionListGrouping.groupTitle(-1L, NOW));
    }

    @Test public void groupBoundaryMatchesTheRelativeLabelOnTheRow() {
        // The header must agree with the timestamp the row shows. A session that reads as
        // "昨天" belongs to 昨天, one that reads as a weekday belongs to 本周.
        long yesterday = fixedTime(2026, Calendar.MAY, 14, 23, 30);
        assertEquals(SessionListGrouping.YESTERDAY,
            SessionListGrouping.groupTitle(yesterday, NOW));

        long threeDaysAgo = fixedTime(2026, Calendar.MAY, 12, 9, 0);
        assertEquals(SessionListGrouping.THIS_WEEK,
            SessionListGrouping.groupTitle(threeDaysAgo, NOW));

        long tenDaysAgo = fixedTime(2026, Calendar.MAY, 5, 9, 0);
        assertEquals(SessionListGrouping.EARLIER,
            SessionListGrouping.groupTitle(tenDaysAgo, NOW));
    }

    @Test public void groupsAreOrderedNewestFirst() {
        // The list is sorted by activity descending, so the groups must appear in this order.
        assertTrue(SessionListGrouping.TODAY.compareTo(SessionListGrouping.YESTERDAY) != 0);
        assertTrue(SessionListGrouping.groupTitle(NOW, NOW)
            .compareTo(SessionListGrouping.groupTitle(fixedTime(2026, Calendar.MAY, 14, 1, 0), NOW)) < 0);
    }
}
