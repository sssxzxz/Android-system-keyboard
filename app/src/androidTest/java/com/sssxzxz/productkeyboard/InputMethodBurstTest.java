package com.sssxzxz.productkeyboard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.util.Arrays;

/** Requires ProductIme enabled and selected on a dedicated test emulator. */
public class InputMethodBurstTest extends InstrumentationTestCase {
    private EditText editor;
    private EditText findEditor(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) {
                EditText found = findEditor(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private Rect keyBounds(String text) {
        long deadline = SystemClock.uptimeMillis()+5000;
        do {
            for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText() == null ? "" : node.getText()) && node.isClickable()) {
                        Rect rect = new Rect(); node.getBoundsInScreen(rect); return rect;
                    }
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis()<deadline);
        fail("Keyboard key not visible: " + text); return null;
    }
    private void inject(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { assertTrue(getInstrumentation().getUiAutomation().injectInputEvent(event,true)); }
        finally { event.recycle(); }
    }
    private void tap(Rect rect, boolean drift) {
        long down = SystemClock.uptimeMillis();
        inject(down,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY());
        if (drift) inject(down,MotionEvent.ACTION_MOVE,rect.right+3,rect.centerY());
        inject(down,MotionEvent.ACTION_UP,drift ? rect.right+3 : rect.centerX(),rect.centerY());
    }
    public void test089ReachesRealEditorWhenEightDriftsOutside() {
        Preferences prefs = new Preferences(getInstrumentation().getTargetContext());
        java.util.List<String> oldModels = prefs.models(); boolean oldEnabled = prefs.modelSuggestions();
        prefs.models(Arrays.asList("089","0891121","800")); prefs.modelSuggestions(true);
        AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getInstrumentation().getUiAutomation().setServiceInfo(info);
        Intent intent = new Intent(getInstrumentation().getTargetContext(),SetupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(700);
        try {
            getInstrumentation().runOnMainSync(() -> {
                editor = findEditor(activity.getWindow().getDecorView()); assertNotNull(editor);
                editor.setText(""); editor.requestFocus();
                ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(editor,0);
            });
            Rect zero = keyBounds("0"), eight = keyBounds("8"), nine = keyBounds("9");
            StringBuilder expected = new StringBuilder();
            for (int i=0;i<20;i++) {
                tap(zero,false); tap(eight,true); tap(nine,false); expected.append("089");
            }
            long deadline = SystemClock.uptimeMillis()+3000;
            String[] actual = {""};
            do {
                getInstrumentation().runOnMainSync(() -> actual[0] = editor.getText().toString());
                if (expected.toString().equals(actual[0])) break;
                SystemClock.sleep(30);
            } while (SystemClock.uptimeMillis()<deadline);
            assertEquals(expected.toString(),actual[0]);
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            prefs.models(oldModels); prefs.modelSuggestions(oldEnabled);
        }
    }
}
