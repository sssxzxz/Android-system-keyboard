package com.sssxzxz.productkeyboard;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
final class Preferences {
    private final SharedPreferences data;
    Preferences(Context context) { data = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE); }
    // Keep legacy layout IDs and preference keys for seamless upgrades.
    private String listKey(boolean first) { return first ? "custom1.items" : "custom2.items"; }
    private String currentKey(boolean first) { return first ? "combo" : "letter"; }
    List<String> list(boolean first) {
        List<String> values = new ArrayList<>();
        String stored = data.getString(listKey(first), null);
        if (stored != null) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    String value = array.getString(i);
                    if (CustomText.valid(value) && !values.contains(value)) values.add(value);
                }
            } catch (org.json.JSONException ignored) { /* Recover defaults below. */ }
        } else {
            for (String value : data.getString(first ? "combos" : "letters", first ? "zp" : "c").split(",")) {
                if (CustomText.valid(value) && !values.contains(value)) values.add(value);
            }
        }
        if (values.isEmpty()) values.add(first ? "zp" : "c");
        return values;
    }
    String current(boolean first) {
        List<String> values = list(first);
        String selected = data.getString(currentKey(first), first ? "zp" : "c");
        return values.contains(selected) ? selected : values.get(0);
    }
    void select(boolean first, String value) {
        if (!list(first).contains(value)) throw new IllegalArgumentException("Unknown candidate");
        data.edit().putString(currentKey(first), value).apply();
    }
    void save(boolean first, List<String> values) {
        save(first, values, current(first));
    }
    private void save(boolean first, List<String> values, String selected) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Keep one candidate");
        for (String value : values) if (!CustomText.valid(value)) throw new IllegalArgumentException("Use 1–10 characters");
        if (new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Duplicate candidate");
        data.edit().putString(listKey(first), new org.json.JSONArray(values).toString())
            .putString(currentKey(first), values.contains(selected) ? selected : values.get(0)).apply();
    }
    void replace(boolean first, String original, String replacement) {
        List<String> values = list(first);
        int index = values.indexOf(original);
        if (index < 0) throw new IllegalArgumentException("Candidate no longer exists");
        String selected = current(first);
        values.set(index, replacement);
        save(first, values, selected.equals(original) ? replacement : selected);
    }
    float scale() { return Math.max(.65f, Math.min(1f, data.getFloat("scale", 1f))); }
    void scale(float scale) { data.edit().putFloat("scale", Math.max(.65f, Math.min(1f, scale))).apply(); }
    boolean vibration() { return data.getBoolean("vibration", false); }
    void vibration(boolean value) { data.edit().putBoolean("vibration", value).apply(); }
    void resetCandidates() { data.edit().remove("custom1.items").remove("custom2.items").remove("combos").remove("letters").remove("combo").remove("letter").apply(); }
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
