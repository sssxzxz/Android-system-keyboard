package com.sssxzxz.productkeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Only the token touching the cursor is matched and replaced. */
final class ModelMatcher {
    static final int CONTEXT_LIMIT = 256;
    static String prefix(CharSequence before) {
        if (before == null) return "";
        int start = before.length();
        while (start > 0 && !Character.isWhitespace(before.charAt(start - 1))) start--;
        // A full context window without a boundary could be a truncated token.
        if (start == 0 && before.length() >= CONTEXT_LIMIT) return "";
        return before.subSequence(start, before.length()).toString();
    }
    static int suffixLength(CharSequence after) {
        if (after == null) return -1;
        int end = 0;
        while (end < after.length() && !Character.isWhitespace(after.charAt(end))) end++;
        return end == CONTEXT_LIMIT ? -1 : end;
    }
    static List<String> match(List<String> models, String prefix) {
        List<String> result = new ArrayList<>();
        if (prefix.isEmpty()) return result;
        String query = prefix.toLowerCase(Locale.ROOT);
        for (String model : models) {
            if (model.toLowerCase(Locale.ROOT).contains(query)) result.add(model);
            if (result.size() == 30) break;
        }
        return result;
    }
}
