package com.sssxzxz.productkeyboard;

import android.test.AndroidTestCase;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.inputmethod.BaseInputConnection;
import java.util.Arrays;

public class KeyboardTest extends AndroidTestCase {
    public void testCustomCharacterLimits() {
        assertFalse(CustomText.valid(""));
        assertTrue(CustomText.valid("1234567890"));
        assertFalse(CustomText.valid("12345678901"));
        assertTrue(CustomText.valid("你好+−×÷,A1"));
        assertEquals(1,CustomText.length("😀"));
        assertEquals(1,CustomText.length("e\u0301"));
        assertTrue(CustomText.valid("😀😀😀😀😀😀😀😀😀😀"));
        assertFalse(CustomText.valid("😀😀😀😀😀😀😀😀😀😀😀"));
    }
    public void testBothCustomSlotsRoundTripAndRename() {
        java.util.List<String> values = Arrays.asList("A12", "你好", ",", "a,b", "+−×÷", "😀", "\"\\", " x ", "a\nb");
        for (boolean first : new boolean[]{true,false}) {
            Preferences p = new Preferences(getContext());
            p.save(first,values); p.select(first,"a,b");
            p = new Preferences(getContext());
            assertEquals(values,p.list(first)); assertEquals("a,b",p.current(first));
            p.replace(first,"a,b","更新"); assertEquals("更新",p.current(first));
            assertEquals("更新",p.list(first).get(3));
            try { p.replace(first,"更新","你好"); fail("Duplicate accepted"); } catch (IllegalArgumentException expected) { }
            assertEquals("更新",p.current(first));
            try { p.save(first,Arrays.asList("12345678901")); fail("Too long"); } catch (IllegalArgumentException expected) { }
            try { p.save(first,java.util.Collections.emptyList()); fail("Empty list"); } catch (IllegalArgumentException expected) { }
        }
    }
    public void testLegacyMigrationAndIndependentReset() {
        getContext().getSharedPreferences("keyboard",0).edit().putString("combos","zp,ab")
            .putString("combo","ab").putString("letters","c,x").putString("letter","x").commit();
        Preferences p = new Preferences(getContext());
        assertEquals(Arrays.asList("zp","ab"),p.list(true)); assertEquals("ab",p.current(true));
        assertEquals("x",p.current(false));
        p.replace(true,"ab","+,中");
        p = new Preferences(getContext()); assertEquals("+,中",p.current(true)); assertEquals("x",p.current(false));
        p.resetCandidates(); assertEquals(Arrays.asList("zp"),p.list(true)); assertEquals(Arrays.asList("c"),p.list(false));
    }
    public void testLayoutPersistsAndResetPreservesOtherSettings() {
        Preferences p = new Preferences(getContext());
        assertFalse(p.functionsOnRight()); assertEquals(Preferences.defaultOrder(),p.functionOrder());
        p.scale(.8f); p.vibration(true); p.save(true,Arrays.asList("zp","ab")); p.select(true,"ab");
        java.util.List<String> order = Arrays.asList("delete","minus","clear","letter","combo");
        p.saveLayout(true,order);
        Preferences loaded = new Preferences(getContext()); assertTrue(loaded.functionsOnRight()); assertEquals(order,loaded.functionOrder());
        java.util.List<String> draft = loaded.functionOrder(); java.util.Collections.reverse(draft);
        assertEquals(order,loaded.functionOrder());
        loaded.saveLayout(false,Preferences.defaultOrder());
        assertFalse(loaded.functionsOnRight()); assertEquals(Preferences.defaultOrder(),loaded.functionOrder());
        assertEquals(.8f,loaded.scale()); assertTrue(loaded.vibration()); assertEquals("ab",loaded.current(true)); assertEquals(Arrays.asList("zp","ab"),loaded.list(true));
    }
    public void testInvalidLayoutCannotRemoveSettingsKey() {
        Preferences p = new Preferences(getContext());
        try { p.saveLayout(true,Arrays.asList("delete","minus","combo","letter","combo")); fail("Invalid order accepted"); } catch (IllegalArgumentException expected) { }
        assertFalse(p.functionsOnRight()); assertEquals(Preferences.defaultOrder(),p.functionOrder());
        getContext().getSharedPreferences("keyboard",0).edit().putString("functionOrder","bad").commit();
        assertEquals(Preferences.defaultOrder(),new Preferences(getContext()).functionOrder());
    }
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
