package com.sssxzxz.productkeyboard;

import android.test.AndroidTestCase;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.inputmethod.BaseInputConnection;
import java.util.Arrays;

public class KeyboardTest extends AndroidTestCase {
    public void testSearchSendsOnlySearchActionWithoutChangingText() {
        final int[] calls = {0};
        BaseInputConnection ic = new BaseInputConnection(new android.view.View(getContext()),true) {
            @Override public boolean performEditorAction(int action) {
                assertEquals(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH, action);
                calls[0]++; return true;
            }
            @Override public boolean commitText(CharSequence text, int cursor) { fail("Search must not insert text"); return false; }
            @Override public boolean sendKeyEvent(android.view.KeyEvent event) { fail("Search must not send Enter"); return false; }
        };
        assertTrue(ProductIme.search(ic)); assertEquals(1,calls[0]); assertFalse(ProductIme.search(null));
    }
    @Override protected void setUp() throws Exception {
        super.setUp(); getContext().getSharedPreferences("keyboard",0).edit().clear().commit();
    }
    public void testPreferencesPersistAndRepairDeletedSelection() {
        Preferences p = new Preferences(getContext());
        p.save(true, Arrays.asList("zp", "ab", "xy")); p.select(true,"ab");
        p.save(true, Arrays.asList("xy", "zp", "ab"));
        Preferences reloaded = new Preferences(getContext());
        assertEquals(Arrays.asList("xy","zp","ab"),reloaded.list(true)); assertEquals("ab",reloaded.current(true));
        reloaded.save(true,Arrays.asList("xy","zp")); assertEquals("xy",reloaded.current(true));
        reloaded.select(false,"c"); reloaded.vibration(true); reloaded.scale(.8f);
        Preferences again = new Preferences(getContext()); assertTrue(again.vibration()); assertEquals(.8f,again.scale());
        again.resetCandidates(); assertEquals(Arrays.asList("zp"),again.list(true)); assertEquals("c",again.current(false));
        assertEquals(.8f,again.scale()); assertTrue(again.vibration());
    }
    public void testScaleBounds() {
        Preferences p = new Preferences(getContext()); p.scale(2f); assertEquals(1f,p.scale()); p.scale(0f); assertEquals(.65f,p.scale());
    }
    public void testClearEntireFieldFromMiddleWithoutClipboard() {
        final Editable content = new SpannableStringBuilder("zp123\nc456😀"); Selection.setSelection(content,3);
        BaseInputConnection ic = new BaseInputConnection(new android.view.View(getContext()),true) {
            @Override public Editable getEditable() { return content; }
            @Override public boolean performContextMenuAction(int id) {
                assertEquals(android.R.id.selectAll,id); Selection.selectAll(content); return true;
            }
        };
        new ProductIme().clear(ic); assertEquals("",content.toString());
    }
}
