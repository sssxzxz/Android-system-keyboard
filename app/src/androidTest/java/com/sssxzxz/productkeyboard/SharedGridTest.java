package com.sssxzxz.productkeyboard;
import android.test.AndroidTestCase;
import java.util.*;

public class SharedGridTest extends AndroidTestCase {
    @Override protected void setUp() throws Exception { super.setUp(); getContext().getSharedPreferences("keyboard",0).edit().clear().commit(); }
    public void testMigrationMergesOnceAndPreservesBindingsAndOtherSettings() {
        Preferences p=new Preferences(getContext());
        p.save(true,Arrays.asList("zp","A","a,b")); p.select(true,"A");
        p.save(false,Arrays.asList("c","zp","a","你好")); p.select(false,"你好");
        p.scale(.8f); p.hideOnOutsideTouch(false); p.models(Arrays.asList("089"));
        CustomLibrary library=p.customs();
        assertEquals(Arrays.asList("zp","A","a,b","c","a","你好"),library.items());
        assertEquals("A",library.current(0)); assertEquals("你好",library.current(1));
        for(int i=2;i<10;i++) assertEquals("zp",library.current(i));
        library.select(9,"a,b"); library.add("×÷");
        p.save(true,Arrays.asList("legacy"));
        CustomLibrary reloaded=new Preferences(getContext()).customs();
        assertFalse(reloaded.items().contains("legacy")); assertEquals("a,b",reloaded.current(9));
        assertEquals(.8f,p.scale()); assertFalse(p.hideOnOutsideTouch()); assertEquals(Arrays.asList("089"),p.models());
    }
    public void testSharedRenameDeleteAndReorder() {
        CustomLibrary lib=new Preferences(getContext()).customs();
        lib.add("A1"); lib.select(1,"A1"); lib.select(9,"A1");
        lib.replace("A1","中文+"); assertEquals("中文+",lib.current(1)); assertEquals("中文+",lib.current(9)); assertEquals("zp",lib.current(0));
        List<String> reverse=lib.items(); Collections.reverse(reverse); lib.reorder(reverse); assertEquals(reverse,lib.items());
        try {lib.delete("中文+",null); fail("In-use deletion needs replacement");} catch(IllegalArgumentException expected) { }
        assertEquals("中文+",lib.current(1));
        lib.delete("中文+","c"); assertEquals("c",lib.current(1)); assertEquals("c",lib.current(9));
        try {lib.replace("zp","c"); fail("Duplicate accepted");} catch(IllegalArgumentException expected) { }
        try {lib.add("12345678901");fail("Length accepted");} catch(IllegalArgumentException expected) { }
        lib.delete("zp","c");
        try {lib.delete("c",null);fail("Last item removed");} catch(IllegalArgumentException expected) { }
    }
    public void testLegacyCommaFormatAndMissingBindingMigration() {
        getContext().getSharedPreferences("keyboard",0).edit().putString("combos","zp,ab").putString("letters","c,ab")
            .putString("combo","Z").putString("letter","c").commit();
        CustomLibrary lib=new Preferences(getContext()).customs();
        assertEquals(Arrays.asList("zp","ab","c","Z"),lib.items()); assertEquals("Z",lib.current(0));
    }
    public void testEveryMovePreservesAllKeysAndWideZero() {
        List<String> original=KeyLayout.defaults(); assertTrue(KeyLayout.valid(original));
        assertEquals("clear",original.get(0)); assertEquals("1",original.get(2)); assertEquals("search",original.get(19));
        for(String key:original) if(!key.equals("tail")) for(int target=0;target<25;target++) {
            List<String> moved=KeyLayout.move(original,key,target);
            if(moved!=null) {assertTrue(KeyLayout.valid(moved)); assertEquals(new HashSet<>(original),new HashSet<>(moved));}
        }
        assertNull(KeyLayout.move(original,"0",4));
        assertEquals(original,KeyLayout.mirror(KeyLayout.mirror(original)));
        List<String> moved=KeyLayout.move(original,"0",0); assertEquals("0",moved.get(0)); assertEquals("tail",moved.get(1));
        assertEquals("0",original.get(17));
    }
    public void testLayoutPersistsWithoutChangingSharedLibrary() {
        Preferences p=new Preferences(getContext()); CustomLibrary lib=p.customs(); lib.select(9,"c");
        List<String> moved=KeyLayout.move(KeyLayout.defaults(),"clear",24); p.saveGridLayout(moved);
        assertEquals(moved,new Preferences(getContext()).gridLayout());
        p.saveGridLayout(KeyLayout.defaults()); assertEquals("c",p.customs().current(9));
        List<String> broken=KeyLayout.defaults(); Collections.swap(broken,17,4);
        try {p.saveGridLayout(broken);fail("Invalid zero saved");} catch(IllegalArgumentException expected) { }
        assertEquals(KeyLayout.defaults(),p.gridLayout());
    }
}
