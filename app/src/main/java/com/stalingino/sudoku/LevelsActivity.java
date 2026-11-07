package com.stalingino.sudoku;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Difficulty tabs and a grid of levels; each level unlocks when the previous one is solved. */
public class LevelsActivity extends BaseActivity {
    public static final String EXTRA_DIFFICULTY = "difficulty";
    private static final String PREFS = "levels_ui";

    private PuzzleStore store;
    private Difficulty difficulty = Difficulty.EASY;
    private final TextView[] tabs = new TextView[Difficulty.values().length];
    private LevelAdapter adapter;

    public static void start(Context ctx, Difficulty d) {
        // Reuse a levels screen already on the back stack instead of stacking another one.
        ctx.startActivity(new Intent(ctx, LevelsActivity.class).putExtra(EXTRA_DIFFICULTY, d.code)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Difficulty d = Difficulty.fromCode(intent.getStringExtra(EXTRA_DIFFICULTY));
        if (d != null) select(d);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_levels);
        setupChrome(getString(R.string.levels), true);
        store = PuzzleStore.get(this);

        String code = getIntent().getStringExtra(EXTRA_DIFFICULTY);
        if (code == null) code = getSharedPreferences(PREFS, MODE_PRIVATE).getString("tab", null);
        if (savedInstanceState != null) code = savedInstanceState.getString(EXTRA_DIFFICULTY, code);
        Difficulty d = Difficulty.fromCode(code);
        if (d != null) difficulty = d;

        LinearLayout tabRow = findViewById(R.id.tabs);
        float density = getResources().getDisplayMetrics().density;
        for (Difficulty diff : Difficulty.values()) {
            TextView t = new TextView(this);
            t.setText(diff.label);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setBackgroundResource(R.drawable.bg_chip);
            t.setOnClickListener(v -> select(diff));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            int m = Math.round(3 * density);
            lp.setMargins(m, 0, m, 0);
            tabRow.addView(t, lp);
            tabs[diff.ordinal()] = t;
        }

        GridView grid = findViewById(R.id.grid);
        adapter = new LevelAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> openLevel(position + 1));
        select(difficulty);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_DIFFICULTY, difficulty.code);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void select(Difficulty d) {
        difficulty = d;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("tab", d.code).apply();
        for (Difficulty x : Difficulty.values()) {
            TextView t = tabs[x.ordinal()];
            boolean sel = x == d;
            t.setSelected(sel);
            t.setTextColor(getColor(sel ? R.color.on_accent : R.color.text_primary));
            t.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        refresh();
        // Show the next level with a couple of rows of solved levels above it.
        ((GridView) findViewById(R.id.grid)).setSelection(Math.max(0, store.nextLevel(d) - 1 - 10));
    }

    private void refresh() {
        int total = LevelPack.get(this, difficulty).size();
        int done = store.completedCount(difficulty);
        int stars = store.starCount(difficulty);
        long best = store.bestTime(difficulty);
        String summary = getString(R.string.pack_summary, done, total, stars, total * 3);
        if (best > 0) summary += " · " + getString(R.string.best_time, formatTime(best));
        ((TextView) findViewById(R.id.pack_summary)).setText(summary);
        ProgressBar bar = findViewById(R.id.pack_progress);
        bar.setMax(total);
        bar.setProgress(done);
        adapter.notifyDataSetChanged();
    }

    private void openLevel(int level) {
        if (!store.isUnlocked(difficulty, level)) {
            Toast.makeText(this, getString(R.string.level_locked, level - 1), Toast.LENGTH_SHORT).show();
            return;
        }
        GameActivity.start(this, store.level(difficulty, level));
    }

    private final class LevelAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return LevelPack.get(LevelsActivity.this, difficulty).size();
        }

        @Override
        public Object getItem(int position) {
            return position + 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = getLayoutInflater().inflate(R.layout.item_level, parent, false);
            int level = position + 1;
            boolean unlocked = store.isUnlocked(difficulty, level);
            boolean done = store.isCompleted(difficulty, level);
            PuzzleRecord r = store.find(PuzzleRecord.levelId(difficulty, level));
            boolean started = r != null && r.inProgress();
            int accent = getColor(difficultyColor(difficulty));
            float density = getResources().getDisplayMetrics().density;

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * density);
            TextView number = view.findViewById(R.id.level_number);
            TextView stars = view.findViewById(R.id.level_stars);
            number.setText(String.valueOf(level));
            if (done) {
                bg.setColor(accent);
                number.setTextColor(getColor(R.color.on_accent));
                stars.setTextColor(getColor(R.color.on_accent));
                stars.setText(starString(store.stars(difficulty, level)));
            } else if (unlocked) {
                bg.setColor(getColor(R.color.card_bg));
                bg.setStroke(Math.round(2 * density), accent);
                number.setTextColor(getColor(R.color.text_primary));
                stars.setTextColor(accent);
                stars.setText(started ? getString(R.string.percent, r.percent()) : "");
            } else {
                bg.setColor(getColor(R.color.locked_bg));
                number.setTextColor(getColor(R.color.text_disabled));
                stars.setTextColor(getColor(R.color.text_disabled));
                stars.setText("🔒");
            }
            view.setBackground(bg);
            view.setContentDescription(getString(done ? R.string.level_desc_done
                    : unlocked ? R.string.level_desc_open : R.string.level_desc_locked, level));
            return view;
        }
    }
}
