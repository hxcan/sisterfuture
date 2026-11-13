package com.stupidbeauty.sisterfuture.adapter;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import com.stupidbeauty.sisterfuture.R;

/**
 * Row container that slides its content aside to reveal action buttons.
 *
 * <p>The action strip sits underneath the content and is translated into view when the user
 * swipes the content to the left. Only one such row may be open at a time, so the picker opens
 * a new row only after closing the previous one.
 *
 * <p>Scrolling the list vertically takes priority: a vertical drag is handed back to the parent
 * so the RecyclerView keeps scrolling instead of the row latching onto the touch.
 *
 * <p>The two children are declared in XML and adopted in {@link #onFinishInflate()}: the strip
 * carrying the {@code session_row_actions} id is the action bar, the other one is the sliding
 * content. The constructors must stay public because LayoutInflater instantiates this class
 * reflectively.
 *
 * <p>Clicks and long presses belong on {@link #getContentView()}, not on this container: the
 * container swallows the touch while a drag may be starting, which would otherwise eat the
 * tap that selects a session.
 */
public final class SwipeRevealContainer extends FrameLayout {

    /** Fraction of the action strip that must be dragged out before the row latches open. */
    private static final float OPEN_THRESHOLD = 0.4f;

    private View content;
    private View actions;
    private float downX;
    private float downY;
    private float translationLimit;
    private boolean open;
    private boolean dragging;
    private final int touchSlop;

    /** Notified when a row opens or closes, so the host can close the other open row. */
    interface OnOpenStateChangedListener {
        void onOpenStateChanged(SwipeRevealContainer container, boolean open);
    }

    private OnOpenStateChangedListener openStateListener;

    public SwipeRevealContainer(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeRevealContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeRevealContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setOpenStateListener(OnOpenStateChangedListener listener) {
        this.openStateListener = listener;
    }

    /**
     * Adopt the children declared in XML once inflation has finished.
     *
     * <p>The strip is recognised by its id; the remaining child is the sliding content. Doing
     * this here keeps the layout file the single source of truth and avoids the caller having
     * to hand the views over.
     */
    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getId() == R.id.session_row_actions) {
                actions = child;
            } else if (content == null) {
                content = child;
            }
        }
    }

    /** The sliding content; clicks and long presses are registered on it. */
    View getContentView() {
        return content;
    }

    /** Wire the rename / delete buttons inside the action strip. */
    void setActionHandlers(View.OnClickListener rename, View.OnClickListener delete) {
        if (actions == null) return;
        View renameButton = actions.findViewById(R.id.session_row_rename);
        View deleteButton = actions.findViewById(R.id.session_row_delete);
        if (renameButton != null) renameButton.setOnClickListener(rename);
        if (deleteButton != null) deleteButton.setOnClickListener(delete);
    }

    /** Close this row if it is open. Safe to call when it is already closed. */
    void close() {
        if (!open) return;
        open = false;
        dragging = false;
        if (content != null) content.setTranslationX(0f);
        if (openStateListener != null) openStateListener.onOpenStateChanged(this, false);
    }

    /** True when this row currently shows its action strip. */
    boolean isOpen() {
        return open;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                // A mostly vertical drag belongs to the list, not to this row.
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > touchSlop) return false;
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (content == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                if (!open) {
                    // Swallow the touch so the row does not also count it as a click.
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    if (openStateListener != null && !open) openStateListener.onOpenStateChanged(this, true);
                }
                if (dragging) {
                    float limited = Math.max(-translationLimit, Math.min(0f, dx - (open ? -translationLimit : 0f)));
                    content.setTranslationX(limited);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    float current = content.getTranslationX();
                    boolean shouldOpen = -current >= translationLimit * OPEN_THRESHOLD;
                    applyOpen(shouldOpen);
                    dragging = false;
                    return true;
                }
                if (open && Math.abs(content.getTranslationX()) < 1f) {
                    // A tap while open closes the row rather than selecting the session.
                    close();
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /** Slide to the open or closed position without animation jank. */
    private void applyOpen(boolean shouldOpen) {
        open = shouldOpen;
        if (content != null) content.setTranslationX(shouldOpen ? -translationLimit : 0f);
        if (openStateListener != null) openStateListener.onOpenStateChanged(this, shouldOpen);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (actions != null && content != null) {
            // The action strip is laid out flush to the right, underneath the content.
            actions.layout(right - actions.getMeasuredWidth(), top, right, bottom);
            content.layout(left, top, right, bottom);
        }
        translationLimit = actions == null ? 0f : actions.getMeasuredWidth();
    }
}
