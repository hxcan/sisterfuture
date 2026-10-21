package com.stupidbeauty.sisterfuture;

import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
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
 * which computed all titles (one blocking database round trip each) before the dialog appeared.
 */
final class SessionPickerDialog {

    /** Sessions per page; small enough that the first page appears quickly. */
    private static final int PAGE_SIZE = 20;

    /** Reports a chosen session back to the host, which performs the actual switch. */
    interface OnSessionSelected {
        void onSelected(String sessionId);
    }

    private SessionPickerDialog() { }

    /**
     * Present the picker for the given sessions.
     *
     * @param host          dialog owner, used for the window context
     * @param currentId     the active session, rendered with a highlight
     * @param onSelected    invoked with the chosen session id; the host still validates it exists
     */
    static void show(android.app.Activity host, String currentId, OnSessionSelected onSelected) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(host)
            .setTitle("会话")
            .setNegativeButton("取消", null)
            .setPositiveButton("新建会话", null)
            .create();

        RecyclerView list = new RecyclerView(host);
        list.setLayoutManager(new LinearLayoutManager(host));
        final float density = host.getResources().getDisplayMetrics().density;
        list.setPadding(0, Math.round(4 * density), 0, 0);

        // Paging state lives with the dialog: it dies with the dialog, so no stale callbacks.
        final Paging paging = new Paging(host, currentId);
        final SessionPickerAdapter adapter = new SessionPickerAdapter(host, currentId,
            new SessionPickerAdapter.Listener() {
                @Override public void onSessionChosen(String sessionId, String label) {
                    if (sessionId.equals(currentId)) return;
                    dialog.dismiss();
                    onSelected.onSelected(sessionId);
                }
                @Override public void onNeedMorePages(boolean atEnd) {
                    if (atEnd) return;
                    paging.loadNextPage(adapter);
                }
            });
        list.setAdapter(adapter);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNullCompat RecyclerView recyclerView, int dx, int dy) {
                if (adapter.shouldLoadMore(recyclerView)) paging.loadNextPage(adapter);
            }
        });

        dialog.setView(list);
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> { dialog.dismiss(); onSelected.onSelected(null); }));

        RecyclerView.OnScrollListener unusedGuard = null;
        android.widget.ListView legacyList = dialog.getListView();
        if (legacyList != null) {
            legacyList.setDivider(new ColorDrawable(0x40808080));
            legacyList.setDividerHeight(Math.max(1, Math.round(density)));
        }
        dialog.show();
        paging.loadNextPage(adapter);
    }

    /** Marker so the scroll listener reads naturally without importing NonNull for AndroidX. */
    private @interface NonNullCompat { }

    /** Loads pages on a worker thread and hands them back on the main thread. */
    private static final class Paging {
        private final android.content.Context context;
        /** Number of sessions already handed to the adapter. */
        private int loaded;
        private int total;
        private boolean exhausted;
        private boolean loading;

        Paging(android.content.Context context, String currentSessionId) {
            this.context = context.getApplicationContext() == null ? context : context.getApplicationContext();
        }

        void loadNextPage(SessionPickerAdapter adapter) {
            // Guard against duplicate requests: scroll callbacks fire repeatedly.
            if (loading || exhausted || adapter.isAwaitingPage()) return;
            loading = true;
            adapter.setAwaitingPage(true);
            final int offset = loaded;
            final int generation = adapter.loadedCount();
            new Thread(() -> {
                List<SessionSummary> page = null;
                int count = total;
                try {
                    SqliteConversationStore store = new SqliteConversationStore(context);
                    page = store.listSessionSummaries(offset, PAGE_SIZE);
                    count = store.countSessions();
                } catch (Exception e) {
                    FileLogger.e("SessionPicker", "读取会话列表失败：" + e.getMessage());
                }
                final List<SessionSummary> result = page;
                final int totalSessions = count;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    loading = false;
                    adapter.setAwaitingPage(false);
                    if (result == null) return;
                    total = totalSessions;
                    loaded += result.size();
                    adapter.appendPage(result);
                    // One short page, or everything accounted for, means there is nothing left.
                    if (result.size() < PAGE_SIZE || loaded >= total) {
                        exhausted = true;
                        adapter.setLoadingFooter(false);
                    }
                });
            }, "session-picker-page").start();
        }
    }
}