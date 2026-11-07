package com.stalingino.sudoku;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One stored puzzle. The original SDK81 string is kept unchanged in {@link #puzzle};
 * the player's progress lives in a separate 81-character string, {@link #progress}.
 */
public final class PuzzleRecord {
    public static final String KIND_IMPORT = "import";
    public static final String KIND_LEVEL = "level";
    public static final String KIND_DAILY = "daily";

    public final String id;
    public final String puzzle;
    public String kind = KIND_IMPORT;
    /** Level pack (levels only). */
    public Difficulty difficulty;
    /** 1-based level number (levels only). */
    public int level;
    /** yyyy-MM-dd (daily only). */
    public String date;

    // Current attempt.
    public String progress;
    /** Pencil marks: 81 groups of 3 hex chars, one 9-bit mask per cell. */
    public String notes;
    public long elapsedSeconds;
    public boolean solved;
    public int hints;
    public int mistakes;

    // Best results over all attempts.
    public int stars;
    public long bestSeconds;
    public int timesSolved;

    public PuzzleRecord(String id, String puzzle) {
        this.id = id;
        this.puzzle = puzzle;
        this.progress = puzzle;
        this.notes = "";
    }

    public static String levelId(Difficulty d, int level) {
        return "L-" + d.code + "-" + level;
    }

    public static String dailyId(String date) {
        return "D-" + date;
    }

    public boolean isLevel() { return KIND_LEVEL.equals(kind); }
    public boolean isDaily() { return KIND_DAILY.equals(kind); }
    public boolean isImport() { return KIND_IMPORT.equals(kind); }

    /** Name shown to the player. */
    public String displayName() {
        if (isLevel() && difficulty != null) return difficulty.label + " · Level " + level;
        if (isDaily()) return "Daily · " + date;
        return id;
    }

    public int givens() {
        int n = 0;
        for (int i = 0; i < puzzle.length(); i++) if (puzzle.charAt(i) != '0') n++;
        return n;
    }

    public int filled() {
        int n = 0;
        for (int i = 0; i < progress.length(); i++) if (progress.charAt(i) != '0') n++;
        return n;
    }

    /** Share of the empty cells the player has filled, 0-100. */
    public int percent() {
        int empty = 81 - givens();
        return empty == 0 ? 100 : Math.round(100f * (filled() - givens()) / empty);
    }

    public boolean inProgress() {
        return !solved && (filled() > givens() || !notes.isEmpty() || elapsedSeconds > 0);
    }

    /** Difficulty label: the pack for levels, otherwise a rough guess from the clue count. */
    public String difficultyLabel() {
        if (difficulty != null) return difficulty.label;
        int g = givens();
        if (g >= 36) return "Easy";
        if (g >= 30) return "Medium";
        if (g >= 25) return "Hard";
        return "Expert";
    }

    /** Stars for the attempt just finished: 3, minus one for any hint, minus one for any mistake. */
    public int attemptStars() {
        return 3 - (hints > 0 ? 1 : 0) - (mistakes > 0 ? 1 : 0);
    }

    /** Records a finished attempt; returns true if it set a new best time. */
    public boolean complete() {
        solved = true;
        timesSolved++;
        stars = Math.max(stars, attemptStars());
        boolean best = bestSeconds == 0 || elapsedSeconds < bestSeconds;
        if (best) bestSeconds = elapsedSeconds;
        return best;
    }

    /** Clears the current attempt; best results are kept. */
    public void reset() {
        progress = puzzle;
        notes = "";
        elapsedSeconds = 0;
        solved = false;
        hints = 0;
        mistakes = 0;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("format", Sdk81.FORMAT);
        o.put("puzzle", puzzle);
        o.put("kind", kind);
        if (difficulty != null) o.put("difficulty", difficulty.code);
        if (level > 0) o.put("level", level);
        if (date != null) o.put("date", date);
        o.put("progress", progress);
        o.put("notes", notes);
        o.put("elapsed", elapsedSeconds);
        o.put("solved", solved);
        o.put("hints", hints);
        o.put("mistakes", mistakes);
        o.put("stars", stars);
        o.put("best", bestSeconds);
        o.put("times", timesSolved);
        return o;
    }

    static PuzzleRecord fromJson(JSONObject o) throws JSONException {
        PuzzleRecord r = new PuzzleRecord(o.getString("id"), o.getString("puzzle"));
        r.kind = o.optString("kind", KIND_IMPORT);
        r.difficulty = Difficulty.fromCode(o.optString("difficulty", ""));
        r.level = o.optInt("level", 0);
        r.date = o.has("date") ? o.getString("date") : null;
        String progress = o.optString("progress", r.puzzle);
        r.progress = Sdk81.isValidFormat(progress) ? progress : r.puzzle;
        r.notes = o.optString("notes", "");
        r.elapsedSeconds = o.optLong("elapsed", 0);
        r.solved = o.optBoolean("solved", false);
        r.hints = o.optInt("hints", 0);
        r.mistakes = o.optInt("mistakes", 0);
        r.stars = o.optInt("stars", 0);
        r.bestSeconds = o.optLong("best", 0);
        r.timesSolved = o.optInt("times", r.solved ? 1 : 0);
        return r;
    }

    static String encodeNotes(int[] masks) {
        StringBuilder sb = new StringBuilder(81 * 3);
        boolean any = false;
        for (int m : masks) {
            if (m != 0) any = true;
            String h = Integer.toHexString(m & 0x3FE);
            for (int k = h.length(); k < 3; k++) sb.append('0');
            sb.append(h);
        }
        return any ? sb.toString() : "";
    }

    static int[] decodeNotes(String s) {
        int[] masks = new int[81];
        if (s == null || s.length() != 81 * 3) return masks;
        for (int i = 0; i < 81; i++) {
            try {
                masks[i] = Integer.parseInt(s.substring(i * 3, i * 3 + 3), 16) & 0x3FE;
            } catch (NumberFormatException ignored) {
                masks[i] = 0;
            }
        }
        return masks;
    }
}
