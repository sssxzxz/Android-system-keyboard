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
    private HorizontalScrollView modelBar;
    private LinearLayout modelItems;
    private List<String> modelCatalog = Collections.emptyList();
    private boolean privateEditor;
    private boolean keyboardTouching;
    private final CandidateVisibility candidateVisibility = new CandidateVisibility();
    private boolean completingModel;
    private int extractionToken;
    private final android.os.Handler suggestionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable suggestionRefresh = this::refreshModelsNow;
    private SuggestionQuery suggestionQuery;
    private List<String> displayedModels = Collections.emptyList();


    private List<String> layoutDraft;
    private String selectedLayoutKey;
    private int keyboardHeight;
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public View onCreateInputView() {
        prefs = new Preferences(this);
        host = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) keyboardTouching = true;
                try { return super.dispatchTouchEvent(event); }
                finally {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) keyboardTouching = false;
                }
            }
        }; host.setBackgroundColor(Color.rgb(18,23,30));
        getWindow().getWindow().setNavigationBarColor(Color.rgb(18,23,30));
        getWindow().getWindow().getDecorView().setSystemUiVisibility(0);
        // Observe the system's outside-down notification without covering the host app.
        getWindow().getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        getWindow().getWindow().getDecorView().setOnTouchListener((view,event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            if (prefs.hideOnOutsideTouch() && isInputViewShown()) requestHideSelf(0);
            return true;
        });
        host.setPadding(0, 0, 0, dp(24));
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, 0, 0, Math.max(dp(24), insets.getSystemWindowInsetBottom()));
            return insets;
        });
        showKeyboard(); return host;
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        int variation = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
        int type = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
        privateEditor = (type == android.text.InputType.TYPE_CLASS_TEXT &&
                (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                 variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                 variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (type == android.text.InputType.TYPE_CLASS_NUMBER && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        if (prefs != null) modelCatalog = prefs.models();
        candidateVisibility.beginEditor(editorText(true));
        if (host != null) showKeyboard();
    }
    private String editorText(boolean monitor) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || privateEditor) return null;
        ExtractedTextRequest request = new ExtractedTextRequest();
        if (monitor) extractionToken++;
        request.token = extractionToken;
        request.hintMaxChars = 8192;
        request.hintMaxLines = 100;
        ExtractedText text = ic.getExtractedText(request, monitor ? InputConnection.GET_EXTRACTED_TEXT_MONITOR : 0);
        // Only compare full snapshots, never cursor-relative or partial windows.
        if (text == null || text.text == null || text.startOffset != 0 || text.partialStartOffset >= 0) return null;
        return text.text.toString();
    }
    @Override public void onUpdateExtractedText(int token, ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        if (token == extractionToken) refreshModels();
    }
    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        refreshModels();
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        suggestionHandler.removeCallbacks(suggestionRefresh);
        if (suggestionQuery != null) suggestionQuery.invalidate();
        displayedModels = Collections.emptyList();
        modelBar = null; modelItems = null;
        keyboardTouching = false;
        super.onFinishInputView(finishingInput);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void panel(boolean keyboard) {
        suggestionHandler.removeCallbacks(suggestionRefresh);
        if (suggestionQuery != null) suggestionQuery.invalidate();
        displayedModels = Collections.emptyList();
        modelBar = null; modelItems = null;
        host.removeAllViews(); root = column();
        float screenWidth = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        float screenHeight = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;
        float base = Math.min(screenWidth, Math.min(480f, screenHeight * .72f));
        unit = base / 400f * prefs.scale();
        keyboardHeight = dp(Math.min(Math.max(250,360*unit),screenHeight*.62f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(base * prefs.scale()), keyboard ? keyboardHeight : dp(Math.min(330, screenHeight * .65f)), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        root.setPadding(dp(4*unit), dp(4*unit), dp(4*unit), dp(4*unit));
        host.addView(root, lp);
    }
    private Button button(String label, Runnable action) {
        return button(label, action, false);
    }
    private Button inputButton(String label, Runnable action) {
        KeyboardButton b = (KeyboardButton) keyButton(label, action);
        b.setCommitOnDown(true);
        return b;
    }
    private Button keyButton(String label, Runnable action) {
        return button(label, action, true);
    }
    private Button button(String label, Runnable action, boolean keyboardKey) {
        Button b = keyboardKey ? new KeyboardButton(this) : new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(Math.max(12, (label.contains("\n") ? 16 : 21)*unit));
        b.setIncludeFontPadding(false);
        b.setTextColor(Color.rgb(235,241,247)); b.setPadding(0,0,0,0); b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(39,49,62)); bg.setCornerRadius(dp(8*unit));
        if (keyboardKey) {
            GradientDrawable pressed = new GradientDrawable(); pressed.setColor(Color.rgb(65,100,108)); pressed.setCornerRadius(dp(8*unit));
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, pressed);
            states.addState(new int[]{}, bg);
            b.setBackground(states);
            b.setStateListAnimator(null);
        } else {
            b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x5578DDD0), bg, null));
        }
        b.setSoundEffectsEnabled(false);
        b.setOnClickListener(v -> { feedback(v); action.run(); }); return b;
    }
    private void feedback(View view) { if (prefs.vibration()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); }
    private void add(LinearLayout parent, View child, float weight) {
        boolean vertical = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(vertical ? -1 : 0, vertical ? 0 : -1, weight);
        lp.setMargins(dp(2*unit),dp(2*unit),dp(2*unit),dp(2*unit)); parent.addView(child, lp);
    }
    private void commit(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && ic.commitText(value,1)) candidateVisibility.edited();
        refreshModels();
    }
    private void refreshModels() {
        suggestionHandler.removeCallbacks(suggestionRefresh);
        if (suggestionQuery != null) suggestionQuery.invalidate();
        if (modelBar != null) suggestionHandler.postDelayed(suggestionRefresh, 80);
    }
    @Override public void onDestroy() {
        suggestionHandler.removeCallbacks(suggestionRefresh);
        if (suggestionQuery != null) suggestionQuery.invalidate();
        if (suggestionQuery != null) suggestionQuery.close();
        super.onDestroy();
    }
    private void refreshModelsNow() {
        if (modelBar == null || completingModel) return;
        if (keyboardTouching) { refreshModels(); return; }
        InputConnection ic = getCurrentInputConnection();
        if (!prefs.modelSuggestions() || modelCatalog.isEmpty() || privateEditor || ic == null) {
            renderModels(Collections.emptyList());
            return;
        }
        if (suggestionQuery == null) suggestionQuery = new SuggestionQuery();
        suggestionQuery.request(ic, modelCatalog, candidateVisibility.dismissed(), (matches, snapshot) -> {
            if (modelBar == null) return;
            if (keyboardTouching) { refreshModels(); return; }
            if (candidateVisibility.dismissed()) candidateVisibility.observe(snapshot);
            renderModels(candidateVisibility.dismissed() ? Collections.emptyList() : matches);
        });
    }
    private void renderModels(List<String> matches) {
        if (displayedModels.equals(matches)) return;
        displayedModels = new ArrayList<>(matches);
        modelItems.removeAllViews();
        for (String model : matches) {
            Button choice = button(model, () -> completeModel(model));
            choice.setTextSize(18); choice.setPadding(dp(12),0,dp(12),0);
            choice.setContentDescription("输入型号 " + model);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,-1);
            lp.setMargins(dp(2),dp(2),dp(2),dp(2)); modelItems.addView(choice,lp);
        }
        boolean visible = !matches.isEmpty();
        modelBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        modelBar.scrollTo(0,0);
        android.view.ViewGroup.LayoutParams lp = root.getLayoutParams();
        int height = keyboardHeight + (visible ? dp(48) : 0);
        if (lp.height != height) { lp.height = height; root.setLayoutParams(lp); }
    }
    private void completeModel(String model) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() != 0) { refreshModels(); return; }
        String prefix = ModelMatcher.prefix(ic.getTextBeforeCursor(ModelMatcher.CONTEXT_LIMIT,0));
        int suffix = ModelMatcher.suffixLength(ic.getTextAfterCursor(ModelMatcher.CONTEXT_LIMIT,0));
        if (suffix < 0 || !ModelMatcher.match(modelCatalog,prefix).contains(model)) { refreshModels(); return; }
        completingModel = true;
        ic.beginBatchEdit();
        try {
            ic.finishComposingText();
            if (ic.deleteSurroundingText(prefix.length(),suffix) && ic.commitText(model,1)) {
                candidateVisibility.dismiss(editorText(false));
            }
        } finally { ic.endBatchEdit(); completingModel = false; }
        suggestionHandler.removeCallbacks(suggestionRefresh);
        if (suggestionQuery != null) suggestionQuery.invalidate();
        if (candidateVisibility.dismissed()) renderModels(Collections.emptyList());
        else refreshModels();
    }
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
            if (ic.performContextMenuAction(android.R.id.selectAll)) {
                if (ic.commitText("", 1)) candidateVisibility.edited();
            }
            else Toast.makeText(this,"此输入框不支持全清，请在应用内选择全部后删除",Toast.LENGTH_SHORT).show();
        } finally { ic.endBatchEdit(); }
        refreshModels();
    }
    private void backspace() {
        InputConnection ic = getCurrentInputConnection(); if (ic == null) return;
        CharSequence selection = ic.getSelectedText(0);
        boolean changed = selection != null && selection.length() > 0 ? ic.commitText("",1) : ic.deleteSurroundingTextInCodePoints(1,0);
        if (changed) candidateVisibility.edited();
        refreshModels();
    }
    private void longPress(Button button, Runnable action) { button.setOnLongClickListener(v -> { feedback(v); action.run(); return true; }); }
    private void showKeyboard() {
        panel(true);
        modelCatalog = prefs.models();
        modelBar = new HorizontalScrollView(this); modelBar.setHorizontalScrollBarEnabled(false); modelBar.setVisibility(View.GONE);
        modelItems = new LinearLayout(this); modelBar.addView(modelItems,new HorizontalScrollView.LayoutParams(-2,-1));
        FrameLayout keyboardFrame = new FrameLayout(this);
        root.addView(keyboardFrame,new LinearLayout.LayoutParams(-1,-1));
        keyboardFrame.addView(modelBar,new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP));
        KeyboardGrid grid = new KeyboardGrid(this);
        keyboardFrame.addView(grid,new FrameLayout.LayoutParams(-1,keyboardHeight-root.getPaddingTop()-root.getPaddingBottom(),Gravity.BOTTOM));
        List<String> keys=prefs.gridLayout();
        for(int i=0;i<25;i++) if(!keys.get(i).equals("tail")) grid.put(functionButton(keys.get(i)),i,keys.get(i).equals("0")?2:1);
        refreshModels();
    }
    private String functionName(String key) {
        if(key.startsWith("custom")) return "自定义 " + (Integer.parseInt(key.substring(6))+1);
        switch(key) {
            case "clear": return "全清";
            case "minus": return "减号";
            case "delete": return "退格";
            case "search": return "搜索";
            default: return key;
        }
    }
    private String keyLabel(String key) {
        if(key.startsWith("custom")) return prefs.customs().current(Integer.parseInt(key.substring(6)));
        return key.equals("minus")?"−":functionName(key);
    }
    private Button functionButton(String key) {
        Button b;
        if(key.startsWith("custom")) {
            int slot=Integer.parseInt(key.substring(6)); String value=prefs.customs().current(slot);
            b=keyButton(value,()->commit(value));
            b.setContentDescription(value+"，"+functionName(key)+"，长按选择公共库");
            longPress(b,()->candidates(slot,false));
        } else switch(key) {
            case "clear": b=keyButton("全清",()->clear(getCurrentInputConnection())); longPress(b,this::settings); break;
            case "minus": b=inputButton("−",()->commit("-")); break;
            case "delete": b=keyButton("退格",this::backspace); break;
            case "search": b=keyButton("搜索",this::search); b.setTextColor(Color.rgb(120,221,208)); break;
            default: b=inputButton(key,()->commit(key));
        }
        fitContent(b); return b;
    }
    private void fitContent(Button b) {
        b.setMaxLines(2);
        b.setPadding(dp(3),0,dp(3),0);
        b.setAutoSizeTextTypeUniformWithConfiguration(8, Math.max(12,Math.round(21*unit)), 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    }
    private void editCandidate(String original) {
        android.content.Intent intent = new android.content.Intent(this, CandidateEditorActivity.class);
        if (original != null) intent.putExtra("original",original);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    private void openLayoutEditor() {
        layoutDraft=prefs.gridLayout(); selectedLayoutKey=null; layoutEditor();
    }
    private void moveLayout(String source,int target) {
        List<String> moved=KeyLayout.move(layoutDraft,source,target);
        if(moved==null) { Toast.makeText(this,"0 需要同行连续两格，请选择其他位置",Toast.LENGTH_SHORT).show(); return; }
        layoutDraft=moved; selectedLayoutKey=null; layoutEditor();
    }
    private void layoutEditor() {
        panel(false);
        LinearLayout header=new LinearLayout(this); root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        add(header,button("取消",this::settings),1);
        Button mirror=button("左右镜像",()->{layoutDraft=KeyLayout.mirror(layoutDraft);selectedLayoutKey=null;layoutEditor();}); mirror.setTextSize(16); add(header,mirror,2);
        TextView hint=new TextView(this); hint.setText("长按拖动换位，落点高亮；也可依次点选两个键。0 始终占两格。"); hint.setTextSize(12); hint.setTextColor(Color.rgb(179,192,205)); root.addView(hint);
        ScrollView scroll=new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        KeyboardGrid grid=new KeyboardGrid(this); scroll.addView(grid,new ScrollView.LayoutParams(-1,dp(300)));
        for(int i=0;i<25;i++) {
            String key=layoutDraft.get(i); if(key.equals("tail")) continue;
            final int position=i;
            Button item=button(keyLabel(key),()->{
                if(selectedLayoutKey==null) {selectedLayoutKey=key;layoutEditor();}
                else moveLayout(selectedLayoutKey,position);
            });
            fitContent(item); item.setContentDescription("调整位置："+functionName(key));
            if(key.equals(selectedLayoutKey)) item.setTextColor(Color.rgb(120,221,208));
            item.setOnLongClickListener(v->{selectedLayoutKey=null;return v.startDragAndDrop(ClipData.newPlainText("layout",key),new View.DragShadowBuilder(v),key,0);});
            item.setOnDragListener((v,event)->{
                switch(event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED: return event.getLocalState() instanceof String && layoutDraft.contains((String)event.getLocalState());
                    case DragEvent.ACTION_DRAG_ENTERED: v.setAlpha(.45f); return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED: v.setAlpha(1f); return true;
                    case DragEvent.ACTION_DROP:
                        String source=(String)event.getLocalState(); host.post(()->moveLayout(source,position)); return true;
                    default: return true;
                }
            });
            grid.put(item,i,key.equals("0")?2:1);
        }
        LinearLayout actions=new LinearLayout(this); root.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));
        Button reset=button("恢复默认",()->{layoutDraft=KeyLayout.defaults();selectedLayoutKey=null;layoutEditor();}); reset.setTextSize(16); add(actions,reset,1);
        add(actions,button("保存",()->{prefs.saveGridLayout(layoutDraft);showKeyboard();}),1);
    }
    private void toolbar(String title, String action, Runnable run) {
        LinearLayout bar = new LinearLayout(this); root.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));
        add(bar,button("返回",this::showKeyboard),1); add(bar,button(title,()->{}),2); if(action != null) add(bar,button(action,run),1);
    }
    private void candidates(int slot, boolean editing) {
        panel(false); toolbar("公共库 · "+(slot+1),editing?"完成":"编辑",()->candidates(slot,!editing));
        ScrollView scroll=new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=column(); scroll.addView(list);
        for(String value:prefs.customs().items()) {
            LinearLayout item=column(); list.addView(item);
            Button select=button((value.equals(prefs.customs().current(slot))?"✓ ":"")+value,()->{prefs.customs().select(slot,value);if(editing)candidates(slot,true);else showKeyboard();});
            fitContent(select); item.addView(select,new LinearLayout.LayoutParams(-1,dp(48)));
            if(editing) {
                LinearLayout actions=new LinearLayout(this); item.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));
                Button modify=button("修改",()->editCandidate(value)); modify.setTextSize(14); add(actions,modify,1);
                Button handle=button("↕",()->Toast.makeText(this,"长按拖动排序，所有自定义键共用此顺序",Toast.LENGTH_SHORT).show());
                handle.setContentDescription("长按拖动排序 "+value);
                longPress(handle,()->handle.startDragAndDrop(ClipData.newPlainText("candidate",value),new View.DragShadowBuilder(handle),value,0)); add(actions,handle,1);
                Button remove=button("删除",()->deleteCandidate(slot,value)); remove.setTextSize(14); add(actions,remove,1);
                item.setOnDragListener((v,event)->{
                    if(event.getAction()==DragEvent.ACTION_DRAG_STARTED) return event.getLocalState() instanceof String && prefs.customs().items().contains(event.getLocalState());
                    if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED) v.setAlpha(.5f);
                    if(event.getAction()==DragEvent.ACTION_DRAG_EXITED || event.getAction()==DragEvent.ACTION_DRAG_ENDED) v.setAlpha(1f);
                    if(event.getAction()==DragEvent.ACTION_DROP) {
                        List<String> values=prefs.customs().items(); String source=(String)event.getLocalState(); int target=values.indexOf(value);
                        if(target>=0 && values.remove(source)) {values.add(target,source);prefs.customs().reorder(values);host.post(()->candidates(slot,true));}
                    }
                    return true;
                });
            }
        }
        if(editing) root.addView(button("＋ 添加到公共库",()->editCandidate(null)),new LinearLayout.LayoutParams(-1,dp(46)));
    }
    private void deleteCandidate(int slot,String value) {
        CustomLibrary library=prefs.customs(); List<String> values=library.items();
        if(values.size()==1) {Toast.makeText(this,"至少保留一个候选",Toast.LENGTH_SHORT).show();return;}
        List<Integer> users=library.users(value);
        if(users.isEmpty()) {library.delete(value,null);candidates(slot,true);return;}
        panel(false);
        Button cancel=button("取消删除",()->candidates(slot,true)); root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(44)));
        List<String> names=new ArrayList<>(); for(int user:users) names.add(Integer.toString(user+1));
        TextView hint=new TextView(this); hint.setText("删除「"+value+"」将影响自定义键 "+String.join("、",names)+"。请选择替代内容，选定后才会删除：");
        hint.setTextColor(Color.rgb(235,241,247)); hint.setTextSize(14); root.addView(hint);
        ScrollView scroll=new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout list=column();scroll.addView(list);
        for(String replacement:values) if(!replacement.equals(value)) {
            Button choose=button(replacement,()->{prefs.customs().delete(value,replacement);candidates(slot,true);}); fitContent(choose); list.addView(choose,new LinearLayout.LayoutParams(-1,dp(48)));
        }
    }
    private void resetCustomLibrary() {
        panel(false); toolbar("恢复公共库",null,null);
        TextView hint=new TextView(this); hint.setText("这会删除公共库自定义内容并重置全部 10 个自定义键。按键布局、型号库和其他设置不变。");
        hint.setTextColor(Color.rgb(235,241,247)); hint.setTextSize(16); root.addView(hint);
        root.addView(button("取消",this::settings),new LinearLayout.LayoutParams(-1,dp(48)));
        root.addView(button("确认恢复",()->{prefs.resetCandidates();settings();}),new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void settings() {
        panel(false); toolbar("设置",null,null);
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout options = column(); scroll.addView(options);
        Switch outside = new Switch(this); outside.setText("触碰键盘外区域时收起");
        outside.setTextSize(16); outside.setTextColor(Color.rgb(235,241,247));
        outside.setChecked(prefs.hideOnOutsideTouch());
        outside.setOnCheckedChangeListener((button,checked) -> prefs.hideOnOutsideTouch(checked));
        options.addView(outside,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView outsideHint = new TextView(this);
        outsideHint.setText("默认开启。触碰上方表格或其他键盘外位置即收起，不必等拖动。");
        outsideHint.setTextColor(Color.rgb(179,192,205)); outsideHint.setTextSize(12);
        options.addView(outsideHint);
        Switch suggestions = new Switch(this); suggestions.setText("输入后显示型号候选");
        suggestions.setTextColor(Color.rgb(235,241,247)); suggestions.setChecked(prefs.modelSuggestions());
        suggestions.setOnCheckedChangeListener((b,checked) -> prefs.modelSuggestions(checked));
        options.addView(suggestions,new LinearLayout.LayoutParams(-1,dp(48)));
        Button models = button("导入型号（已保存 " + prefs.models().size() + " 个）", () -> {
            android.content.Intent intent = new android.content.Intent(this, ModelImportActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent);
        }); models.setTextSize(17); options.addView(models,new LinearLayout.LayoutParams(-1,dp(48)));
        Button layout = button("按键布局",this::openLayoutEditor); options.addView(layout,new LinearLayout.LayoutParams(-1,dp(48)));
        Button resize=button("上下拖动此处调整大小 · "+Math.round(prefs.scale()*100)+"%",()->{});
        final float[] initial=new float[2];
        resize.setOnTouchListener((v,event)->{ if(event.getAction()==MotionEvent.ACTION_DOWN){initial[0]=event.getRawY();initial[1]=prefs.scale();return true;} if(event.getAction()==MotionEvent.ACTION_MOVE){prefs.scale(initial[1]+(initial[0]-event.getRawY())/dp(400)); resize.setText("大小 · "+Math.round(prefs.scale()*100)+"%");return true;} if(event.getAction()==MotionEvent.ACTION_UP){v.performClick();settings();return true;} return true; });
        options.addView(resize,new LinearLayout.LayoutParams(-1,dp(48)));
        Switch vibration=new Switch(this); vibration.setText("按键震动"); vibration.setTextColor(Color.rgb(235,241,247)); vibration.setChecked(prefs.vibration()); vibration.setOnCheckedChangeListener((b,checked)->prefs.vibration(checked)); options.addView(vibration,new LinearLayout.LayoutParams(-1,dp(48)));
        options.addView(button("恢复默认大小",()->{prefs.scale(1);settings();}),new LinearLayout.LayoutParams(-1,dp(48)));

        options.addView(button("恢复默认候选（公共库）",this::resetCustomLibrary),new LinearLayout.LayoutParams(-1,dp(48)));
        options.addView(button("切换其他输入法",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()),new LinearLayout.LayoutParams(-1,dp(48)));
    }
}

