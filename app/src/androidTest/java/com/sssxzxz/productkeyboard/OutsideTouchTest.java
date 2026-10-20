package com.sssxzxz.productkeyboard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual outside touch routing, not a synthetic ACTION_OUTSIDE call. */
public class OutsideTouchTest extends InstrumentationTestCase {
    private Rect key(String label) {
        for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
            if (window.getType()!=AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            AccessibilityNodeInfo root=window.getRoot(); if(root==null) continue;
            for (AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)) {
                if(label.contentEquals(node.getText()==null?"":node.getText()) && node.isClickable()) {
                    Rect rect=new Rect(); node.getBoundsInScreen(rect); return rect;
                }
            }
        }
        return null;
    }
    private void awaitKeyboard(boolean visible) {
        long deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline) {
            if((key("1")!=null)==visible) return;
            SystemClock.sleep(50);
        }
        assertEquals("Keyboard visibility",visible,key("1")!=null);
    }
    private void tap(Rect rect) {
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,rect.centerX(),rect.centerY(),0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(event,true)); }
            finally { event.recycle(); }
        }
    }
    public void testOutsideSwitchAndTouchPassthrough() {
        Preferences prefs=new Preferences(getInstrumentation().getTargetContext());
        boolean original=prefs.hideOnOutsideTouch();
        AccessibilityServiceInfo info=getInstrumentation().getUiAutomation().getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getInstrumentation().getUiAutomation().setServiceInfo(info);
        Intent intent=new Intent(getInstrumentation().getTargetContext(),SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync(); SystemClock.sleep(700);
        EditText[] editor=new EditText[1]; Button[] outside=new Button[1]; AtomicInteger clicks=new AtomicInteger();
        try {
            getInstrumentation().runOnMainSync(() -> {
                LinearLayout content=new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(24,100,24,0);
                editor[0]=new EditText(activity); editor[0].setSingleLine(true); content.addView(editor[0]);
                outside[0]=new Button(activity); outside[0].setText("外部区域测试"); outside[0].setFocusable(false);
                outside[0].setOnClickListener(v -> clicks.incrementAndGet()); content.addView(outside[0]);
                activity.setContentView(content);
            });
            for(boolean enabled:new boolean[]{true,false,true}) {
                getInstrumentation().runOnMainSync(() -> {
                    prefs.hideOnOutsideTouch(enabled);
                    assertEquals(enabled,new Preferences(activity).hideOnOutsideTouch());
                    editor[0].requestFocus();
                    ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(editor[0],0);
                });
                awaitKeyboard(true);
                tap(key("1")); awaitKeyboard(true);
                Rect area=new Rect();
                getInstrumentation().runOnMainSync(() -> outside[0].getGlobalVisibleRect(area));
                int before=clicks.get(); tap(area);
                if(enabled) awaitKeyboard(false); else { SystemClock.sleep(300); awaitKeyboard(true); }
                getInstrumentation().waitForIdleSync(); assertEquals(before+1,clicks.get());
            }
            getInstrumentation().runOnMainSync(() -> assertEquals("111",editor[0].getText().toString()));
        } finally {
            prefs.hideOnOutsideTouch(original);
            getInstrumentation().runOnMainSync(activity::finish);
        }
    }
}
