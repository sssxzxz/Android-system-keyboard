package com.sssxzxz.productkeyboard;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.util.Collections;
import java.util.List;

/** At most one running query and one pending query; only the latest can publish. */
final class SuggestionQuery {
    interface Callback { void ready(List<String> matches, String snapshot); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("keyboard-suggestions", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler worker;
    private volatile int generation;
    private volatile boolean closed;
    SuggestionQuery() { thread.start(); worker = new Handler(thread.getLooper()); }
    void invalidate() {
        generation++;
        worker.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
    }
    void request(InputConnection connection, List<String> catalog, boolean observeText, Callback callback) {
        invalidate();
        if (closed) return;
        final int request = generation;
        worker.post(() -> {
            if (closed || request != generation) return;
            String snapshot = null;
            List<String> matches = Collections.emptyList();
            try {
                if (observeText) {
                    ExtractedTextRequest extraction = new ExtractedTextRequest();
                    extraction.hintMaxChars = 8192; extraction.hintMaxLines = 100;
                    ExtractedText text = connection.getExtractedText(extraction,0);
                    if (text != null && text.text != null && text.startOffset == 0 && text.partialStartOffset < 0) snapshot = text.text.toString();
                }
                if (closed || request != generation) return;
                CharSequence selected = connection.getSelectedText(0);
                if (closed || request != generation) return;
                if (selected == null || selected.length() == 0) {
                    String prefix = ModelMatcher.prefix(connection.getTextBeforeCursor(ModelMatcher.CONTEXT_LIMIT,0));
                    if (closed || request != generation) return;
                    matches = ModelMatcher.match(catalog,prefix);
                }
            } catch (RuntimeException ignored) {
                // A disconnected or failing editor must not break keyboard input.
            }
            final String text = snapshot;
            final List<String> result = matches;
            if (closed || request != generation) return;
            main.post(() -> { if (!closed && request == generation) callback.ready(result,text); });
        });
    }
    void close() { closed = true; invalidate(); thread.quitSafely(); }
}
