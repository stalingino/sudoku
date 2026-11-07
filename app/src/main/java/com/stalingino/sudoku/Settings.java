package com.stalingino.sudoku;

import android.content.Context;
import android.content.SharedPreferences;

/** Player preferences. */
public final class Settings {
    private static final String PREFS = "sudoku_settings";
    public static final String SHOW_MISTAKES = "show_mistakes";
    public static final String HIGHLIGHT_SAME = "highlight_same";
    public static final String AUTO_REMOVE_NOTES = "auto_remove_notes";
    public static final String KEEP_SCREEN_ON = "keep_screen_on";

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean showMistakes() { return prefs.getBoolean(SHOW_MISTAKES, true); }
    public boolean highlightSame() { return prefs.getBoolean(HIGHLIGHT_SAME, true); }
    public boolean autoRemoveNotes() { return prefs.getBoolean(AUTO_REMOVE_NOTES, true); }
    public boolean keepScreenOn() { return prefs.getBoolean(KEEP_SCREEN_ON, false); }

    public boolean get(String key) {
        return prefs.getBoolean(key, !KEEP_SCREEN_ON.equals(key));
    }

    public void set(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
