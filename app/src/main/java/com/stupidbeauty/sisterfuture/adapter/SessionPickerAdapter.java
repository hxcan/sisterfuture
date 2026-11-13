package com.stupidbeauty.sisterfuture.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.stupidbeauty.sisterfuture.R;
import com.stupidbeauty.sisterfuture.SessionListGrouping;
import com.stupidbeauty.sisterfuture.SessionSummary;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Paged list of sessions for the session picker.
 *
 * Rows are appended as the user scrolls; each page costs one indexed query instead of one
 * blocking database round trip per session. The trailing loading row keeps the layout stable
 * while a page is in flight, and disappears once every session has been loaded.
 *
 * <p>Rows carry an optional group header (今天 / 昨天 / 本周 / 更早). The header is a separate
 * view type so a tap on it never selects a session. While a search keyword is active the group
 * headers are suppressed: a filtered result set is not contiguous, so grouping it would mislead.
 */
public final class SessionPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int ROW_TYPE = 0;
    private static final int FOOTER_TYPE = 1;
    private static final int HEADER_TYPE = 2;
    /** Rows from the end within which the next page is requested early. */
    private static final int PREFETCH_DISTANCE = 5;

    /** Notified when the user picks a session. */
    public interface Listener {
        void onSessionChosen(String sessionId, String label);

        /** Long press on a row: offer pin/unpin and rename. */
        void onSessionLongPressed(String sessionId, SessionSummary summary);

        /** Swipe action on a row. */
        void onSessionDeleteRequested(String sessionId);

        /** Swipe action on a row. */
        void onSessionRenameRequested(String sessionId, SessionSummary summary);
    }

    /**
     * One rendered line: either a session row or a group header.
     *
     * Keeping the two in one list means a page append is a single insertion range, so the
     * RecyclerView animates one batch instead of many.
     */
    private static final class Row {
        final SessionSummary summary;
        /** Non-null for a group header row. */
        final String headerTitle;

        Row(SessionSummary summary) {
            this.summary = summary;
            this.headerTitle = null;
        }

        Row(String headerTitle) {
            this.summary = null;
            this.headerTitle = headerTitle;
        }
    }

    private final Context context;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final String currentSessionId;
    /**
     * Main thread scheduler for the picker's debounced search.
     *
     * <p>RecyclerView.Adapter has no post/postDelayed of its own, so the adapter carries one:
     * the dialog uses it to debounce keystrokes and to hop back to the main thread.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean loadingFooter;
    /** Guards against duplicate requests when scroll callbacks fire repeatedly. */
    private boolean awaitingPage;
    /** While true, group headers are omitted because the list is filtered by a keyword. */
    private boolean searchMode;

    public SessionPickerAdapter(Context context, String currentSessionId, Listener listener) {
        this.context = context;
        this.currentSessionId = currentSessionId;
        this.listener = listener;
    }

    /** Run a task on the main thread. */
    public void post(Runnable task) {
        mainHandler.post(task);
    }

    /** Run a task on the main thread after the given delay. */
    public void postDelayed(Runnable task, long delayMillis) {
        mainHandler.postDelayed(task, delayMillis);
    }

    /**
     * Append a freshly loaded page and publish the inserted range.
     *
     * <p>When group headers are active, a header is emitted only when the group changes, so
     * consecutive rows of the same group share one header even across a page boundary.
     */
    public void appendPage(List<SessionSummary> page) {
        if (page == null || page.isEmpty()) return;
        List<Row> fresh = new ArrayList<>();
        for (SessionSummary summary : page) {
            // Never show the same session twice, even if a page boundary shifts under us.
            if (indexOf(summary.id) >= 0) continue;
            if (!searchMode) {
                String group = SessionListGrouping.groupTitle(summary.lastActivityAt);
                if (group != null && !group.equals(lastGroupTitle())) fresh.add(new Row(group));
            }
            fresh.add(new Row(summary));
        }
        if (fresh.isEmpty()) return;
        int insertedAt = rows.size();
        rows.addAll(fresh);
        notifyItemRangeInserted(insertedAt, fresh.size());
    }

    /** Replace the whole list, used when the search keyword changes. */
    public void reset() {
        int removed = rows.size();
        rows.clear();
        if (removed > 0) notifyItemRangeRemoved(0, removed);
    }

    /** Turn grouping on or off; called when the search keyword is cleared or set. */
    public void setSearchMode(boolean searchMode) {
        this.searchMode = searchMode;
    }

    public int loadedCount() { return rows.size(); }

    public void setAwaitingPage(boolean awaitingPage) { this.awaitingPage = awaitingPage; }

    public boolean isAwaitingPage() { return awaitingPage; }

    public void setLoadingFooter(boolean loadingFooter) {
        if (this.loadingFooter == loadingFooter) return;
        this.loadingFooter = loadingFooter;
        if (loadingFooter) notifyItemInserted(rows.size());
        else notifyItemRemoved(rows.size());
    }

    private int indexOf(String sessionId) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.summary != null && row.summary.id.equals(sessionId)) return i;
        }
        return -1;
    }

    /** The group title of the most recently added row, or null when there is none yet. */
    private String lastGroupTitle() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (row.headerTitle != null) return row.headerTitle;
            // A session row means the current group is the one that produced it.
            return SessionListGrouping.groupTitle(row.summary.lastActivityAt);
        }
        return null;
    }

    @Override public int getItemViewType(int position) {
        if (loadingFooter && position == rows.size()) return FOOTER_TYPE;
        Row row = rows.get(position);
        return row.headerTitle != null ? HEADER_TYPE : ROW_TYPE;
    }

    @Override public int getItemCount() { return loadingFooter ? rows.size() + 1 : rows.size(); }

    /** True once the user is close enough to the end to warrant prefetching the next page. */
    public boolean shouldLoadMore(RecyclerView recyclerView) {
        if (awaitingPage || rows.isEmpty()) return false;
        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) return false;
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int lastVisible = manager.findLastVisibleItemPosition();
        if (lastVisible == RecyclerView.NO_POSITION) return false;
        return lastVisible >= getItemCount() - PREFETCH_DISTANCE;
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == FOOTER_TYPE) {
            return new FooterHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false));
        }
        if (viewType == HEADER_TYPE) {
            return new HeaderHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.item_session_picker_row, parent, false));
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            ((FooterHolder) holder).bind("正在加载…");
            return;
        }
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(rows.get(position).headerTitle);
            return;
        }
        final SessionSummary summary = rows.get(position).summary;
        RowHolder row = (RowHolder) holder;
        final String title = summary.displayTitle();
        final String activity = describeActivity(summary.lastActivityAt);
        final String preview = summary.preview;
        row.title.setText(title);
        row.subtitle.setText(preview.isEmpty() ? activity : preview + " · " + activity);
        row.pinIcon.setVisibility(summary.pinned ? View.VISIBLE : View.GONE);
        // Recycling resets this per row, so the highlight is applied on every bind.
        row.itemView.setBackgroundColor(summary.id.equals(currentSessionId)
            ? 0x243F8CFF : Color.TRANSPARENT);
        row.itemView.setContentDescription((summary.pinned ? "已置顶，" : "") + title + "，" + activity);
        row.itemView.setOnClickListener(v -> listener.onSessionChosen(summary.id, title));
        row.itemView.setOnLongClickListener(v -> {
            listener.onSessionLongPressed(summary.id, summary);
            return true;
        });
        row.bindSwipeActions(v -> listener.onSessionRenameRequested(summary.id, summary),
            v -> listener.onSessionDeleteRequested(summary.id));
    }

    /**
     * Relative-first timestamp: recency reads faster than an absolute clock, and it matches how
     * messaging apps present conversation lists.
     */
    static String describeActivity(long activityAt) {
        if (activityAt <= 0L) return "尚无消息";
        long now = System.currentTimeMillis();
        if (activityAt > now) return formatDate(activityAt);
        long elapsed = now - activityAt;
        if (elapsed < 60_000L) return "刚刚";
        if (elapsed < 3_600_000L) return (elapsed / 60_000L) + " 分钟前";
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(activityAt);
        int dayDelta = daysBetween(Calendar.getInstance(), then);
        if (dayDelta == 0) {
            if (elapsed < 6 * 3_600_000L) return (elapsed / 3_600_000L) + " 小时前";
            return formatClock(then);
        }
        if (dayDelta == 1) return "昨天 " + formatClock(then);
        if (dayDelta < 7) return weekdayName(then) + " " + formatClock(then);
        return formatDate(activityAt);
    }

    /**
     * Localised weekday abbreviation.
     *
     * {@code Calendar.getDisplayName} needs an explicit style argument, so the zero-padded
     * weekday constant is mapped to a short form here rather than relying on that overload.
     */
    private static String weekdayName(Calendar calendar) {
        int style = isChineseLocale() ? Calendar.SHORT : Calendar.SHORT;
        String name = calendar.getDisplayName(Calendar.DAY_OF_WEEK, style, Locale.getDefault());
        return name == null ? "" : name;
    }

    private static boolean isChineseLocale() {
        String language = Locale.getDefault().getLanguage();
        return "zh".equals(language);
    }

    /** Calendar-aware day difference, so a DST shift cannot move a session across a day boundary. */
    private static int daysBetween(Calendar today, Calendar then) {
        return SessionListGrouping.daysBetween(today, then);
    }

    private static Calendar startOfDay(Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private static String formatClock(Calendar calendar) {
        return String.format(Locale.getDefault(), "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    /** Include the year only when it differs, keeping same-year rows compact. */
    private static String formatDate(long timeAt) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timeAt);
        Calendar today = Calendar.getInstance();
        String monthDay = String.format(Locale.getDefault(), "%02d/%02d",
            then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH));
        if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)) return monthDay;
        return then.get(Calendar.YEAR) + "/" + monthDay;
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final ImageView pinIcon;
        private final SwipeRevealContainer container;

        RowHolder(View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.session_row_container);
            title = itemView.findViewById(R.id.session_row_title);
            subtitle = itemView.findViewById(R.id.session_row_subtitle);
            pinIcon = itemView.findViewById(R.id.session_row_pin);
            // A long title may wrap onto a second line; the preview stays on one.
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.START);
            subtitle.setGravity(Gravity.START);
        }

        void bindSwipeActions(View.OnClickListener rename, View.OnClickListener delete) {
            container.setActionHandlers(rename, delete);
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView text;

        HeaderHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(android.R.id.text1);
            text.setGravity(Gravity.START);
            float density = itemView.getResources().getDisplayMetrics().density;
            int horizontal = Math.round(20 * density);
            int vertical = Math.round(10 * density);
            itemView.setPadding(horizontal, vertical, horizontal, vertical);
            // A header is not a session: it must never react to a tap or a long press.
            itemView.setClickable(false);
            itemView.setLongClickable(false);
            itemView.setOnClickListener(null);
            itemView.setOnLongClickListener(null);
        }

        void bind(String title) {
            text.setText(title);
            text.setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

    private static final class FooterHolder extends RecyclerView.ViewHolder {
        private final TextView text;

        FooterHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(android.R.id.text1);
            text.setGravity(Gravity.CENTER);
            float density = itemView.getResources().getDisplayMetrics().density;
            itemView.setPadding(0, Math.round(16 * density), 0, Math.round(16 * density));
        }

        void bind(String message) { text.setText(message); }
    }
}
