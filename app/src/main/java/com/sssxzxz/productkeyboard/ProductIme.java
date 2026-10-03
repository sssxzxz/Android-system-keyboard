package com.sssxzxz.productkeyboard;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.widget.TextView;
public class ProductIme extends InputMethodService {
    @Override public View onCreateInputView() { TextView view = new TextView(this); view.setText("商品键盘"); return view; }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
}
