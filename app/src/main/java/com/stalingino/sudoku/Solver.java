package com.stalingino.sudoku;

/** Bitmask backtracking solver with minimum-remaining-values heuristic. */
public final class Solver {
    private final int[] grid = new int[81];
    private final int[] rows = new int[9];
    private final int[] cols = new int[9];
    private final int[] boxes = new int[9];
    private int[] firstSolution;
    private int count;
    private int limit;

    private Solver() {}

    /** Returns the number of solutions, stopping once {@code limit} is reached. */
    public static int countSolutions(int[] puzzle, int limit) {
        Solver s = new Solver();
        return s.run(puzzle, limit) ? s.count : 0;
    }

    /** Returns a solution, or null when the puzzle is contradictory or unsolvable. */
    public static int[] solve(int[] puzzle) {
        Solver s = new Solver();
        return s.run(puzzle, 1) ? s.firstSolution : null;
    }

    private boolean run(int[] puzzle, int limit) {
        this.limit = limit;
        for (int i = 0; i < 81; i++) {
            int v = puzzle[i];
            grid[i] = v;
            if (v == 0) continue;
            int bit = 1 << v, r = i / 9, c = i % 9, b = (r / 3) * 3 + c / 3;
            if ((rows[r] & bit) != 0 || (cols[c] & bit) != 0 || (boxes[b] & bit) != 0) return false;
            rows[r] |= bit;
            cols[c] |= bit;
            boxes[b] |= bit;
        }
        search();
        return true;
    }

    private void search() {
        if (count >= limit) return;
        int best = -1, bestMask = 0, bestCount = 10;
        for (int i = 0; i < 81; i++) {
            if (grid[i] != 0) continue;
            int r = i / 9, c = i % 9, b = (r / 3) * 3 + c / 3;
            int mask = ~(rows[r] | cols[c] | boxes[b]) & 0x3FE;
            int n = Integer.bitCount(mask);
            if (n < bestCount) {
                best = i;
                bestMask = mask;
                bestCount = n;
                if (n <= 1) break;
            }
        }
        if (best < 0) {
            count++;
            if (firstSolution == null) firstSolution = grid.clone();
            return;
        }
        if (bestCount == 0) return;
        int r = best / 9, c = best % 9, b = (r / 3) * 3 + c / 3;
        for (int v = 1; v <= 9; v++) {
            int bit = 1 << v;
            if ((bestMask & bit) == 0) continue;
            grid[best] = v;
            rows[r] |= bit;
            cols[c] |= bit;
            boxes[b] |= bit;
            search();
            rows[r] &= ~bit;
            cols[c] &= ~bit;
            boxes[b] &= ~bit;
            grid[best] = 0;
            if (count >= limit) return;
        }
    }
}
