package com.sssxzxz.productkeyboard;

import android.inputmethodservice.InputMethodService;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public class ProductIme extends InputMethodService {
    private Preferences prefs;
    private LinearLayout root;
    private FrameLayout host;
    private float unit = 1f;
    private boolean editingCombo;
    private String draft = "";
    private List<String> layoutDraft;
    private boolean layoutRight;
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        prefs = new Preferences(this);
        host = new FrameLayout(this); host.setBackgroundColor(Color.rgb(18,23,30));
        getWindow().getWindow().setNavigationBarColor(Color.rgb(18,23,30));
        getWindow().getWindow().getDecorView().setSystemUiVisibility(0);
        host.setPadding(0, 0, 0, dp(24));
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, 0, 0, Math.max(dp(24), insets.getSystemWindowInsetBottom()));
            return insets;
        });
        showKeyboard(); return host;
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) { super.onStartInputView(info, restarting); if (host != null) showKeyboard(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void panel(boolean keyboard) {
        host.removeAllViews(); root = column();
        float screenWidth = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        float screenHeight = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;
        float base = Math.min(screenWidth, Math.min(480f, screenHeight * .72f));
        unit = base / 400f * prefs.scale();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(base * prefs.scale()), keyboard ? dp(288 * unit) : dp(Math.min(330, screenHeight * .65f)), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        root.setPadding(dp(4*unit), dp(4*unit), dp(4*unit), dp(4*unit));
        host.addView(root, lp);
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(Math.max(12, (label.contains("\n") ? 16 : 21)*unit));
        b.setIncludeFontPadding(false);
        b.setTextColor(Color.rgb(235,241,247)); b.setPadding(0,0,0,0); b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(39,49,62)); bg.setCornerRadius(dp(8*unit));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x5578DDD0), bg, null));
        b.setSoundEffectsEnabled(false);
        b.setOnClickListener(v -> { feedback(v); action.run(); }); return b;
    }
    private void feedback(View view) { if (prefs.vibration()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); }
    private void add(LinearLayout parent, View child, float weight) {
        boolean vertical = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(vertical ? -1 : 0, vertical ? 0 : -1, weight);
        lp.setMargins(dp(2*unit),dp(2*unit),dp(2*unit),dp(2*unit)); parent.addView(child, lp);
    }
    private void commit(String value) { InputConnection ic = getCurrentInputConnection(); if(ic != null) ic.commitText(value, 1); }
    static boolean search(InputConnection ic) {
        if (ic == null) return false;
        ic.finishComposingText();
        // Ask the focused editor to search; never insert text or submit a different action.
        return ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH);
    }
    private void search() {
        if (!search(getCurrentInputConnection())) Toast.makeText(this,"搜索未发送，请重新点击应用的搜索框",Toast.LENGTH_SHORT).show();
    }
    void clear(InputConnection ic) {
        if (ic == null) return;
        ic.beginBatchEdit();
        try {
            ic.finishComposingText();
            // Never use cut: it would write the user's text to the clipboard.
            if (ic.performContextMenuAction(android.R.id.selectAll)) ic.commitText("", 1);
            else Toast.makeText(this,"此输入框不支持全清，请在应用内选择全部后删除",Toast.LENGTH_SHORT).show();
        } finally { ic.endBatchEdit(); }
    }
    private void backspace() {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        CharSequence selection = ic.getSelectedText(0);
        if (selection != null && selection.length() > 0) ic.commitText("",1);
        else ic.deleteSurroundingTextInCodePoints(1,0);
    }
    private void longPress(Button button, Runnable action) { button.setOnLongClickListener(v -> { feedback(v); action.run(); return true; }); }
    private void showKeyboard() {
        panel(true); LinearLayout row = new LinearLayout(this); root.addView(row,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout functions = column();
        for (String key : prefs.functionOrder()) add(functions, functionButton(key), 1);
        LinearLayout right = column();
        if (!prefs.functionsOnRight()) add(row,functions,1);
        add(row,right,3);
        if (prefs.functionsOnRight()) add(row,functions,1);
        for(int r=0;r<3;r++) { LinearLayout numbers = new LinearLayout(this); add(right,numbers,1); for(int c=1;c<=3;c++) { String value = Integer.toString(r*3+c); add(numbers,button(value,()->commit(value)),1); } }
        LinearLayout bottom = new LinearLayout(this); add(right,bottom,1); add(bottom,button("0",()->commit("0")),2);
        Button search = button("搜索", this::search); search.setTextColor(Color.rgb(120,221,208)); add(bottom,search,1);
    }
    private String functionName(String key) {
        switch (key) {
            case "clear": return "全清";
            case "combo": return "组合";
            case "letter": return "字母";
            case "minus": return "减号";
            default: return "退格";
        }
    }
    private Button functionButton(String key) {
        Button b;
        switch (key) {
            case "clear": b = button("全清", () -> clear(getCurrentInputConnection())); longPress(b,this::settings); return b;
            case "combo": b = button(prefs.current(true).toUpperCase(Locale.ROOT)+"\n组合", () -> commit(prefs.current(true))); longPress(b,() -> candidates(true,false)); return b;
            case "letter": b = button(prefs.current(false).toUpperCase(Locale.ROOT)+"\n字母", () -> commit(prefs.current(false))); longPress(b,() -> candidates(false,false)); return b;
            case "minus": return button("−", () -> commit("-"));
            default: return button("退格", this::backspace);
        }
    }
    private void openLayoutEditor() {
        layoutDraft = prefs.functionOrder(); layoutRight = prefs.functionsOnRight(); layoutEditor();
    }
    private void layoutEditor() {
        panel(false);
        LinearLayout header = new LinearLayout(this); root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        add(header,button("取消",this::settings),1);
        add(header,button(layoutRight ? "功能列：右侧 ⇄" : "功能列：左侧 ⇄", () -> {layoutRight = !layoutRight; layoutEditor();}),3);
        TextView hint = new TextView(this); hint.setText("长按功能键拖到目标位置；点击保存生效"); hint.setTextColor(Color.rgb(179,192,205)); hint.setTextSize(12); root.addView(hint);
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout preview = new LinearLayout(this); scroll.addView(preview,new ScrollView.LayoutParams(-1,dp(230)));
        LinearLayout functions = column();
        for (String key : layoutDraft) {
            Button item = button("↕ " + functionName(key), () -> {}); item.setTextSize(15);
            item.setContentDescription("拖动排列" + functionName(key));
            item.setOnLongClickListener(v -> { feedback(v); return v.startDragAndDrop(ClipData.newPlainText("layout",key),new View.DragShadowBuilder(v),key,0); });
            item.setOnDragListener((v,event) -> {
                switch(event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED: return event.getLocalState() instanceof String && layoutDraft.contains((String)event.getLocalState());
                    case DragEvent.ACTION_DRAG_ENTERED: v.setAlpha(.5f); return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED: v.setAlpha(1f); return true;
                    case DragEvent.ACTION_DROP:
                        String source = (String)event.getLocalState();
                        int target = layoutDraft.indexOf(key);
                        if (target >= 0 && layoutDraft.remove(source)) { layoutDraft.add(target,source); host.post(this::layoutEditor); }
                        return true;
                    default: return true;
                }
            });
            add(functions,item,1);
        }
        LinearLayout numbers = column();
        for (String label : new String[]{"1    2    3", "4    5    6", "7    8    9", "0       搜索"}) {
            TextView text = new TextView(this); text.setText(label); text.setTextSize(18); text.setTextColor(Color.rgb(179,192,205)); text.setGravity(Gravity.CENTER); add(numbers,text,1);
        }
        if (!layoutRight) add(preview,functions,2);
        add(preview,numbers,3);
        if (layoutRight) add(preview,functions,2);
        LinearLayout actions = new LinearLayout(this); root.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));
        Button reset = button("恢复默认", () -> { layoutRight = false; layoutDraft = Preferences.defaultOrder(); layoutEditor(); }); reset.setTextSize(16); add(actions,reset,1);
        add(actions,button("保存", () -> { prefs.saveLayout(layoutRight,layoutDraft); showKeyboard(); }),1);
    }
    private void toolbar(String title, String action, Runnable run) {
        LinearLayout bar = new LinearLayout(this); root.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));
        add(bar,button("返回",this::showKeyboard),1); add(bar,button(title,()->{}),2); if(action != null) add(bar,button(action,run),1);
    }
    private void candidates(boolean combo, boolean editing) {
        panel(false); toolbar(combo ? "组合候选" : "字母候选", editing ? "完成" : "编辑", () -> candidates(combo,!editing));
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout list = column(); scroll.addView(list);
        for(String value : prefs.list(combo)) {
            LinearLayout row = new LinearLayout(this); list.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
            Button select = button((value.equals(prefs.current(combo)) ? "✓ " : "") + value.toUpperCase(Locale.ROOT), () -> {prefs.select(combo,value); if(!editing) showKeyboard(); else candidates(combo,true);}); add(row,select,3);
            if(editing) {
                Button handle = button("↕",()->Toast.makeText(this,"长按此处，拖到目标候选上松开",Toast.LENGTH_SHORT).show()); handle.setContentDescription("长按拖动排序 "+value);
                longPress(handle,()->handle.startDragAndDrop(ClipData.newPlainText("candidate",value),new View.DragShadowBuilder(handle),value,0)); add(row,handle,1);
                add(row,button("删除",()->{ List<String> values=prefs.list(combo); if(values.size()==1) { Toast.makeText(this,"至少保留一个候选",Toast.LENGTH_SHORT).show(); return; } values.remove(value); prefs.save(combo,values); candidates(combo,true); }),1);
                row.setOnDragListener((v,event)-> {
                    if(event.getAction()==DragEvent.ACTION_DRAG_STARTED) return event.getLocalState() instanceof String;
                    if(event.getAction()==DragEvent.ACTION_DROP) { List<String> values=prefs.list(combo); String source=(String)event.getLocalState(); int target=values.indexOf(value); if(values.remove(source)) { values.add(target,source); prefs.save(combo,values); host.post(()->candidates(combo,true)); } }
                    return true;
                });
            }
        }
        if(editing) { Button add = button("＋ 新增候选",()->{editingCombo=combo;draft=""; addCandidate();}); root.addView(add,new LinearLayout.LayoutParams(-1,dp(46))); }
    }
    private void addCandidate() {
        panel(false); toolbar("新增候选",null,null);
        TextView value = new TextView(this); value.setText("候选："+draft+"  （"+(editingCombo ? "2–8 个字母" : "1 个字母")+"）"); value.setTextColor(Color.rgb(235,241,247)); value.setTextSize(18); root.addView(value);
        String[] rows={"abcdefghi","jklmnopqr","stuvwxyz"};
        for(String letters:rows) { LinearLayout row=new LinearLayout(this); add(root,row,1); for(char ch:letters.toCharArray()) { String s=String.valueOf(ch); add(row,button(s,()->{if(draft.length() < (editingCombo?8:1)) draft+=s; addCandidate();}),1); } }
        LinearLayout actions=new LinearLayout(this); add(root,actions,1);
        add(actions,button("取消",()->candidates(editingCombo,true)),1);
        add(actions,button("退格",()->{if(!draft.isEmpty()) draft=draft.substring(0,draft.length()-1); addCandidate();}),1);
        add(actions,button("保存",()->{if(draft.length()<(editingCombo?2:1))return; List<String> values=prefs.list(editingCombo); if(!values.contains(draft))values.add(draft); prefs.save(editingCombo,values); draft=""; candidates(editingCombo,true);}),1);
    }
    private void settings() {
        panel(false); toolbar("设置",null,null);
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout options = column(); scroll.addView(options);
        Button layout = button("按键布局",this::openLayoutEditor); options.addView(layout,new LinearLayout.LayoutParams(-1,dp(48)));
        Button resize=button("上下拖动此处调整大小 · "+Math.round(prefs.scale()*100)+"%",()->{});
        final float[] initial=new float[2];
        resize.setOnTouchListener((v,event)->{ if(event.getAction()==MotionEvent.ACTION_DOWN){initial[0]=event.getRawY();initial[1]=prefs.scale();return true;} if(event.getAction()==MotionEvent.ACTION_MOVE){prefs.scale(initial[1]+(initial[0]-event.getRawY())/dp(400)); resize.setText("大小 · "+Math.round(prefs.scale()*100)+"%");return true;} if(event.getAction()==MotionEvent.ACTION_UP){v.performClick();settings();return true;} return true; });
        options.addView(resize,new LinearLayout.LayoutParams(-1,dp(48)));
        Switch vibration=new Switch(this); vibration.setText("按键震动"); vibration.setTextColor(Color.rgb(235,241,247)); vibration.setChecked(prefs.vibration()); vibration.setOnCheckedChangeListener((b,checked)->prefs.vibration(checked)); options.addView(vibration,new LinearLayout.LayoutParams(-1,dp(48)));
        options.addView(button("恢复默认大小",()->{prefs.scale(1);settings();}),new LinearLayout.LayoutParams(-1,dp(48)));
        options.addView(button("恢复默认候选",()->{prefs.resetCandidates();Toast.makeText(this,"候选已恢复为 zp、c",Toast.LENGTH_SHORT).show();}),new LinearLayout.LayoutParams(-1,dp(48)));
        options.addView(button("切换其他输入法",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()),new LinearLayout.LayoutParams(-1,dp(48)));
    }
}

