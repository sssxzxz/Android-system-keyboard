package com.sssxzxz.productkeyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Button;

/** Keyboard taps must not wait for View's posted click or pressed-state timeout. */
final class KeyboardButton extends Button {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean tracking;
    private boolean held;
    private final Runnable longPress = () -> {
        if (tracking && isPressed() && isLongClickable()) held = performLongClick();
    };

    KeyboardButton(Context context) { super(context); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelTouch();
                tracking = true;
                held = false;
                setPressed(true);
                if (isLongClickable()) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getX() < 0 || event.getY() < 0 || event.getX() >= getWidth() || event.getY() >= getHeight()) cancelTouch();
                return true;
            case MotionEvent.ACTION_UP:
                boolean click = tracking && !held && event.getX() >= 0 && event.getY() >= 0
                        && event.getX() < getWidth() && event.getY() < getHeight();
                cancelTouch();
                if (click) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                return true;
            default:
                return true;
        }
    }
    @Override public boolean performClick() { return super.performClick(); }
    private void cancelTouch() {
        handler.removeCallbacks(longPress);
        tracking = false;
        setPressed(false);
    }
    @Override protected void onDetachedFromWindow() {
        cancelTouch();
        super.onDetachedFromWindow();
    }
}
