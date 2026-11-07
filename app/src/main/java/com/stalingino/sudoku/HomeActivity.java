package com.stalingino.sudoku;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Start screen: continue / play, levels, daily challenge, imported puzzles, stats, settings. */
public class HomeActivity extends BaseActivity {

    private PuzzleStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setupChrome(getString(R.string.app_name), false);
        hideToolbar();
        store = PuzzleStore.get(this);

        card(R.id.card_levels, "🏆", R.string.levels).setOnClickListener(v ->
                startActivity(new Intent(this, LevelsActivity.class)));
        card(R.id.card_daily, "📅", R.string.daily_challenge).setOnClickListener(v ->
                GameActivity.start(this, store.daily(PuzzleStore.today())));
        card(R.id.card_my_puzzles, "🧩", R.string.my_puzzles).setOnClickListener(v -> showMyPuzzles());
        card(R.id.card_import, "📥", R.string.import_title).setOnClickListener(v -> showImport(null));
        findViewById(R.id.play).setOnClickListener(v -> play());
        findViewById(R.id.btn_stats).setOnClickListener(v -> showStats());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettings());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelp());

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** The home header already shows the name, so only the status-bar inset stays. */
    @SuppressWarnings("deprecation")
    private void hideToolbar() {
        findViewById(R.id.toolbar).setVisibility(View.GONE);
        View bar = findViewById(R.id.toolbar_container);
        bar.setBackground(null);
        bar.setElevation(0);
        if (isNight()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            View root = findViewById(R.id.root);
            root.setSystemUiVisibility(root.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private View card(int id, String icon, int title) {
        View card = findViewById(id);
        ((TextView) card.findViewById(R.id.card_icon)).setText(icon);
        ((TextView) card.findViewById(R.id.card_title)).setText(title);
        return card;
    }

    private void setSubtitle(int cardId, CharSequence text) {
        ((TextView) findViewById(cardId).findViewById(R.id.card_subtitle)).setText(text);
    }

    /** The puzzle the big button continues, or null to start the next level. */
    private PuzzleRecord continuable() {
        PuzzleRecord cur = store.current();
        return cur != null && cur.inProgress() ? cur : null;
    }

    private Difficulty nextDifficulty() {
        PuzzleRecord cur = store.current();
        Difficulty d = cur != null && cur.difficulty != null ? cur.difficulty : Difficulty.EASY;
        // Move on to the next pack once this one is complete.
        while (store.completedCount(d) >= LevelPack.get(this, d).size() && d.ordinal() < Difficulty.values().length - 1) {
            d = Difficulty.values()[d.ordinal() + 1];
        }
        return d;
    }

    private void refresh() {
        int totalLevels = 0, solvedLevels = 0, stars = 0;
        for (Difficulty d : Difficulty.values()) {
            totalLevels += LevelPack.get(this, d).size();
            solvedLevels += store.completedCount(d);
            stars += store.starCount(d);
        }
        ((TextView) findViewById(R.id.summary)).setText(
                getString(R.string.home_summary, solvedLevels, totalLevels, stars));

        TextView playTitle = findViewById(R.id.play_title);
        TextView playSub = findViewById(R.id.play_subtitle);
        PuzzleRecord cont = continuable();
        if (cont != null) {
            playTitle.setText(R.string.continue_game);
            playSub.setText(getString(R.string.continue_subtitle, cont.displayName(), cont.percent(),
                    formatTime(cont.elapsedSeconds)));
        } else {
            Difficulty d = nextDifficulty();
            playTitle.setText(R.string.play);
            playSub.setText(getString(R.string.play_subtitle, d.label, store.nextLevel(d)));
        }

        setSubtitle(R.id.card_levels, getString(R.string.levels_subtitle, solvedLevels, totalLevels, stars));

        PuzzleRecord daily = store.find(PuzzleRecord.dailyId(PuzzleStore.today()));
        int streak = store.dailyStreak();
        String dailyStatus;
        if (daily != null && daily.timesSolved > 0) {
            dailyStatus = getString(R.string.daily_done, formatTime(daily.bestSeconds));
        } else if (daily != null && daily.inProgress()) {
            dailyStatus = getString(R.string.daily_in_progress, daily.percent());
        } else {
            dailyStatus = getString(R.string.daily_new);
        }
        if (streak > 0) dailyStatus += " · " + getResources().getQuantityString(R.plurals.streak, streak, streak);
        setSubtitle(R.id.card_daily, dailyStatus);

        int imports = store.imports().size();
        setSubtitle(R.id.card_my_puzzles, getResources().getQuantityString(R.plurals.puzzle_count, imports, imports));
        setSubtitle(R.id.card_import, getString(R.string.import_subtitle));
    }

    private void play() {
        PuzzleRecord cont = continuable();
        if (cont != null) {
            GameActivity.start(this, cont);
        } else {
            Difficulty d = nextDifficulty();
            GameActivity.start(this, store.level(d, store.nextLevel(d)));
        }
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
            intent.setAction(Intent.ACTION_MAIN); // don't import again after rotation
            showImport(text);
        }
    }

    private void showImport(String prefill) {
        ImportDialog.show(this, prefill, (records, warnings) -> {
            PuzzleRecord last = records.get(records.size() - 1);
            String msg = records.size() == 1
                    ? getString(R.string.imported_one, last.id)
                    : getString(R.string.imported_many, records.size());
            if (!warnings.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle(msg)
                        .setMessage(String.join("\n", warnings))
                        .setPositiveButton(R.string.play, (d, w) -> GameActivity.start(this, last))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                GameActivity.start(this, last);
            }
            refresh();
        });
    }

    private void showMyPuzzles() {
        List<PuzzleRecord> list = store.imports();
        if (list.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.my_puzzles)
                    .setMessage(R.string.no_imports)
                    .setPositiveButton(R.string.import_action, (d, w) -> showImport(null))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            PuzzleRecord r = list.get(i);
            String status = r.timesSolved > 0
                    ? getString(R.string.status_solved) + " " + starString(r.stars)
                    : r.percent() + "%";
            items[i] = r.id + " · " + r.difficultyLabel() + " · " + status;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.my_puzzles)
                .setItems(items, (d, which) -> GameActivity.start(this, list.get(which)))
                .setNeutralButton(R.string.delete_ellipsis, (d, w) -> pickToDelete(list))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickToDelete(List<PuzzleRecord> list) {
        CharSequence[] items = new CharSequence[list.size()];
        boolean[] checked = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) items[i] = list.get(i).id;
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_puzzles)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    for (int i = 0; i < list.size(); i++) if (checked[i]) store.delete(list.get(i));
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- info dialogs

    private void showStats() {
        StringBuilder sb = new StringBuilder();
        for (Difficulty d : Difficulty.values()) {
            int total = LevelPack.get(this, d).size();
            long best = store.bestTime(d);
            sb.append(getString(R.string.stats_pack, d.label, store.completedCount(d), total,
                    store.starCount(d), total * 3, best == 0 ? "–" : formatTime(best))).append("\n\n");
        }
        sb.append(getString(R.string.stats_daily, store.dailiesSolved(), store.dailyStreak())).append("\n\n");
        sb.append(getString(R.string.stats_total, store.totalSolved()));
        new AlertDialog.Builder(this)
                .setTitle(R.string.statistics)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSettings() {
        Settings s = new Settings(this);
        String[] keys = {Settings.SHOW_MISTAKES, Settings.HIGHLIGHT_SAME, Settings.AUTO_REMOVE_NOTES,
                Settings.KEEP_SCREEN_ON};
        CharSequence[] labels = {getString(R.string.setting_show_mistakes), getString(R.string.setting_highlight_same),
                getString(R.string.setting_auto_notes), getString(R.string.setting_screen_on)};
        boolean[] values = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = s.get(keys[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setMultiChoiceItems(labels, values, (d, which, checked) -> s.set(keys[which], checked))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.reset_progress, (d, w) -> confirmReset())
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_progress)
                .setMessage(R.string.reset_progress_message)
                .setPositiveButton(R.string.reset, (d, w) -> {
                    store.resetAll();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_to_play)
                .setMessage(R.string.help_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
