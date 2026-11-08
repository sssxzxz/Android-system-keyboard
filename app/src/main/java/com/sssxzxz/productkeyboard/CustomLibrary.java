package com.sssxzxz.productkeyboard;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.*;

/** One library, ten independent bindings. Migration is one atomic preferences edit. */
final class CustomLibrary {
    private static final String ITEMS = "sharedCustom.items";
    private final SharedPreferences data;
    CustomLibrary(SharedPreferences data) {
        this.data = data;
        if (!data.contains(ITEMS)) migrate();
    }
    private List<String> legacy(boolean first) {
        List<String> result = new ArrayList<>();
        String json = data.getString(first ? "custom1.items" : "custom2.items", null);
        if (json != null) result.addAll(decode(json));
        else for (String s : data.getString(first ? "combos" : "letters", first ? "zp" : "c").split(","))
            if (CustomText.valid(s) && !result.contains(s)) result.add(s);
        if (result.isEmpty()) result.add(first ? "zp" : "c");
        return result;
    }
    private static List<String> decode(String json) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i=0;i<array.length();i++) {
                String s=array.getString(i);
                if(CustomText.valid(s) && !result.contains(s)) result.add(s);
            }
        } catch(JSONException ignored) { }
        return result;
    }
    private void migrate() {
        List<String> a=legacy(true), b=legacy(false), all=new ArrayList<>(a);
        for(String s:b) if(!all.contains(s)) all.add(s);
        String[] selected={data.getString("combo","zp"),data.getString("letter","c")};
        // Preserve even a valid legacy binding missing from its old list.
        for(String s:selected) if(CustomText.valid(s) && !all.contains(s)) all.add(s);
        SharedPreferences.Editor edit=data.edit().putString(ITEMS,new JSONArray(all).toString());
        for(int i=0;i<10;i++) edit.putString(binding(i), i<2 && CustomText.valid(selected[i]) ? selected[i] : all.get(0));
        edit.apply();
    }
    private static String binding(int slot) {
        if(slot<0 || slot>=10) throw new IllegalArgumentException("Unknown custom key");
        return "sharedCustom.key"+slot;
    }
    List<String> items() {
        List<String> result=decode(data.getString(ITEMS,"[]"));
        if(result.isEmpty()) result.add("zp");
        return result;
    }
    String current(int slot) {
        List<String> values=items(); String value=data.getString(binding(slot),values.get(0));
        return values.contains(value)?value:values.get(0);
    }
    void select(int slot,String value) {
        if(!items().contains(value)) throw new IllegalArgumentException("Unknown candidate");
        data.edit().putString(binding(slot),value).apply();
    }
    private void validate(List<String> values) {
        if(values.isEmpty() || new HashSet<>(values).size()!=values.size()) throw new IllegalArgumentException("Empty or duplicate library");
        for(String s:values) if(!CustomText.valid(s)) throw new IllegalArgumentException("Use 1–10 characters");
    }
    void add(String value) {
        List<String> values=items(); values.add(value); validate(values);
        data.edit().putString(ITEMS,new JSONArray(values).toString()).apply();
    }
    void reorder(List<String> values) {
        validate(values);
        if(!new HashSet<>(items()).equals(new HashSet<>(values))) throw new IllegalArgumentException("Reorder must preserve candidates");
        data.edit().putString(ITEMS,new JSONArray(values).toString()).apply();
    }
    List<Integer> users(String value) {
        List<Integer> slots=new ArrayList<>();
        for(int i=0;i<10;i++) if(current(i).equals(value)) slots.add(i);
        return slots;
    }
    void replace(String original,String replacement) {
        List<String> values=items(); int index=values.indexOf(original);
        if(index<0) throw new IllegalArgumentException("Candidate removed");
        values.set(index,replacement); validate(values);
        SharedPreferences.Editor edit=data.edit().putString(ITEMS,new JSONArray(values).toString());
        for(int i:users(original)) edit.putString(binding(i),replacement);
        edit.apply();
    }
    void delete(String value,String replacement) {
        List<String> values=items();
        if(!values.remove(value) || values.isEmpty()) throw new IllegalArgumentException("Keep one candidate");
        List<Integer> affected=users(value);
        if(!affected.isEmpty() && !values.contains(replacement)) throw new IllegalArgumentException("Choose replacement");
        SharedPreferences.Editor edit=data.edit().putString(ITEMS,new JSONArray(values).toString());
        for(int i:affected) edit.putString(binding(i),replacement);
        edit.apply();
    }
    static void clear(SharedPreferences.Editor edit) {
        edit.remove(ITEMS);
        for(int i=0;i<10;i++) edit.remove(binding(i));
    }
}
