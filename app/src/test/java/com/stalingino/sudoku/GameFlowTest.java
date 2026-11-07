package com.stalingino.sudoku;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GameFlowTest {
    private static final String OTHER =
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079";

    @Before
    public void clean() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("sudoku_store", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("sudoku_settings", Context.MODE_PRIVATE).edit().clear().commit();
        PuzzleStore.resetInstance();
    }

    /** Runs queued main-thread work (dialog listeners, button clicks). */
    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private PuzzleStore store() {
        return PuzzleStore.get(RuntimeEnvironment.getApplication());
    }

    private ActivityController<GameActivity> openGame(PuzzleRecord r) {
        Intent i = new Intent(RuntimeEnvironment.getApplication(), GameActivity.class)
                .putExtra(GameActivity.EXTRA_ID, r.id);
        ActivityController<GameActivity> c = Robolectric.buildActivity(GameActivity.class, i).setup();
        c.get().findViewById(R.id.board).layout(0, 0, 900, 900);
        return c;
    }

    private void enter(GameActivity a, int index, int digit) {
        SudokuBoardView board = a.findViewById(R.id.board);
        float x = (index % 9) * 100 + 50, y = (index / 9) * 100 + 50;
        long t = SystemClock.uptimeMillis();
        board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0));
        a.onKeyDown(KeyEvent.KEYCODE_0 + digit, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0 + digit));
    }

    private void solve(GameActivity a, PuzzleRecord r) throws Exception {
        int[] p = Sdk81.parse(r.puzzle);
        int[] s = Solver.solve(p);
        for (int i = 0; i < 81; i++) {
            if (p[i] == 0 && Sdk81.parse(r.progress)[i] != s[i]) enter(a, i, s[i]);
        }
    }

    @Test
    public void homeShowsPlayForFirstEasyLevel() {
        HomeActivity a = Robolectric.buildActivity(HomeActivity.class).setup().get();
        assertEquals("Easy · Level 1", ((TextView) a.findViewById(R.id.play_subtitle)).getText().toString());
        a.findViewById(R.id.play).performClick();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(GameActivity.class.getName(), next.getComponent().getClassName());
        assertEquals(PuzzleRecord.levelId(Difficulty.EASY, 1), next.getStringExtra(GameActivity.EXTRA_ID));
    }

    @Test
    public void solvingLevelGivesThreeStarsAndUnlocksNext() throws Exception {
        PuzzleRecord r = store().level(Difficulty.EASY, 1);
        assertFalse(store().isUnlocked(Difficulty.EASY, 2));
        GameActivity a = openGame(r).get();
        solve(a, r);
        assertTrue(r.solved);
        assertEquals(3, r.stars);
        assertTrue(store().isUnlocked(Difficulty.EASY, 2));
        assertFalse(store().isUnlocked(Difficulty.EASY, 3));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(d.isShowing());
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); // Next level
        idle();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(PuzzleRecord.levelId(Difficulty.EASY, 2), next.getStringExtra(GameActivity.EXTRA_ID));
    }

    @Test
    public void hintsAndMistakesCostStars() throws Exception {
        PuzzleRecord r = store().level(Difficulty.MEDIUM, 1);
        GameActivity a = openGame(r).get();
        int[] p = Sdk81.parse(r.puzzle);
        int[] s = Solver.solve(p);
        int first = 0;
        while (p[first] != 0) first++;
        enter(a, first, s[first] % 9 + 1); // wrong digit
        assertEquals(1, r.mistakes);
        a.findViewById(R.id.btn_hint).performClick(); // fixes the selected wrong cell
        assertEquals(1, r.hints);
        assertEquals(s[first], Sdk81.parse(r.progress)[first]);
        solve(a, r);
        assertTrue(r.solved);
        assertEquals(1, r.stars);
    }

    @Test
    public void progressIsSavedSeparately() throws Exception {
        PuzzleRecord r = store().level(Difficulty.EASY, 1);
        ActivityController<GameActivity> c = openGame(r);
        int[] p = Sdk81.parse(r.puzzle);
        int first = 0;
        while (p[first] != 0) first++;
        enter(c.get(), first, 5);
        c.pause();
        assertEquals(store().level(Difficulty.EASY, 1).puzzle, r.puzzle);
        assertEquals('5', r.progress.charAt(first));
        assertNotEquals(r.puzzle, r.progress);
        assertEquals(r.id, store().current().id);
    }

    @Test
    public void lockedLevelShowsToast() {
        LevelsActivity a = Robolectric.buildActivity(LevelsActivity.class).setup().get();
        GridView grid = a.findViewById(R.id.grid);
        assertEquals(50, grid.getAdapter().getCount());
        grid.performItemClick(null, 4, 4); // level 5
        assertEquals("Solve level 4 first", ShadowToast.getTextOfLatestToast());
        assertNull(shadowOf(a).getNextStartedActivity());
        grid.performItemClick(null, 0, 0); // level 1
        assertNotNull(shadowOf(a).getNextStartedActivity());
    }

    @Test
    public void dailyIsStableForADate() throws Exception {
        PuzzleRecord a = store().daily("2026-11-07");
        String puzzle = a.puzzle;
        store().resetAll();
        assertEquals(puzzle, store().daily("2026-11-07").puzzle);
        assertNotEquals(puzzle, store().daily("2026-11-08").puzzle);
        assertEquals(1, Solver.countSolutions(Sdk81.parse(puzzle), 2));
    }

    @Test
    public void importFromHomeOpensGame() {
        HomeActivity a = Robolectric.buildActivity(HomeActivity.class).setup().get();
        a.findViewById(R.id.card_import).performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        ((EditText) d.findViewById(R.id.import_input)).setText(
                "530 070 000\n600 195 000\n098 000 060\n800 060 003\n400 803 001\n"
                        + "700 020 006\n060 000 280\n000 419 005\n000 080 079");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertFalse(d.isShowing());
        PuzzleRecord r = store().findImportByPuzzle(OTHER);
        assertNotNull(r);
        assertEquals("IMP-1", r.id);
        assertEquals(r.id, shadowOf(a).getNextStartedActivity().getStringExtra(GameActivity.EXTRA_ID));
    }

    @Test
    public void invalidImportIsRejected() {
        HomeActivity a = Robolectric.buildActivity(HomeActivity.class).setup().get();
        a.findViewById(R.id.card_import).performClick();
        idle();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        ((EditText) d.findViewById(R.id.import_input)).setText("11" + PuzzleStore.DEFAULT_PUZZLE.substring(2));
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue(d.isShowing());
        String status = ((TextView) d.findViewById(R.id.import_status)).getText().toString();
        assertTrue(status, status.contains("repeats"));
        assertEquals(1, store().imports().size());
    }

    @Test
    public void shareIntentPrefillsImport() {
        Intent share = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, OTHER).setType("text/plain");
        Robolectric.buildActivity(HomeActivity.class, share).setup();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(OTHER, ((EditText) d.findViewById(R.id.import_input)).getText().toString());
    }
}
