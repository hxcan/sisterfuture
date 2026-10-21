package com.stupidbeauty.sisterfuture.adapter;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
 */
public final class SessionPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int ROW_TYPE = 0;
    private static final int FOOTER_TYPE = 1;
    /** Rows from the end within which the next page is requested early. */
    private static final int PREFETCH_DISTANCE = 5;

    /** Notified when the user picks a session. */
    public interface Listener {
        void onSessionChosen(String sessionId, String label);
    }

    private final Context context;
    private final Listener listener;
    private final List<SessionSummary> rows = new ArrayList<>();
    private final String currentSessionId;
    private boolean loadingFooter;
    /** Guards against duplicate requests when scroll callbacks fire repeatedly. */
    private boolean awaitingPage;

    public SessionPickerAdapter(Context context, String currentSessionId, Listener listener) {
        this.context = context;
        this.currentSessionId = currentSessionId;
        this.listener = listener;
    }

    /** Append a freshly loaded page and publish the inserted range. */
    public void appendPage(List<SessionSummary> page) {
        if (page == null || page.isEmpty()) return;
        int insertedAt = rows.size();
        int inserted = 0;
        for (SessionSummary summary : page) {
            // Never show the same session twice, even if a page boundary shifts under us.
            if (indexOf(summary.id) < 0) { rows.add(summary); inserted++; }
        }
        if (inserted > 0) notifyItemRangeInserted(insertedAt, inserted);
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
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(sessionId)) return i;
        return -1;
    }

    @Override public int getItemViewType(int position) {
        return (loadingFooter && position == rows.size()) ? FOOTER_TYPE : ROW_TYPE;
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
        return new RowHolder(inflater.inflate(android.R.layout.simple_list_item_2, parent, false));
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            ((FooterHolder) holder).bind("正在加载…");
            return;
        }
        final SessionSummary summary = rows.get(position);
        RowHolder row = (RowHolder) holder;
        final String label = summary.label();
        final String activity = describeActivity(summary.lastActivityAt);
        row.title.setText(label);
        row.subtitle.setText(activity);
        // Recycling resets this per row, so the highlight is applied on every bind.
        row.itemView.setBackgroundColor(summary.id.equals(currentSessionId)
            ? 0x243F8CFF : Color.TRANSPARENT);
        row.itemView.setContentDescription(label + "，" + activity);
        row.itemView.setOnClickListener(v -> listener.onSessionChosen(summary.id, label));
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
        /** Both cells stack vertically; the container cannot carry the gravity itself. */
        private final LinearLayout container;

        RowHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(android.R.id.text1);
            subtitle = itemView.findViewById(android.R.id.text2);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            float density = itemView.getResources().getDisplayMetrics().density;
            int horizontal = Math.round(20 * density);
            int vertical = Math.round(12 * density);
            itemView.setPadding(horizontal, vertical, horizontal, vertical);
            title.setGravity(Gravity.START);
            subtitle.setGravity(Gravity.START);
            container = null;
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