package com.sssxzxz.productkeyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.List;

/** A normal text editor can use any installed IME, including Chinese keyboards. */
public class CandidateEditorActivity extends Activity {
    private EditText input;
    private Preferences prefs;
    private boolean first;
    private String original;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button action(String label, Runnable run) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> run.run()); return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Preferences(this);
        first = getIntent().getBooleanExtra("first", true);
        original = getIntent().getStringExtra("original");
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText((original == null ? "添加" : "修改") + "自定义 · " + (first ? "1" : "2")); title.setTextSize(24);
        content.addView(title);
        TextView hint = new TextView(this);
        hint.setText("输入 1–10 个字符，保留大小写和空格。支持中文、数字、符号与表情。\n需要中文或更多字符时，点击下方切换输入法；保存后返回原应用，切回商品键盘即可使用。");
        content.addView(hint);
        input = new EditText(this); input.setSaveEnabled(false); input.setTextSize(24);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setMinLines(2); input.setMaxLines(4); input.setHint("例如 zp、A12、你好、+−×÷");
        input.setText(state != null ? state.getString("draft", "") : original == null ? "" : original);
        content.addView(input);
        TextView count = new TextView(this); content.addView(count);
        Button save = action("保存", this::save);
        Runnable update = () -> {
            String text = input.getText().toString();
            count.setText(CustomText.length(text) + " / 10 个字符" + (CustomText.length(text) > 10 ? " · 请缩短内容" : ""));
            save.setEnabled(CustomText.valid(text));
        };
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(Editable e) { }
        });
        content.addView(action("切换输入法 / 输入中文", () -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()));
        TextView symbols = new TextView(this); symbols.setText("常用符号 · 点击插入"); content.addView(symbols);
        for (String[] rowValues : new String[][]{{"+","−","×","÷","="}, {",",".","/","@","#"}}) {
            LinearLayout row = new LinearLayout(this); content.addView(row);
            for (String value : rowValues) row.addView(action(value, () -> {
                int start = Math.max(0, input.getSelectionStart()), end = Math.max(0, input.getSelectionEnd());
                input.getText().replace(Math.min(start,end), Math.max(start,end), value);
            }), new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        LinearLayout actions = new LinearLayout(this); content.addView(actions);
        actions.addView(action("取消", this::finish), new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        update.run();
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 30) bottom = Math.max(bottom, insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            content.setPadding(dp(20), insets.getSystemWindowInsetTop() + dp(20), dp(20), dp(20));
            scroll.setPadding(0,0,0,bottom); return insets;
        });
    }
    private void save() {
        String value = input.getText().toString();
        if (!CustomText.valid(value)) { input.setError("请输入 1–10 个字符"); return; }
        List<String> values = prefs.list(first);
        if (values.contains(value) && !value.equals(original)) { input.setError("此候选已存在"); return; }
        try {
            if (original != null) prefs.replace(first, original, value);
            else { values.add(value); prefs.save(first, values); }
            Toast.makeText(this,"已保存自定义",Toast.LENGTH_SHORT).show(); finish();
        } catch (IllegalArgumentException e) { input.setError("候选已变化，请返回后重新编辑"); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("draft",input.getText().toString()); super.onSaveInstanceState(state);
    }
}
