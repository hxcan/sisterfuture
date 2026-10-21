package com.stupidbeauty.sisterfuture;

import android.os.Handler;
import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * Runtime verification helpers for the paged session list.
 *
 * Pure Java so the assertions can run on the JVM test task without an emulator.
 */
public final class SessionListPaging {

    /** Sessions per page used by {@link SessionPickerDialog}. */
    public static final int PAGE_SIZE = 20;

    private SessionListPaging() { }

    /**
     * Whether another page should be requested after a page was appended.
     *
     * Stops paging once a short page arrives or every known session has been delivered, so the
     * picker never requests a page that cannot exist.
     */
    public static boolean hasMorePages(int pageSize, int loadedSoFar, int totalSessions) {
        if (pageSize <= 0) return false;
        if (pageSize < PAGE_SIZE) return false;
        return loadedSoFar < totalSessions;
    }

    /**
     * Merge a freshly loaded page into the rows already on screen.
     *
     * Duplicates are dropped by session id so a shifting page boundary cannot render a session
     * twice, and the returned size is the authoritative new row count for the caller.
     */
    public static int mergePage(java.util.List<String> existingIds, java.util.List<String> pageIds) {
        if (existingIds == null || pageIds == null) return existingIds == null ? 0 : existingIds.size();
        for (String id : pageIds) {
            if (id != null && !existingIds.contains(id)) existingIds.add(id);
        }
        return existingIds.size();
    }

    /**
     * Offset for the next page request.
     *
     * Derived from the number of rows actually delivered rather than the requested limit, so
     * deduplicated rows cannot make the picker re-read the same offset forever.
     */
    public static int nextOffset(int loadedRows) {
        return Math.max(0, loadedRows);
    }
}