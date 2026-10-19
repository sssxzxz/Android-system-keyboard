package com.sssxzxz.productkeyboard;

import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class KeyboardTouchTest extends InstrumentationTestCase {
    private void event(KeyboardButton button, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        button.onTouchEvent(event);
        event.recycle();
    }
    private KeyboardButton key(Runnable click) {
        KeyboardButton button = new KeyboardButton(getInstrumentation().getTargetContext());
        button.layout(0,0,100,100);
        button.setOnClickListener(v -> click.run());
        return button;
    }
    public void testRapid1121CommitsEveryTapAndReleasesHighlightImmediately() {
        getInstrumentation().runOnMainSync(() -> {
            StringBuilder received = new StringBuilder();
            KeyboardButton one = key(() -> received.append('1'));
            KeyboardButton two = key(() -> received.append('2'));
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                for (char c : "1121".toCharArray()) {
                    KeyboardButton button = c == '1' ? one : two;
                    event(button,MotionEvent.ACTION_DOWN,50,50);
                    assertTrue(button.isPressed());
                    event(button,MotionEvent.ACTION_UP,50,50);
                    expected.append(c);
                    assertEquals(expected.toString(),received.toString());
                    assertFalse(button.isPressed());
                }
            }
        });
    }
    public void testCancelAndMoveOutsideDoNotInputOrLeaveHighlight() {
        getInstrumentation().runOnMainSync(() -> {
            int[] clicks = {0}; KeyboardButton button = key(() -> clicks[0]++);
            event(button,MotionEvent.ACTION_DOWN,50,50);
            event(button,MotionEvent.ACTION_CANCEL,50,50);
            assertFalse(button.isPressed()); assertEquals(0,clicks[0]);
            event(button,MotionEvent.ACTION_DOWN,50,50);
            event(button,MotionEvent.ACTION_MOVE,110,50);
            event(button,MotionEvent.ACTION_UP,50,50);
            assertFalse(button.isPressed()); assertEquals(0,clicks[0]);
            button.performClick(); assertEquals(1,clicks[0]); // Accessibility click still works.
        });
    }
    public void testLongPressDoesNotAlsoClick() {
        int[] clicks = {0}, holds = {0}; KeyboardButton[] key = new KeyboardButton[1];
        getInstrumentation().runOnMainSync(() -> {
            key[0] = key(() -> clicks[0]++);
            key[0].setOnLongClickListener(v -> { holds[0]++; return true; });
            event(key[0],MotionEvent.ACTION_DOWN,50,50);
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100);
        getInstrumentation().runOnMainSync(() -> {
            event(key[0],MotionEvent.ACTION_UP,50,50);
            assertEquals(1,holds[0]); assertEquals(0,clicks[0]); assertFalse(key[0].isPressed());
            event(key[0],MotionEvent.ACTION_DOWN,50,50);
            event(key[0],MotionEvent.ACTION_UP,50,50);
            assertEquals(1,clicks[0]);
        });
    }
}
