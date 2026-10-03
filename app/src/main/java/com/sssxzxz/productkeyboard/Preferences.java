package com.sssxzxz.productkeyboard;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
final class Preferences {
    private final SharedPreferences data;
    Preferences(Context context) { data = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE); }
    List<String> list(boolean combo) { return new ArrayList<>(Arrays.asList(data.getString(combo ? "combos" : "letters", combo ? "zp" : "c").split(","))); }
    String current(boolean combo) { return data.getString(combo ? "combo" : "letter", combo ? "zp" : "c"); }
    void select(boolean combo, String value) { data.edit().putString(combo ? "combo" : "letter", value).apply(); }
    void save(boolean combo, List<String> values) {
        data.edit().putString(combo ? "combos" : "letters", String.join(",", values)).apply();
        if (!values.contains(current(combo))) select(combo, values.get(0));
    }
    float scale() { return Math.max(.65f, Math.min(1f, data.getFloat("scale", 1f))); }
    void scale(float scale) { data.edit().putFloat("scale", Math.max(.65f, Math.min(1f, scale))).apply(); }
    boolean vibration() { return data.getBoolean("vibration", false); }
    void vibration(boolean value) { data.edit().putBoolean("vibration", value).apply(); }
    void resetCandidates() { data.edit().remove("combos").remove("letters").remove("combo").remove("letter").apply(); }
    static List<String> defaultOrder() { return new ArrayList<>(Arrays.asList("clear", "combo", "letter", "minus", "delete")); }
    List<String> functionOrder() {
        List<String> order = new ArrayList<>(Arrays.asList(data.getString("functionOrder", String.join(",", defaultOrder())).split(",")));
        return validOrder(order) ? order : defaultOrder();
    }
    private static boolean validOrder(List<String> order) {
        return order != null && order.size() == 5 && new HashSet<>(order).equals(new HashSet<>(defaultOrder()));
    }
    boolean functionsOnRight() { return data.getBoolean("functionsOnRight", false); }
    void saveLayout(boolean onRight, List<String> order) {
        if (!validOrder(order)) throw new IllegalArgumentException("All five function keys must occur exactly once");
        data.edit().putBoolean("functionsOnRight", onRight).putString("functionOrder", String.join(",", order)).apply();
    }
}
