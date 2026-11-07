package com.stalingino.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The "Import SDK81 puzzle" dialog plus the validation behind it. */
public final class ImportDialog {

    public interface Callback {
        /** Called with the imported (or already existing) records, in input order. */
        void onImported(List<PuzzleRecord> records, List<String> warnings);
    }

    private ImportDialog() {}

    public static AlertDialog show(Activity activity, String prefill, Callback callback) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_import, null);
        EditText input = content.findViewById(R.id.import_input);
        TextView status = content.findViewById(R.id.import_status);
        if (prefill != null) input.setText(prefill);

        content.findViewById(R.id.import_paste).setOnClickListener(v -> {
            String clip = clipboardText(activity);
            if (clip == null) {
                status.setText(R.string.clipboard_empty);
            } else {
                input.setText(clip);
                input.setSelection(input.length());
            }
        });
        input.addTextChangedListener(new SimpleTextWatcher(s ->
                status.setText(activity.getString(R.string.import_count, Sdk81.normalize(s).length()))));
        status.setText(activity.getString(R.string.import_count,
                Sdk81.normalize(input.getText().toString()).length()));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.import_title)
                .setView(content)
                .setPositiveButton(R.string.import_action, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<String> warnings = new ArrayList<>();
            List<String> puzzles = new ArrayList<>();
            String error = validate(activity, input.getText().toString(), puzzles, warnings);
            if (error != null) {
                status.setText(error);
                return;
            }
            PuzzleStore store = PuzzleStore.get(activity);
            List<PuzzleRecord> added = new ArrayList<>();
            for (String p : puzzles) added.add(store.addImport(p));
            dialog.dismiss();
            callback.onImported(added, warnings);
        }));
        dialog.show();
        return dialog;
    }

    /**
     * Checks text holding one or more SDK81 puzzles. Fills {@code out} and {@code warnings} and
     * returns null when everything can be imported, otherwise an error message.
     */
    static String validate(Context ctx, String text, List<String> out, List<String> warnings) {
        List<String> parsed;
        try {
            parsed = Sdk81.parseMany(text);
        } catch (Sdk81.FormatException e) {
            return e.getMessage();
        }
        int n = 0;
        for (String p : parsed) {
            n++;
            String prefix = parsed.size() > 1 ? ctx.getString(R.string.puzzle_n, n) : "";
            int[] grid;
            try {
                grid = Sdk81.parse(p);
            } catch (Sdk81.FormatException e) {
                return prefix + e.getMessage();
            }
            if (Sdk81.hasConflicts(grid)) return prefix + ctx.getString(R.string.error_conflict);
            int solutions = Solver.countSolutions(grid, 2);
            if (solutions == 0) return prefix + ctx.getString(R.string.error_unsolvable);
            if (solutions > 1) warnings.add(prefix + ctx.getString(R.string.warn_multiple));
        }
        out.addAll(parsed);
        return null;
    }

    private static String clipboardText(Context ctx) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
        return cs == null ? null : cs.toString();
    }
}
