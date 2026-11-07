package com.stalingino.sudoku;

import android.text.Editable;
import android.text.TextWatcher;

/** TextWatcher that only cares about the text after it changed. */
final class SimpleTextWatcher implements TextWatcher {
    interface Callback {
        void onChanged(String text);
    }

    private final Callback callback;

    SimpleTextWatcher(Callback callback) {
        this.callback = callback;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override public void afterTextChanged(Editable s) { callback.onChanged(s.toString()); }
}
