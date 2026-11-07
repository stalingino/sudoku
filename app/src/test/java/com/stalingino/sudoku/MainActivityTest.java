package com.stalingino.sudoku;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    private static final String SDU3 = PuzzleStore.DEFAULT_PUZZLE;
    // A different valid puzzle (well-known easy grid).
    private static final String OTHER =
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079";

    @Before
    public void clearPrefs() {
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("sudoku_store", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void startsWithDefaultPuzzle() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        TextView title = a.findViewById(R.id.title);
        assertTrue(title.getText().toString().startsWith("SDU-3"));
        assertEquals(SDU3, new PuzzleStore(a).current().puzzle);
    }

    @Test
    public void playMoveIsSavedAsSeparateProgress() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        SudokuBoardView board = a.findViewById(R.id.board);
        board.layout(0, 0, 900, 900);
        // Select row 0, col 0 (empty in SDU-3) and tap digit 2 (solution value is irrelevant here).
        board.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0,
                android.view.MotionEvent.ACTION_DOWN, 50, 50, 0));
        ((android.widget.LinearLayout) a.findViewById(R.id.keypad)).getChildAt(1).performClick();
        c.pause();
        PuzzleRecord r = new PuzzleStore(a).current();
        assertEquals(SDU3, r.puzzle);                // original kept unchanged
        assertEquals('2', r.progress.charAt(0));     // progress stored separately
        assertEquals(SDU3.substring(1), r.progress.substring(1));
    }

    @Test
    public void importsViaDialog() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.onOptionsItemSelected(new RoboMenuItem(R.id.action_import));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        EditText input = d.findViewById(R.id.import_input);
        input.setText("530 070 000\n600 195 000\n098 000 060\n800 060 003\n400 803 001\n"
                + "700 020 006\n060 000 280\n000 419 005\n000 080 079");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(d.isShowing());
        PuzzleRecord cur = new PuzzleStore(a).current();
        assertEquals(OTHER, cur.puzzle);
        assertEquals("IMP-1", cur.id);
    }

    @Test
    public void rejectsInvalidImport() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.onOptionsItemSelected(new RoboMenuItem(R.id.action_import));
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        ((EditText) d.findViewById(R.id.import_input)).setText("11" + SDU3.substring(2));
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(d.isShowing());
        String status = ((TextView) d.findViewById(R.id.import_status)).getText().toString();
        assertTrue(status, status.contains("repeats"));
        assertEquals(1, new PuzzleStore(a).all().size());
    }

    @Test
    public void importsFromShareIntent() {
        Intent share = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, OTHER).setType("text/plain");
        MainActivity a = Robolectric.buildActivity(MainActivity.class, share).setup().get();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(OTHER, ((EditText) d.findViewById(R.id.import_input)).getText().toString());
    }
}
