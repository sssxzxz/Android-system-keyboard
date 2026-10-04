package com.sssxzxz.productkeyboard;

import android.icu.text.BreakIterator;
import java.util.Locale;

/** One user-perceived character may contain several UTF-16 units (e.g. emoji). */
final class CustomText {
    static int length(String value) {
        BreakIterator boundaries = BreakIterator.getCharacterInstance(Locale.ROOT);
        boundaries.setText(value);
        int count = 0;
        boundaries.first();
        while (boundaries.next() != BreakIterator.DONE) count++;
        return count;
    }
    static boolean valid(String value) {
        return value != null && !value.isEmpty() && length(value) <= 10;
    }
}
