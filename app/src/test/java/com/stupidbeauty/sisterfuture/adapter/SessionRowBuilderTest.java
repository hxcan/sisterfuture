package com.stupidbeauty.sisterfuture.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.stupidbeauty.sisterfuture.SessionListGrouping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import org.junit.Test;

/**
 * Covers the grouping decision of the session picker.
 *
 * <p>The bug this guards against showed a group header above every single session of a page,
 * because the header decision asked the already committed rows instead of the page being
 * built. Neither the compiler nor the linter noticed, so the rule is pinned down here.
 */
public class SessionRowBuilderTest {

    /** A timestamp one hour ago, used as "today". */
    private static long todayAt() {
        return System.currentTimeMillis() - 3_600_000L;
    }

    /** A timestamp two days ago, used as "yesterday" or earlier. */
    private static long yesterdayAt() {
        Calendar then = Calendar.getInstance();
        then.add(Calendar.DAY_OF_YEAR, -1);
        return then.getTimeInMillis();
    }

    /**
     * Build the header sequence the adapter would produce for one page.
     *
     * <p>Mirrors {@code appendPage}: the group of the preceding row is carried over from the
     * page before, and updated as soon as a header is emitted.
     */
    private static List<String> headersOf(List<Long> times, String previousGroup) {
        List<String> groups = SessionRowBuilder.groupHeaders(times, previousGroup);
        List<String> headers = new ArrayList<>();
        String currentGroup = previousGroup;
        for (String group : groups) {
            if (SessionRowBuilder.needsHeader(group, currentGroup)) {
                headers.add(group);
                currentGroup = group;
            }
        }
        return headers;
    }

    @Test public void oneHeaderForTwoSessionsOfSameGroupInOnePage() {
        List<String> headers = headersOf(Arrays.asList(todayAt(), todayAt()), null);
        assertEquals("同页内两个同组会话只应有一个标题", 1, headers.size());
        assertEquals(SessionListGrouping.TODAY, headers.get(0));
    }

    @Test public void twoHeadersForTwoGroupsInOnePage() {
        List<String> headers = headersOf(Arrays.asList(todayAt(), yesterdayAt()), null);
        assertEquals("同页内两个不同组会话应有两个标题", 2, headers.size());
        assertEquals(SessionListGrouping.TODAY, headers.get(0));
        assertEquals(SessionListGrouping.YESTERDAY, headers.get(1));
    }

    @Test public void noHeaderAtPageBoundaryOfSameGroup() {
        List<String> headers = headersOf(Arrays.asList(todayAt()), SessionListGrouping.TODAY);
        assertTrue("跨页同组不应再插入标题", headers.isEmpty());
    }

    @Test public void headerAtPageBoundaryOfDifferentGroup() {
        List<String> headers = headersOf(Arrays.asList(todayAt()), SessionListGrouping.YESTERDAY);
        assertEquals("跨页换组应插入新标题", 1, headers.size());
        assertEquals(SessionListGrouping.TODAY, headers.get(0));
    }

    @Test public void sessionWithoutActivityHasNoGroup() {
        List<String> headers = headersOf(Arrays.asList(0L), null);
        assertTrue("无活动时间的会话不应有标题", headers.isEmpty());
    }

    @Test public void groupHeadersKeepTheOrderOfThePage() {
        List<String> groups = SessionRowBuilder.groupHeaders(
            Arrays.asList(todayAt(), yesterdayAt(), todayAt()), null);
        assertEquals(3, groups.size());
        assertEquals(SessionListGrouping.TODAY, groups.get(0));
        assertEquals(SessionListGrouping.YESTERDAY, groups.get(1));
        assertEquals(SessionListGrouping.TODAY, groups.get(2));
    }

    @Test public void nullGroupNeverNeedsAHeader() {
        assertFalse("null 组永远不需要标题", SessionRowBuilder.needsHeader(null, null));
        assertFalse("null 组永远不需要标题", SessionRowBuilder.needsHeader(null, SessionListGrouping.TODAY));
    }

    @Test public void firstRowOfAnEmptyListNeedsAHeader() {
        assertTrue("列表为空时需要标题", SessionRowBuilder.needsHeader(SessionListGrouping.TODAY, null));
    }

    @Test public void sameGroupDoesNotNeedAHeader() {
        assertFalse("同组不需要标题",
            SessionRowBuilder.needsHeader(SessionListGrouping.TODAY, SessionListGrouping.TODAY));
    }

    @Test public void differentGroupNeedsAHeader() {
        assertTrue("换组需要标题",
            SessionRowBuilder.needsHeader(SessionListGrouping.TODAY, SessionListGrouping.YESTERDAY));
    }

    @Test public void emptyPageProducesNoGroups() {
        assertTrue(SessionRowBuilder.groupHeaders(null, null).isEmpty());
        assertTrue(SessionRowBuilder.groupHeaders(new ArrayList<Long>(), null).isEmpty());
    }
}
