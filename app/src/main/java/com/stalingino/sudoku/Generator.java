package com.stalingino.sudoku;

import java.util.Random;

/** Creates puzzles with exactly one solution from a seed (same seed, same puzzle). */
public final class Generator {
    private Generator() {}

    /** A random complete, valid grid. */
    public static int[] fullGrid(Random rnd) {
        int[] g = new int[81];
        fill(g, 0, rnd);
        return g;
    }

    private static boolean fill(int[] g, int i, Random rnd) {
        if (i == 81) return true;
        int[] digits = shuffled(rnd, 9);
        int r = i / 9, c = i % 9;
        for (int k = 0; k < 9; k++) {
            int d = digits[k] + 1;
            if (canPlace(g, r, c, d)) {
                g[i] = d;
                if (fill(g, i + 1, rnd)) return true;
                g[i] = 0;
            }
        }
        return false;
    }

    private static boolean canPlace(int[] g, int r, int c, int d) {
        for (int k = 0; k < 9; k++) {
            if (g[r * 9 + k] == d || g[k * 9 + c] == d) return false;
        }
        int br = (r / 3) * 3, bc = (c / 3) * 3;
        for (int k = 0; k < 9; k++) if (g[(br + k / 3) * 9 + bc + k % 3] == d) return false;
        return true;
    }

    /**
     * Removes clues from a full grid in random symmetric pairs while the solution stays unique,
     * stopping once at most {@code targetGivens} remain (or no more clues can go).
     */
    public static int[] dig(int[] full, int targetGivens, Random rnd) {
        int[] p = full.clone();
        int givens = 81;
        int[] order = shuffled(rnd, 41); // cell i and its 180° partner 80 - i
        for (int k = 0; k < order.length && givens > targetGivens; k++) {
            int a = order[k], b = 80 - a;
            int va = p[a], vb = p[b];
            p[a] = 0;
            p[b] = 0;
            if (Solver.countSolutions(p, 2) == 1) {
                givens -= (a == b) ? 1 : 2;
            } else {
                p[a] = va;
                p[b] = vb;
            }
        }
        return p;
    }

    /**
     * Generates a puzzle of the given difficulty. Retries with the same random stream until
     * the grade matches, so the result depends only on the seed.
     */
    public static int[] generate(Difficulty diff, long seed) {
        Random rnd = new Random(seed);
        int[] best = null;
        for (int attempt = 0; attempt < 400; attempt++) {
            int[] full = fullGrid(rnd);
            int[] p = dig(full, targetGivens(diff), rnd);
            int grade = Grader.grade(p);
            int givens = Sdk81.countGivens(p);
            best = p;
            switch (diff) {
                case EASY:
                    if (grade == Grader.SINGLES && givens >= 36) return p;
                    break;
                case MEDIUM:
                    if (grade == Grader.SINGLES && givens <= 32) return p;
                    break;
                case HARD:
                    if (grade == Grader.INTERMEDIATE) return p;
                    break;
                case EXPERT:
                    if (grade == Grader.ADVANCED) return p;
                    break;
            }
        }
        return best;
    }

    static int targetGivens(Difficulty diff) {
        switch (diff) {
            case EASY: return 38;
            case MEDIUM: return 31;
            case HARD: return 26;
            default: return 17; // dig until minimal
        }
    }

    private static int[] shuffled(Random rnd, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return a;
    }
}
