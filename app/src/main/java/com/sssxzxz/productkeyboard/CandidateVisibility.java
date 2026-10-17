package com.sssxzxz.productkeyboard;

/** Session-only state: moving the cursor is not an edit. No input is persisted. */
final class CandidateVisibility {
    private boolean dismissed;
    private String textAtDismissal;
    boolean dismissed() { return dismissed; }
    void dismiss(String text) { dismissed = true; textAtDismissal = text; }
    void edited() { dismissed = false; textAtDismissal = null; }
    void observe(String text) {
        if (!dismissed || text == null) return;
        if (textAtDismissal == null) textAtDismissal = text;
        else if (!textAtDismissal.equals(text)) edited();
    }
    void beginEditor(String text) { if (dismissed) textAtDismissal = text; }
}
