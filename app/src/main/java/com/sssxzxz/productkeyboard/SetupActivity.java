package com.sssxzxz.productkeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 60, 28, 28);
        TextView title = new TextView(this);
        title.setText("商品键盘\n\n专用于商品型号输入。\n长按全清进入设置；长按组合或字母编辑候选。\n所有配置只保存在本机，不联网、不记录输入历史。\n");
        title.setTextSize(20);
        layout.addView(title);
        Button enable = new Button(this); enable.setText("1 · 启用商品键盘");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        layout.addView(enable);
        Button pick = new Button(this); pick.setText("2 · 切换到商品键盘");
        pick.setOnClickListener(v -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        layout.addView(pick);
        EditText test = new EditText(this); test.setHint("在这里试输型号"); test.setSaveEnabled(false);
        test.setSingleLine(true);
        test.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        test.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            Toast.makeText(this,"已收到搜索操作",Toast.LENGTH_SHORT).show(); return true;
        });
        layout.addView(test);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(layout);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 30) bottom = Math.max(bottom, insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            layout.setPadding(28, insets.getSystemWindowInsetTop() + 28, 28, 16);
            scroll.setPadding(0, 0, 0, bottom);
            if (test.hasFocus()) test.post(() -> test.requestRectangleOnScreen(new android.graphics.Rect(0,0,test.getWidth(),test.getHeight()),true));
            return insets;
        });
    }
}
