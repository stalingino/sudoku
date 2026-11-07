package com.stalingino.sudoku;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;

/** Plays one puzzle: a level, the daily challenge or an imported SDK81 puzzle. */
public class GameActivity extends BaseActivity {
    public static final String EXTRA_ID = "puzzle_id";

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

    public static void start(Context ctx, PuzzleRecord r) {
        ctx.startActivity(new Intent(ctx, GameActivity.class).putExtra(EXTRA_ID, r.id));
    }

    private PuzzleStore store;
    private Settings settings;
    private PuzzleRecord record;

    private int[] givens;
    private int[] values;
    private int[] notes;
    private int[] solution;
    /** Mistakes are only judged against a unique solution. */
    private boolean uniqueSolution;
    private boolean[] wrong = new boolean[81];
    private final Deque<Move> undo = new ArrayDeque<>();
    private int selected = -1;
    private boolean notesMode;

    private SudokuBoardView board;
    private TextView titleView;
    private TextView timerView;
    private TextView mistakesView;
    private Button notesButton;
    private final Button[] digitButtons = new Button[10];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean timerRunning;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning || record.solved) return;
            record.elapsedSeconds++;
            updateStatus();
            if (record.elapsedSeconds % 10 == 0) store.save();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = PuzzleStore.get(this);
        record = store.find(getIntent().getStringExtra(EXTRA_ID));
        if (record == null) {
            finish();
            return;
        }
        settings = new Settings(this);
        setContentView(R.layout.activity_game);
        setupChrome(record.displayName(), true);

        board = findViewById(R.id.board);
        titleView = findViewById(R.id.title);
        timerView = findViewById(R.id.timer);
        mistakesView = findViewById(R.id.mistakes);
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

        loadPuzzle();
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt(STATE_SELECTED, -1);
            notesMode = savedInstanceState.getBoolean(STATE_NOTES_MODE, false);
            refresh();
        }
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
        if (record == null) return;
        if (settings.keepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        board.setHighlightSameDigits(settings.highlightSame());
        markWrongEntries();
        refresh();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (record == null) return;
        stopTimer();
        persist();
    }

    // ---------------------------------------------------------------- puzzle lifecycle

    private void loadPuzzle() {
        store.setCurrent(record);
        try {
            givens = Sdk81.parse(record.puzzle);
            values = Sdk81.parse(record.progress);
        } catch (Sdk81.FormatException e) {
            record.reset();
            values = givens.clone();
        }
        // Givens are immutable: always restore them from the original string.
        for (int i = 0; i < 81; i++) if (givens[i] != 0) values[i] = givens[i];
        notes = PuzzleRecord.decodeNotes(record.notes);
        solution = Solver.solve(givens);
        uniqueSolution = solution != null && Solver.countSolutions(givens, 2) == 1;
        wrong = new boolean[81];
        undo.clear();
        selected = -1;
        notesMode = false;
        markWrongEntries();
        refresh();
    }

    private void persist() {
        record.progress = Sdk81.serialize(values);
        record.notes = PuzzleRecord.encodeNotes(notes);
        store.save();
    }

    private void restart() {
        stopTimer();
        record.reset();
        persist();
        loadPuzzle();
        startTimer();
    }

    // ---------------------------------------------------------------- input

    private void buildKeypad(LinearLayout keypad) {
        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(2 * density);
        for (int d = 1; d <= 9; d++) {
            Button b = new Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless);
            b.setBackgroundResource(R.drawable.bg_key);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(0, 0, 0, 0);
            b.setAllCaps(false);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setTextSize(22);
            b.setTextColor(getColor(R.color.key_text));
            b.setStateListAnimator(null);
            final int digit = d;
            b.setOnClickListener(v -> enterDigit(digit));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(gap, 0, gap, 0);
            keypad.addView(b, lp);
            digitButtons[d] = b;
        }
    }

    private boolean isEditable(int index) {
        return index >= 0 && givens[index] == 0 && !record.solved;
    }

    private void enterDigit(int d) {
        if (record.solved) return;
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
            int v = values[selected];
            if (v != 0) {
                if (uniqueSolution && v != solution[selected]) {
                    record.mistakes++;
                    if (settings.showMistakes()) wrong[selected] = true;
                }
                if (settings.autoRemoveNotes()) clearPeerNotes(selected, v);
            }
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
            // Prefer the empty cell with the fewest candidates: the most natural next step.
            int bestCount = 10;
            for (int i = 0; i < 81; i++) {
                if (givens[i] != 0 || values[i] == solution[i]) continue;
                int n = values[i] != 0 ? 0 : candidateCount(i);
                if (n < bestCount) {
                    bestCount = n;
                    target = i;
                }
            }
        }
        if (target < 0) return;
        record.hints++;
        pushUndo(target);
        values[target] = solution[target];
        notes[target] = 0;
        wrong[target] = false;
        if (settings.autoRemoveNotes()) clearPeerNotes(target, solution[target]);
        selected = target;
        afterMove();
    }

    private int candidateCount(int index) {
        int used = 0, r = index / 9, c = index % 9;
        for (int k = 0; k < 9; k++) {
            used |= 1 << values[r * 9 + k];
            used |= 1 << values[k * 9 + c];
            used |= 1 << values[((r / 3) * 3 + k / 3) * 9 + (c / 3) * 3 + k % 3];
        }
        return Integer.bitCount(~used & 0x3FE);
    }

    private void markWrongEntries() {
        boolean show = uniqueSolution && settings.showMistakes();
        for (int i = 0; i < 81; i++) {
            wrong[i] = show && givens[i] == 0 && values[i] != 0 && values[i] != solution[i];
        }
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
            stopTimer();
            boolean newBest = record.complete();
            persist();
            refresh();
            showSolvedDialog(newBest);
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

        updateStatus();

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

    private void updateStatus() {
        String info = record.isLevel() || record.isDaily()
                ? record.difficultyLabel()
                : getString(R.string.title_format, record.id, record.difficultyLabel(), record.givens());
        if (record.hints > 0) info += " · " + getResources().getQuantityString(R.plurals.hints_used, record.hints, record.hints);
        titleView.setText(info);
        mistakesView.setText(uniqueSolution ? getString(R.string.mistakes_count, record.mistakes) : "");
        String time = formatTime(record.elapsedSeconds);
        timerView.setText(record.solved ? getString(R.string.solved_timer, time) : time);
    }

    private void startTimer() {
        if (timerRunning || record.solved) return;
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
        getMenuInflater().inflate(R.menu.game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_check) {
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

    // ---------------------------------------------------------------- dialogs

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

    private void showSolvedDialog(boolean newBest) {
        int stars = record.attemptStars();
        StringBuilder msg = new StringBuilder();
        msg.append(starString(stars)).append("\n\n");
        msg.append(getString(R.string.solved_time, formatTime(record.elapsedSeconds)));
        if (newBest && record.timesSolved > 1) msg.append("  ").append(getString(R.string.new_best));
        else if (record.timesSolved > 1) msg.append("\n").append(getString(R.string.best_time, formatTime(record.bestSeconds)));
        msg.append("\n").append(getString(R.string.solved_stats, record.mistakes, record.hints));
        if (stars < 3) msg.append("\n\n").append(getString(R.string.stars_tip));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(record.isLevel() ? R.string.level_complete : R.string.solved_title)
                .setMessage(msg.toString())
                .setCancelable(false);
        if (record.isLevel()) {
            int next = record.level + 1;
            boolean hasNext = next <= LevelPack.get(this, record.difficulty).size();
            if (hasNext) {
                b.setPositiveButton(R.string.next_level, (d, w) -> {
                    start(this, store.level(record.difficulty, next));
                    finish();
                });
            }
            b.setNeutralButton(R.string.levels, (d, w) -> {
                LevelsActivity.start(this, record.difficulty);
                finish();
            });
            b.setNegativeButton(R.string.close, null);
        } else {
            b.setPositiveButton(R.string.home, (d, w) -> finish());
            b.setNegativeButton(R.string.close, null);
        }
        b.show();
    }
}
