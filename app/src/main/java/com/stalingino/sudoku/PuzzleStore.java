package com.stalingino.sudoku;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Persists the puzzle library and per-puzzle progress in SharedPreferences. */
public final class PuzzleStore {
    public static final String DEFAULT_ID = "SDU-3";
    public static final String DEFAULT_PUZZLE =
            "000013400080006950650000000960201000100070002000304016000000079025800040019760000";

    private static final String PREFS = "sudoku_store";
    private static final String KEY_PUZZLES = "puzzles";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_NEXT_ID = "next_id";

    private final SharedPreferences prefs;
    private final List<PuzzleRecord> puzzles = new ArrayList<>();

    public PuzzleStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (puzzles.isEmpty()) {
            puzzles.add(new PuzzleRecord(DEFAULT_ID, DEFAULT_PUZZLE));
            prefs.edit().putString(KEY_CURRENT, DEFAULT_ID).apply();
            save();
        }
    }

    public List<PuzzleRecord> all() {
        return puzzles;
    }

    public PuzzleRecord current() {
        String id = prefs.getString(KEY_CURRENT, null);
        PuzzleRecord r = find(id);
        return r != null ? r : puzzles.get(0);
    }

    public void setCurrent(PuzzleRecord r) {
        prefs.edit().putString(KEY_CURRENT, r.id).apply();
    }

    public PuzzleRecord find(String id) {
        if (id == null) return null;
        for (PuzzleRecord r : puzzles) if (r.id.equals(id)) return r;
        return null;
    }

    public PuzzleRecord findByPuzzle(String puzzle) {
        for (PuzzleRecord r : puzzles) if (r.puzzle.equals(puzzle)) return r;
        return null;
    }

    /** Adds a puzzle (already validated) and returns its record; existing duplicates are reused. */
    public PuzzleRecord add(String puzzle) {
        PuzzleRecord existing = findByPuzzle(puzzle);
        if (existing != null) return existing;
        int next = prefs.getInt(KEY_NEXT_ID, 1);
        String id;
        do {
            id = "IMP-" + next++;
        } while (find(id) != null);
        prefs.edit().putInt(KEY_NEXT_ID, next).apply();
        PuzzleRecord r = new PuzzleRecord(id, puzzle);
        puzzles.add(r);
        save();
        return r;
    }

    public void delete(PuzzleRecord r) {
        puzzles.remove(r);
        if (puzzles.isEmpty()) puzzles.add(new PuzzleRecord(DEFAULT_ID, DEFAULT_PUZZLE));
        if (current() == r || find(prefs.getString(KEY_CURRENT, null)) == null) {
            setCurrent(puzzles.get(0));
        }
        save();
    }

    public void save() {
        JSONArray arr = new JSONArray();
        try {
            for (PuzzleRecord r : puzzles) arr.put(r.toJson());
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_PUZZLES, arr.toString()).apply();
    }

    private void load() {
        puzzles.clear();
        String raw = prefs.getString(KEY_PUZZLES, null);
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                PuzzleRecord r = PuzzleRecord.fromJson(arr.getJSONObject(i));
                if (Sdk81.isValidFormat(r.puzzle)) puzzles.add(r);
            }
        } catch (JSONException ignored) {
            puzzles.clear();
        }
    }
}
