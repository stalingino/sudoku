package com.stalingino.sudoku;

/**
 * Grades a puzzle by the human techniques needed to solve it without guessing.
 *
 * <ol>
 *   <li>{@link #SINGLES}: naked and hidden singles only.</li>
 *   <li>{@link #INTERMEDIATE}: also locked candidates (pointing / claiming) and naked pairs.</li>
 *   <li>{@link #ADVANCED}: none of the above is enough.</li>
 * </ol>
 */
public final class Grader {
    public static final int SINGLES = 1;
    public static final int INTERMEDIATE = 2;
    public static final int ADVANCED = 3;

    /** All 27 units (9 rows, 9 columns, 9 boxes) as lists of cell indices. */
    static final int[][] UNITS = new int[27][9];

    static {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                UNITS[i][j] = i * 9 + j;
                UNITS[9 + i][j] = j * 9 + i;
                UNITS[18 + i][j] = ((i / 3) * 3 + j / 3) * 9 + (i % 3) * 3 + j % 3;
            }
        }
    }

    private final int[] grid = new int[81];
    private final int[] cand = new int[81];
    private int intermediateSteps;

    private Grader(int[] puzzle) {
        System.arraycopy(puzzle, 0, grid, 0, 81);
        for (int i = 0; i < 81; i++) cand[i] = grid[i] == 0 ? 0x3FE : 0;
        for (int i = 0; i < 81; i++) if (grid[i] != 0) eliminatePeers(i, grid[i]);
    }

    /** Returns SINGLES, INTERMEDIATE or ADVANCED. */
    public static int grade(int[] puzzle) {
        return new Grader(puzzle).run();
    }

    /** Number of intermediate eliminations needed (0 for singles-only puzzles); -1 if advanced. */
    public static int intermediateSteps(int[] puzzle) {
        Grader g = new Grader(puzzle);
        return g.run() == ADVANCED ? -1 : g.intermediateSteps;
    }

    private int run() {
        int level = SINGLES;
        while (true) {
            if (solved()) return level;
            if (applySingles()) continue;
            if (applyLockedCandidates() || applyNakedPairs()) {
                intermediateSteps++;
                level = INTERMEDIATE;
                continue;
            }
            return ADVANCED;
        }
    }

    private boolean solved() {
        for (int v : grid) if (v == 0) return false;
        return true;
    }

    private void place(int i, int d) {
        grid[i] = d;
        cand[i] = 0;
        eliminatePeers(i, d);
    }

    private void eliminatePeers(int i, int d) {
        int r = i / 9, c = i % 9, bit = ~(1 << d);
        for (int k = 0; k < 9; k++) {
            cand[r * 9 + k] &= bit;
            cand[k * 9 + c] &= bit;
        }
        int br = (r / 3) * 3, bc = (c / 3) * 3;
        for (int k = 0; k < 9; k++) cand[(br + k / 3) * 9 + bc + k % 3] &= bit;
    }

    private boolean applySingles() {
        boolean progress = false;
        for (int i = 0; i < 81; i++) {
            if (grid[i] == 0 && Integer.bitCount(cand[i]) == 1) {
                place(i, Integer.numberOfTrailingZeros(cand[i]));
                progress = true;
            }
        }
        for (int[] unit : UNITS) {
            for (int d = 1; d <= 9; d++) {
                int bit = 1 << d, where = -1, n = 0;
                for (int i : unit) {
                    if ((cand[i] & bit) != 0) { where = i; n++; }
                }
                if (n == 1 && grid[where] == 0) {
                    place(where, d);
                    progress = true;
                }
            }
        }
        return progress;
    }

    /** Pointing (box -> line) and claiming (line -> box) eliminations. */
    private boolean applyLockedCandidates() {
        boolean progress = false;
        for (int b = 0; b < 9; b++) {
            int[] box = UNITS[18 + b];
            for (int d = 1; d <= 9; d++) {
                int bit = 1 << d, rows = 0, cols = 0;
                for (int i : box) {
                    if ((cand[i] & bit) != 0) { rows |= 1 << (i / 9); cols |= 1 << (i % 9); }
                }
                if (Integer.bitCount(rows) == 1) {
                    int r = Integer.numberOfTrailingZeros(rows);
                    for (int c = 0; c < 9; c++) {
                        int i = r * 9 + c;
                        if (boxOf(i) != b && (cand[i] & bit) != 0) { cand[i] &= ~bit; progress = true; }
                    }
                }
                if (Integer.bitCount(cols) == 1) {
                    int c = Integer.numberOfTrailingZeros(cols);
                    for (int r = 0; r < 9; r++) {
                        int i = r * 9 + c;
                        if (boxOf(i) != b && (cand[i] & bit) != 0) { cand[i] &= ~bit; progress = true; }
                    }
                }
            }
        }
        for (int u = 0; u < 18; u++) {
            int[] line = UNITS[u];
            for (int d = 1; d <= 9; d++) {
                int bit = 1 << d, boxes = 0;
                for (int i : line) if ((cand[i] & bit) != 0) boxes |= 1 << boxOf(i);
                if (Integer.bitCount(boxes) != 1) continue;
                int b = Integer.numberOfTrailingZeros(boxes);
                for (int i : UNITS[18 + b]) {
                    if (!contains(line, i) && (cand[i] & bit) != 0) { cand[i] &= ~bit; progress = true; }
                }
            }
        }
        return progress;
    }

    private boolean applyNakedPairs() {
        boolean progress = false;
        for (int[] unit : UNITS) {
            for (int a = 0; a < 9; a++) {
                int ma = cand[unit[a]];
                if (Integer.bitCount(ma) != 2) continue;
                for (int b = a + 1; b < 9; b++) {
                    if (cand[unit[b]] != ma) continue;
                    for (int k = 0; k < 9; k++) {
                        int i = unit[k];
                        if (k != a && k != b && (cand[i] & ma) != 0) { cand[i] &= ~ma; progress = true; }
                    }
                }
            }
        }
        return progress;
    }

    private static int boxOf(int i) {
        return (i / 27) * 3 + (i % 9) / 3;
    }

    private static boolean contains(int[] unit, int i) {
        for (int x : unit) if (x == i) return true;
        return false;
    }
}
