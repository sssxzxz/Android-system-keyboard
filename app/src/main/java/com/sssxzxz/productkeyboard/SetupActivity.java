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
        layout.addView(test);
        setContentView(layout);
        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(28, bars.top + 28, 28, bars.bottom + 16); return insets;
        });
    }
}
