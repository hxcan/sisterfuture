package com.stupidbeauty.sisterfuture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ListView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.stupidbeauty.sisterfuture.adapter.SessionPickerAdapter;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.util.List;

/**
 * Session picker dialog backed by paged storage queries.
 *
 * Shows the first page as soon as it arrives instead of blocking on every session's title, and
 * appends further pages as the user scrolls. Replaces the previous AlertDialog + ArrayAdapter,
 * which computed every title (one blocking database round trip per session) before showing.
 */
final class SessionPickerDialog {

    /** Sessions per page; small enough that the first page appears quickly. */
    private static final int PAGE_SIZE = 20;

    /**
     * Reports the chosen session back to the host.
     *
     * A null id means "create a new session"; the host still validates any real id exists.
     */
    interface OnSessionSelected {
        void onSelected(String sessionId);
    }

    private SessionPickerDialog() { }

    static void show(Activity host, String currentId, final OnSessionSelected onSelected) {
        final AlertDialog dialog = new AlertDialog.Builder(host)
            .setTitle("会话")
            .setNegativeButton("取消", null)
            .setPositiveButton("新建会话", null)
            .create();

        RecyclerView list = new RecyclerView(host);
        list.setLayoutManager(new LinearLayoutManager(host));
        final float density = host.getResources().getDisplayMetrics().density;
        list.setPadding(0, Math.round(4 * density), 0, 0);

        // Paging state lives with the dialog, so no callback outlives it.
        final Paging paging = new Paging(host);
        final SessionPickerAdapter adapter = new SessionPickerAdapter(host, currentId,
            (sessionId, label) -> {
                // Tapping the active session is a no-op rather than a redundant reload.
                if (sessionId.equals(currentId)) return;
                dialog.dismiss();
                onSelected.onSelected(sessionId);
            });
        list.setAdapter(adapter);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (adapter.shouldLoadMore(recyclerView)) paging.loadNextPage(adapter);
            }
        });

        dialog.setView(list);
        // Intercept the positive button so creating a session does not dismiss twice.
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> { dialog.dismiss(); onSelected.onSelected(null); }));

        // Keep the standard dialog divider look; getListView() is null for a custom view.
        ListView legacyList = dialog.getListView();
        if (legacyList != null) {
            legacyList.setDivider(new ColorDrawable(0x40808080));
            legacyList.setDividerHeight(Math.max(1, Math.round(density)));
        }

        dialog.show();
        paging.loadNextPage(adapter);
    }

    /** Loads pages on a worker thread and hands them back on the main thread. */
    private static final class Paging {
        private final Context context;
        /** Sessions already handed to the adapter. */
        private int loaded;
        private int total;
        private boolean exhausted;
        private boolean loading;

        Paging(Context context) {
            Context application = context.getApplicationContext();
            this.context = application == null ? context : application;
        }

        void loadNextPage(final SessionPickerAdapter adapter) {
            // Scroll callbacks fire repeatedly; never let two pages be in flight at once.
            if (loading || exhausted || adapter.isAwaitingPage()) return;
            loading = true;
            adapter.setAwaitingPage(true);
            final int offset = loaded;

            new Thread(() -> {
                List<SessionSummary> page = null;
                int sessionCount = total;
                try {
                    SqliteConversationStore store = new SqliteConversationStore(context);
                    page = store.listSessionSummaries(offset, PAGE_SIZE);
                    sessionCount = store.countSessions();
                } catch (Exception e) {
                    FileLogger.e("SessionPicker", "读取会话列表失败：" + e.getMessage());
                }
                final List<SessionSummary> loadedPage = page;
                final int reportedTotal = sessionCount;

                new Handler(Looper.getMainLooper()).post(() -> {
                    loading = false;
                    adapter.setAwaitingPage(false);
                    if (loadedPage == null) return;
                    total = reportedTotal;
                    loaded += loadedPage.size();
                    adapter.appendPage(loadedPage);
                    // A short page, or everything already accounted for, means nothing follows.
                    if (loadedPage.size() < PAGE_SIZE || loaded >= total) {
                        exhausted = true;
                        adapter.setLoadingFooter(false);
                    } else {
                        adapter.setLoadingFooter(true);
                    }
                });
            }, "session-picker-page").start();
        }
    }
}