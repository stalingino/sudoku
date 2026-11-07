package com.stalingino.sudoku;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One stored puzzle. The original SDK81 string is kept unchanged in {@link #puzzle};
 * the player's progress lives in a separate 81-character string, {@link #progress}.
 */
public final class PuzzleRecord {
    public final String id;
    public final String puzzle;
    public String progress;
    /** Pencil marks: 81 groups of 3 hex chars, one 9-bit mask per cell. */
    public String notes;
    public long elapsedSeconds;
    public boolean solved;
    public int mistakesChecked;

    public PuzzleRecord(String id, String puzzle) {
        this.id = id;
        this.puzzle = puzzle;
        this.progress = puzzle;
        this.notes = "";
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

    /** Rough difficulty label from the number of given cells. */
    public String difficulty() {
        int g = givens();
        if (g >= 36) return "Easy";
        if (g >= 30) return "Medium";
        if (g >= 25) return "Hard";
        return "Expert";
    }

    public void reset() {
        progress = puzzle;
        notes = "";
        elapsedSeconds = 0;
        solved = false;
        mistakesChecked = 0;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("format", Sdk81.FORMAT);
        o.put("puzzle", puzzle);
        o.put("progress", progress);
        o.put("notes", notes);
        o.put("elapsed", elapsedSeconds);
        o.put("solved", solved);
        o.put("checks", mistakesChecked);
        return o;
    }

    static PuzzleRecord fromJson(JSONObject o) throws JSONException {
        PuzzleRecord r = new PuzzleRecord(o.getString("id"), o.getString("puzzle"));
        String progress = o.optString("progress", r.puzzle);
        r.progress = Sdk81.isValidFormat(progress) ? progress : r.puzzle;
        r.notes = o.optString("notes", "");
        r.elapsedSeconds = o.optLong("elapsed", 0);
        r.solved = o.optBoolean("solved", false);
        r.mistakesChecked = o.optInt("checks", 0);
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
