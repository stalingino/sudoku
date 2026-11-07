package com.stalingino.sudoku;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String STATE_SELECTED = "selected";
    private static final String STATE_NOTES_MODE = "notes_mode";

    /** One reversible edit of a single cell. */
    private static final class Move {
        final int index, oldValue, oldNotes;
        Move(int index, int oldValue, int oldNotes) {
            this.index = index;
            this.oldValue = oldValue;
            this.oldNotes = oldNotes;
        }
    }

    private PuzzleStore store;
    private PuzzleRecord record;

    private int[] givens;
    private int[] values;
    private int[] notes;
    private int[] solution;
    private boolean[] wrong = new boolean[81];
    private final Deque<Move> undo = new ArrayDeque<>();
    private int selected = -1;
    private boolean notesMode;

    private SudokuBoardView board;
    private TextView titleView;
    private TextView timerView;
    private Button notesButton;
    private final Button[] digitButtons = new Button[10];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean timerRunning;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning || record == null || record.solved) return;
            record.elapsedSeconds++;
            updateTimer();
            if (record.elapsedSeconds % 10 == 0) store.save();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        board = findViewById(R.id.board);
        titleView = findViewById(R.id.title);
        timerView = findViewById(R.id.timer);
        notesButton = findViewById(R.id.btn_notes);

        board.setOnCellSelectedListener(index -> {
            selected = index;
            refresh();
        });
        findViewById(R.id.btn_undo).setOnClickListener(v -> undoMove());
        findViewById(R.id.btn_erase).setOnClickListener(v -> erase());
        notesButton.setOnClickListener(v -> {
            notesMode = !notesMode;
            refresh();
        });
        findViewById(R.id.btn_hint).setOnClickListener(v -> hint());
        buildKeypad(findViewById(R.id.keypad));

        store = new PuzzleStore(this);
        loadPuzzle(store.current());
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt(STATE_SELECTED, -1);
            notesMode = savedInstanceState.getBoolean(STATE_NOTES_MODE, false);
            refresh();
        }
        handleIncomingIntent(getIntent());
    }

    /**
     * Draws behind the system bars on every API level (Android 15+ enforces this for apps
     * targeting API 35) and pads the toolbar and content by the bar insets so nothing hides.
     */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);
        View root = findViewById(R.id.root);
        View bar = findViewById(R.id.toolbar_container);
        View content = findViewById(R.id.content);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!night && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            root.setSystemUiVisibility(flags);
        }

        int cLeft = content.getPaddingLeft(), cRight = content.getPaddingRight(), cBottom = content.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = i.left; top = i.top; right = i.right; bottom = i.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            bar.setPadding(left, top, right, 0);
            content.setPadding(cLeft + left, content.getPaddingTop(), cRight + right, cBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SELECTED, selected);
        out.putBoolean(STATE_NOTES_MODE, notesMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        persist();
    }

    // ---------------------------------------------------------------- puzzle lifecycle

    private void loadPuzzle(PuzzleRecord r) {
        if (record != null) persist();
        record = r;
        store.setCurrent(r);
        try {
            givens = Sdk81.parse(r.puzzle);
            values = Sdk81.parse(r.progress);
        } catch (Sdk81.FormatException e) {
            r.reset();
            values = givens.clone();
        }
        // Givens are immutable: always restore them from the original string.
        for (int i = 0; i < 81; i++) if (givens[i] != 0) values[i] = givens[i];
        notes = PuzzleRecord.decodeNotes(r.notes);
        solution = Solver.solve(givens);
        wrong = new boolean[81];
        undo.clear();
        selected = -1;
        notesMode = false;
        refresh();
        stopTimer();
        startTimer();
    }

    private void persist() {
        if (record == null) return;
        record.progress = Sdk81.serialize(values);
        record.notes = PuzzleRecord.encodeNotes(notes);
        store.save();
    }

    private void restart() {
        PuzzleRecord r = record;
        r.reset();
        record = null; // skip saving the old board over the reset progress
        loadPuzzle(r);
        persist();
    }

    // ---------------------------------------------------------------- input

    private void buildKeypad(LinearLayout keypad) {
        for (int d = 1; d <= 9; d++) {
            Button b = new Button(this);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(0, 0, 0, 0);
            b.setAllCaps(false);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setTextSize(22);
            final int digit = d;
            b.setOnClickListener(v -> enterDigit(digit));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            keypad.addView(b, lp);
            digitButtons[d] = b;
        }
    }

    private boolean isEditable(int index) {
        return index >= 0 && givens[index] == 0 && !record.solved;
    }

    private void enterDigit(int d) {
        if (selected < 0) {
            Toast.makeText(this, R.string.select_cell_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isEditable(selected)) return;
        if (notesMode) {
            if (values[selected] != 0) return;
            pushUndo(selected);
            notes[selected] ^= (1 << d);
        } else {
            pushUndo(selected);
            values[selected] = values[selected] == d ? 0 : d;
            notes[selected] = 0;
            wrong[selected] = false;
            if (values[selected] != 0) clearPeerNotes(selected, d);
        }
        afterMove();
    }

    private void erase() {
        if (!isEditable(selected)) return;
        if (values[selected] == 0 && notes[selected] == 0) return;
        pushUndo(selected);
        values[selected] = 0;
        notes[selected] = 0;
        wrong[selected] = false;
        afterMove();
    }

    private void hint() {
        if (record.solved) return;
        if (solution == null) {
            Toast.makeText(this, R.string.no_solution, Toast.LENGTH_SHORT).show();
            return;
        }
        int target = -1;
        if (isEditable(selected) && values[selected] != solution[selected]) {
            target = selected;
        } else {
            for (int i = 0; i < 81; i++) {
                if (givens[i] == 0 && values[i] != solution[i]) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) return;
        pushUndo(target);
        values[target] = solution[target];
        notes[target] = 0;
        wrong[target] = false;
        clearPeerNotes(target, solution[target]);
        selected = target;
        afterMove();
    }

    private void checkProgress() {
        if (solution == null) {
            Toast.makeText(this, R.string.no_solution, Toast.LENGTH_SHORT).show();
            return;
        }
        int errors = 0;
        for (int i = 0; i < 81; i++) {
            wrong[i] = givens[i] == 0 && values[i] != 0 && values[i] != solution[i];
            if (wrong[i]) errors++;
        }
        record.mistakesChecked++;
        refresh();
        Toast.makeText(this, errors == 0
                ? getString(R.string.check_ok)
                : getResources().getQuantityString(R.plurals.check_errors, errors, errors),
                Toast.LENGTH_SHORT).show();
    }

    private void clearPeerNotes(int index, int d) {
        int r = index / 9, c = index % 9;
        int bit = ~(1 << d);
        for (int i = 0; i < 81; i++) {
            int r2 = i / 9, c2 = i % 9;
            if (r == r2 || c == c2 || (r / 3 == r2 / 3 && c / 3 == c2 / 3)) notes[i] &= bit;
        }
    }

    private void pushUndo(int index) {
        undo.push(new Move(index, values[index], notes[index]));
    }

    private void undoMove() {
        if (undo.isEmpty() || record.solved) return;
        Move m = undo.pop();
        values[m.index] = m.oldValue;
        notes[m.index] = m.oldNotes;
        wrong[m.index] = false;
        selected = m.index;
        afterMove();
    }

    private void afterMove() {
        boolean complete = true;
        for (int v : values) if (v == 0) { complete = false; break; }
        if (complete && !Sdk81.hasConflicts(values)) {
            record.solved = true;
            stopTimer();
            persist();
            refresh();
            showSolvedDialog();
            return;
        }
        persist();
        refresh();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            enterDigit(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_1 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            enterDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
            return true;
        }
        int sel = selected < 0 ? 40 : selected;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
            case KeyEvent.KEYCODE_0:
                erase();
                return true;
            case KeyEvent.KEYCODE_N:
                notesMode = !notesMode;
                refresh();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                selected = selected < 0 ? sel : (sel + 72) % 81;
                refresh();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selected = selected < 0 ? sel : (sel + 9) % 81;
                refresh();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                selected = selected < 0 ? sel : (sel / 9) * 9 + (sel % 9 + 8) % 9;
                refresh();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                selected = selected < 0 ? sel : (sel / 9) * 9 + (sel % 9 + 1) % 9;
                refresh();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ---------------------------------------------------------------- rendering

    private void refresh() {
        boolean[] conflicts = Sdk81.conflicts(values);
        board.setState(givens, values, notes, conflicts, wrong, selected, record.solved);

        titleView.setText(getString(R.string.title_format, record.id, record.difficulty(), record.givens()));
        updateTimer();

        notesButton.setText(notesMode ? R.string.notes_on : R.string.notes_off);
        notesButton.setSelected(notesMode);

        int[] counts = new int[10];
        for (int v : values) counts[v]++;
        for (int d = 1; d <= 9; d++) {
            int left = Math.max(0, 9 - counts[d]);
            String label = d + "\n" + left;
            SpannableString s = new SpannableString(label);
            s.setSpan(new RelativeSizeSpan(0.5f), label.indexOf('\n') + 1, label.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            digitButtons[d].setText(s);
            digitButtons[d].setAlpha(left == 0 ? 0.35f : 1f);
            digitButtons[d].setContentDescription(getString(R.string.digit_description, d, left));
        }
    }

    private void updateTimer() {
        long s = record.elapsedSeconds;
        String time = s >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
        timerView.setText(record.solved ? getString(R.string.solved_timer, time) : time);
    }

    private void startTimer() {
        if (timerRunning || record == null || record.solved) return;
        timerRunning = true;
        handler.postDelayed(tick, 1000);
    }

    private void stopTimer() {
        timerRunning = false;
        handler.removeCallbacks(tick);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import) {
            showImportDialog(null);
        } else if (id == R.id.action_puzzles) {
            showPuzzleList();
        } else if (id == R.id.action_check) {
            checkProgress();
        } else if (id == R.id.action_copy_puzzle) {
            copyToClipboard(getString(R.string.clip_puzzle_label), record.puzzle);
        } else if (id == R.id.action_copy_progress) {
            copyToClipboard(getString(R.string.clip_progress_label), Sdk81.serialize(values));
        } else if (id == R.id.action_restart) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restart_title)
                    .setMessage(R.string.restart_message)
                    .setPositiveButton(R.string.restart, (d, w) -> restart())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (id == R.id.action_format) {
            showFormatInfo();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, getString(R.string.copied, label), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- import

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String text = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            text = intent.getData().getQueryParameter("p");
        }
        if (text != null) {
            intent.setAction(Intent.ACTION_MAIN); // don't re-import on rotation
            showImportDialog(text);
        }
    }

    private String clipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        return cs == null ? null : cs.toString();
    }

    private void showImportDialog(String prefill) {
        View content = getLayoutInflater().inflate(R.layout.dialog_import, null);
        EditText input = content.findViewById(R.id.import_input);
        TextView status = content.findViewById(R.id.import_status);
        if (prefill != null) input.setText(prefill);

        content.findViewById(R.id.import_paste).setOnClickListener(v -> {
            String clip = clipboardText();
            if (clip == null) {
                status.setText(R.string.clipboard_empty);
            } else {
                input.setText(clip);
                input.setSelection(input.length());
            }
        });
        input.addTextChangedListener(new SimpleTextWatcher(s -> {
            String n = Sdk81.normalize(s);
            status.setText(getString(R.string.import_count, n.length()));
        }));
        status.setText(getString(R.string.import_count, Sdk81.normalize(input.getText().toString()).length()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.import_title)
                .setView(content)
                .setPositiveButton(R.string.import_action, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String error = importPuzzles(input.getText().toString());
            if (error == null) dialog.dismiss();
            else status.setText(error);
        }));
        dialog.show();
    }

    /** Imports one or more puzzles. Returns an error message, or null on success. */
    private String importPuzzles(String text) {
        List<String> parsed;
        try {
            parsed = Sdk81.parseMany(text);
        } catch (Sdk81.FormatException e) {
            return e.getMessage();
        }
        List<String> warnings = new ArrayList<>();
        List<PuzzleRecord> added = new ArrayList<>();
        int n = 0;
        for (String p : parsed) {
            n++;
            String prefix = parsed.size() > 1 ? getString(R.string.puzzle_n, n) : "";
            int[] grid;
            try {
                grid = Sdk81.parse(p);
            } catch (Sdk81.FormatException e) {
                return prefix + e.getMessage();
            }
            if (Sdk81.hasConflicts(grid)) return prefix + getString(R.string.error_conflict);
            int solutions = Solver.countSolutions(grid, 2);
            if (solutions == 0) return prefix + getString(R.string.error_unsolvable);
            if (solutions > 1) warnings.add(prefix + getString(R.string.warn_multiple));
        }
        for (String p : parsed) added.add(store.add(p));
        loadPuzzle(added.get(added.size() - 1));

        String msg = added.size() == 1
                ? getString(R.string.imported_one, added.get(0).id)
                : getString(R.string.imported_many, added.size());
        if (!warnings.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(msg)
                    .setMessage(String.join("\n", warnings))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    // ---------------------------------------------------------------- dialogs

    private void showPuzzleList() {
        persist();
        List<PuzzleRecord> all = store.all();
        CharSequence[] items = new CharSequence[all.size()];
        for (int i = 0; i < all.size(); i++) {
            PuzzleRecord r = all.get(i);
            int pct = Math.round(100f * (r.filled() - r.givens()) / Math.max(1, 81 - r.givens()));
            String status = r.solved ? getString(R.string.status_solved) : pct + "%";
            String marker = r == record ? "▶ " : "";
            items[i] = marker + r.id + " · " + r.difficulty() + " · " + status;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.puzzles_title)
                .setItems(items, (d, which) -> loadPuzzle(all.get(which)))
                .setNeutralButton(R.string.delete_current, (d, w) -> confirmDelete())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        PuzzleRecord r = record;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_title, r.id))
                .setMessage(R.string.delete_message)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    record = null;
                    store.delete(r);
                    loadPuzzle(store.current());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFormatInfo() {
        TextView tv = new TextView(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad / 2, pad, 0);
        tv.setTextIsSelectable(true);
        tv.setText(getString(R.string.format_info, record.id, Sdk81.pretty(record.puzzle)));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.START);
        new AlertDialog.Builder(this)
                .setTitle(R.string.format_title)
                .setView(tv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSolvedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.solved_title)
                .setMessage(getString(R.string.solved_message, record.id, timerView.getText()))
                .setPositiveButton(R.string.puzzles_title, (d, w) -> showPuzzleList())
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }
}
