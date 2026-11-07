package com.stalingino.sudoku;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persists every puzzle the player has touched (imports, levels, dailies) and their progress. */
public final class PuzzleStore {
    public static final String DEFAULT_ID = "SDU-3";
    public static final String DEFAULT_PUZZLE =
            "000013400080006950650000000960201000100070002000304016000000079025800040019760000";

    private static final String PREFS = "sudoku_store";
    private static final String KEY_PUZZLES = "puzzles";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_NEXT_ID = "next_id";

    private static PuzzleStore instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final Map<String, PuzzleRecord> puzzles = new LinkedHashMap<>();

    public static synchronized PuzzleStore get(Context context) {
        if (instance == null) instance = new PuzzleStore(context.getApplicationContext());
        return instance;
    }

    /** Drops the cached instance (tests). */
    static synchronized void resetInstance() {
        instance = null;
    }

    private PuzzleStore(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (puzzles.isEmpty()) {
            put(new PuzzleRecord(DEFAULT_ID, DEFAULT_PUZZLE));
            save();
        }
    }

    // ------------------------------------------------------------ lookup

    public PuzzleRecord find(String id) {
        return id == null ? null : puzzles.get(id);
    }

    /** The puzzle last opened, or null. */
    public PuzzleRecord current() {
        return find(prefs.getString(KEY_CURRENT, null));
    }

    public void setCurrent(PuzzleRecord r) {
        prefs.edit().putString(KEY_CURRENT, r.id).apply();
    }

    public List<PuzzleRecord> imports() {
        List<PuzzleRecord> out = new ArrayList<>();
        for (PuzzleRecord r : puzzles.values()) if (r.isImport()) out.add(r);
        return out;
    }

    // ------------------------------------------------------------ levels

    /** The record for a level, created from the bundled pack on first use. */
    public PuzzleRecord level(Difficulty d, int level) {
        String id = PuzzleRecord.levelId(d, level);
        PuzzleRecord r = find(id);
        if (r == null) {
            r = new PuzzleRecord(id, LevelPack.get(app, d).get(level - 1));
            r.kind = PuzzleRecord.KIND_LEVEL;
            r.difficulty = d;
            r.level = level;
            put(r);
            save();
        }
        return r;
    }

    /** Level 1 is always open; every other level opens once the one before it is solved. */
    public boolean isUnlocked(Difficulty d, int level) {
        return level <= 1 || isCompleted(d, level - 1);
    }

    public boolean isCompleted(Difficulty d, int level) {
        PuzzleRecord r = find(PuzzleRecord.levelId(d, level));
        return r != null && r.timesSolved > 0;
    }

    public int stars(Difficulty d, int level) {
        PuzzleRecord r = find(PuzzleRecord.levelId(d, level));
        return r == null ? 0 : r.stars;
    }

    public int completedCount(Difficulty d) {
        int n = 0;
        for (PuzzleRecord r : puzzles.values()) if (r.isLevel() && r.difficulty == d && r.timesSolved > 0) n++;
        return n;
    }

    public int starCount(Difficulty d) {
        int n = 0;
        for (PuzzleRecord r : puzzles.values()) if (r.isLevel() && r.difficulty == d) n += r.stars;
        return n;
    }

    public long bestTime(Difficulty d) {
        long best = 0;
        for (PuzzleRecord r : puzzles.values()) {
            if (r.isLevel() && r.difficulty == d && r.bestSeconds > 0 && (best == 0 || r.bestSeconds < best)) {
                best = r.bestSeconds;
            }
        }
        return best;
    }

    /** First level in the pack not yet solved (or the last level when all are done). */
    public int nextLevel(Difficulty d) {
        int size = LevelPack.get(app, d).size();
        for (int i = 1; i <= size; i++) if (!isCompleted(d, i)) return i;
        return size;
    }

    // ------------------------------------------------------------ daily

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Today's puzzle: generated from the date, so everyone gets the same one. */
    public PuzzleRecord daily(String date) {
        String id = PuzzleRecord.dailyId(date);
        PuzzleRecord r = find(id);
        if (r == null) {
            long seed = date.hashCode() * 31L + 7;
            r = new PuzzleRecord(id, Sdk81.serialize(Generator.generate(Difficulty.MEDIUM, seed)));
            r.kind = PuzzleRecord.KIND_DAILY;
            r.date = date;
            put(r);
            save();
        }
        return r;
    }

    /** Consecutive days with a solved daily, ending today (or yesterday if today is open). */
    public int dailyStreak() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        if (!dailySolved(f.format(cal.getTime()))) cal.add(Calendar.DAY_OF_YEAR, -1);
        int streak = 0;
        while (dailySolved(f.format(cal.getTime()))) {
            streak++;
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    public int dailiesSolved() {
        int n = 0;
        for (PuzzleRecord r : puzzles.values()) if (r.isDaily() && r.timesSolved > 0) n++;
        return n;
    }

    private boolean dailySolved(String date) {
        PuzzleRecord r = find(PuzzleRecord.dailyId(date));
        return r != null && r.timesSolved > 0;
    }

    // ------------------------------------------------------------ totals

    public int totalSolved() {
        int n = 0;
        for (PuzzleRecord r : puzzles.values()) if (r.timesSolved > 0) n++;
        return n;
    }

    // ------------------------------------------------------------ imports

    public PuzzleRecord findImportByPuzzle(String puzzle) {
        for (PuzzleRecord r : puzzles.values()) if (r.isImport() && r.puzzle.equals(puzzle)) return r;
        return null;
    }

    /** Adds an imported puzzle (already validated); an identical existing import is reused. */
    public PuzzleRecord addImport(String puzzle) {
        PuzzleRecord existing = findImportByPuzzle(puzzle);
        if (existing != null) return existing;
        int next = prefs.getInt(KEY_NEXT_ID, 1);
        String id;
        do {
            id = "IMP-" + next++;
        } while (find(id) != null);
        prefs.edit().putInt(KEY_NEXT_ID, next).apply();
        PuzzleRecord r = new PuzzleRecord(id, puzzle);
        put(r);
        save();
        return r;
    }

    public void delete(PuzzleRecord r) {
        puzzles.remove(r.id);
        if (r.id.equals(prefs.getString(KEY_CURRENT, null))) prefs.edit().remove(KEY_CURRENT).apply();
        save();
    }

    /** Erases all progress, levels, dailies and imports. */
    public void resetAll() {
        puzzles.clear();
        prefs.edit().clear().apply();
        put(new PuzzleRecord(DEFAULT_ID, DEFAULT_PUZZLE));
        save();
    }

    // ------------------------------------------------------------ persistence

    private void put(PuzzleRecord r) {
        puzzles.put(r.id, r);
    }

    public void save() {
        JSONArray arr = new JSONArray();
        try {
            for (PuzzleRecord r : puzzles.values()) arr.put(r.toJson());
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
                if (Sdk81.isValidFormat(r.puzzle)) put(r);
            }
        } catch (JSONException ignored) {
            puzzles.clear();
        }
    }
}
