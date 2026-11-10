package com.stupidbeauty.sisterfuture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    /** Debounce for the search field, so a fast typist does not trigger a query per keystroke. */
    private static final long SEARCH_DEBOUNCE_MILLIS = 250L;

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

        final float density = host.getResources().getDisplayMetrics().density;

        // Search field above the list.
        final EditText search = new EditText(host);
        search.setHint(host.getString(R.string.session_search_hint));
        search.setSingleLine(true);
        search.setPadding(Math.round(20 * density), Math.round(8 * density),
            Math.round(20 * density), Math.round(8 * density));
        final int searchId = View.generateViewId();
        search.setId(searchId);

        final TextView empty = new TextView(host);
        empty.setVisibility(View.GONE);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Math.round(20 * density), Math.round(32 * density),
            Math.round(20 * density), Math.round(32 * density));
        empty.setText(host.getString(R.string.session_search_empty));

        RecyclerView list = new RecyclerView(host);
        list.setLayoutManager(new LinearLayoutManager(host));
        list.setPadding(0, Math.round(4 * density), 0, 0);

        FrameLayout content = new FrameLayout(host);
        content.addView(list, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(empty, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(search, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Paging state lives with the dialog, so no callback outlives it.
        final Paging paging = new Paging(host);
        final SessionPickerAdapter adapter = new SessionPickerAdapter(host, currentId,
            new SessionPickerAdapter.Listener() {
                @Override public void onSessionChosen(String sessionId, String label) {
                    // Tapping the active session is a no-op rather than a redundant reload.
                    if (sessionId.equals(currentId)) return;
                    dialog.dismiss();
                    onSelected.onSelected(sessionId);
                }

                @Override public void onSessionLongPressed(String sessionId, SessionSummary summary) {
                    showRowMenu(host, dialog, sessionId, summary, paging, adapter, currentId);
                }

                @Override public void onSessionDeleteRequested(String sessionId) {
                    confirmDelete(host, dialog, sessionId, paging, adapter, currentId);
                }

                @Override public void onSessionRenameRequested(String sessionId, SessionSummary summary) {
                    promptRename(host, dialog, sessionId, summary, paging, adapter);
                }
            });
        list.setAdapter(adapter);

        // Search restarts paging from offset zero with the new keyword.
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                final String keyword = s.toString();
                paging.restart(adapter, keyword, empty);
            }
        });

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (adapter.shouldLoadMore(recyclerView)) paging.loadNextPage(adapter);
            }
        });

        dialog.setView(root);
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
        paging.restart(adapter, "", empty);
    }

    /** Long-press menu: pin / unpin, rename, delete. */
    private static void showRowMenu(final Activity host, final AlertDialog dialog,
                                    final String sessionId, final SessionSummary summary,
                                    final Paging paging, final SessionPickerAdapter adapter,
                                    final String currentId) {
        final boolean isCurrent = sessionId.equals(currentId);
        CharSequence[] labels = new CharSequence[] {
            summary.pinned ? host.getString(R.string.session_menu_unpin) : host.getString(R.string.session_menu_pin),
            host.getString(R.string.session_menu_rename),
            host.getString(R.string.session_menu_delete),
        };
        new AlertDialog.Builder(host)
            .setItems(labels, (d, which) -> {
                if (which == 0) {
                    // Pinning the active session is still allowed; it only changes list order.
                    new Thread(() -> {
                        try {
                            new SqliteConversationStore(host.getApplicationContext())
                                .setSessionPinned(sessionId, !summary.pinned);
                        } catch (Exception e) {
                            FileLogger.e("SessionPicker", "置顶失败：" + e.getMessage());
                        }
                        new Handler(Looper.getMainLooper()).post(
                            () -> paging.reload(adapter, paging.currentKeyword()));
                    }).start();
                } else if (which == 1) {
                    promptRename(host, dialog, sessionId, summary, paging, adapter);
                } else {
                    confirmDelete(host, dialog, sessionId, paging, adapter, currentId);
                }
            })
            .show();
    }

    /** Rename dialog: the new name replaces the generated id prefix. */
    private static void promptRename(final Activity host, final AlertDialog parentDialog,
                                     final String sessionId, final SessionSummary summary,
                                     final Paging paging, final SessionPickerAdapter adapter) {
        final EditText input = new EditText(host);
        input.setHint(host.getString(R.string.session_rename_hint));
        input.setSingleLine(true);
        input.setText(summary.title);
        input.setSelection(input.getText().length());
        int padding = Math.round(20 * host.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(host)
            .setTitle(R.string.session_rename_title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (d, which) -> {
                final String name = input.getText().toString();
                new Thread(() -> {
                    try {
                        new SqliteConversationStore(host.getApplicationContext())
                            .renameSession(sessionId, name);
                    } catch (Exception e) {
                        FileLogger.e("SessionPicker", "重命名失败：" + e.getMessage());
                    }
                    new Handler(Looper.getMainLooper()).post(
                        () -> paging.reload(adapter, paging.currentKeyword()));
                }).start();
            })
            .show();
    }

    /** Delete confirmation, because deletion removes the session's messages for good. */
    private static void confirmDelete(final Activity host, final AlertDialog parentDialog,
                                      final String sessionId, final Paging paging,
                                      final SessionPickerAdapter adapter, final String currentId) {
        new AlertDialog.Builder(host)
            .setTitle(R.string.session_delete_title)
            .setMessage(R.string.session_delete_message)
            .setNegativeButton("取消", null)
            .setPositiveButton(R.string.session_delete_confirm, (d, which) -> {
                new Thread(() -> {
                    try {
                        new SqliteConversationStore(host.getApplicationContext())
                            .deleteSession(sessionId);
                    } catch (Exception e) {
                        FileLogger.e("SessionPicker", "删除失败：" + e.getMessage());
                    }
                    final boolean wasCurrent = sessionId.equals(currentId);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        paging.reload(adapter, paging.currentKeyword());
                        // Deleting the active session would leave the host pointing at a
                        // session that no longer exists, so the dialog closes instead.
                        if (wasCurrent) parentDialog.dismiss();
                    });
                }).start();
            })
            .show();
    }

    /** Loads pages on a worker thread and hands them back on the main thread. */
    private static final class Paging {
        private final Context context;
        /** Sessions already handed to the adapter. */
        private int loaded;
        private int total;
        private boolean exhausted;
        private boolean loading;
        /** Active search keyword; blank means no filter. */
        private String keyword = "";
        /** Debounce handle for the search field. */
        private Runnable pendingRestart;

        Paging(Context context) {
            Context application = context.getApplicationContext();
            this.context = application == null ? context : application;
        }

        String currentKeyword() { return keyword; }

        /**
         * Restart paging with a new keyword, clearing the list first.
         *
         * The search field fires on every keystroke, so the work is debounced: only the last
         * keyword in a burst reaches the database.
         */
        void restart(final SessionPickerAdapter adapter, final String newKeyword, final TextView empty) {
            if (pendingRestart != null) adapter.post(pendingRestart);
            pendingRestart = new Runnable() {
                @Override public void run() {
                    pendingRestart = null;
                    applyRestart(adapter, newKeyword, empty);
                }
            };
            adapter.postDelayed(pendingRestart, SEARCH_DEBOUNCE_MILLIS);
        }

        private void applyRestart(final SessionPickerAdapter adapter, final String newKeyword,
                                  final TextView empty) {
            final boolean filtering = newKeyword != null && !newKeyword.trim().isEmpty();
            adapter.setSearchMode(filtering);
            adapter.reset();
            loaded = 0;
            total = 0;
            exhausted = false;
            keyword = newKeyword == null ? "" : newKeyword;
            adapter.setLoadingFooter(false);
            empty.setVisibility(View.GONE);
            loadNextPage(adapter, empty);
        }

        /** Reload the current list, e.g. after a pin, rename or delete. */
        void reload(final SessionPickerAdapter adapter, final String newKeyword) {
            adapter.post(() -> applyRestart(adapter, newKeyword, null));
        }

        void loadNextPage(final SessionPickerAdapter adapter) {
            loadNextPage(adapter, null);
        }

        void loadNextPage(final SessionPickerAdapter adapter, final TextView empty) {
            // Scroll callbacks fire repeatedly; never let two pages be in flight at once.
            if (loading || exhausted || adapter.isAwaitingPage()) return;
            loading = true;
            adapter.setAwaitingPage(true);
            final int offset = loaded;
            final String activeKeyword = keyword;

            new Thread(() -> {
                List<SessionSummary> page = null;
                int sessionCount = total;
                try {
                    SqliteConversationStore store = new SqliteConversationStore(context);
                    page = store.listSessionSummaries(offset, PAGE_SIZE, activeKeyword);
                    sessionCount = store.countSessions(activeKeyword);
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
                    if (empty != null && loaded == 0) {
                        // Only surface the empty state while a filter is active.
                        empty.setVisibility(activeKeyword.trim().isEmpty() ? View.GONE : View.VISIBLE);
                    }
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
