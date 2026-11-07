package com.stalingino.sudoku;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SDK81-v1 puzzle format.
 *
 * <ul>
 *   <li>Exactly 81 ASCII digits, row-major (index = row * 9 + col).</li>
 *   <li>'0' is an empty cell, '1'..'9' are pre-filled, immutable cells.</li>
 *   <li>Whitespace is only for readability and is removed before parsing.</li>
 * </ul>
 */
public final class Sdk81 {
    public static final String FORMAT = "SDK81-v1";
    public static final int SIZE = 81;
    private static final Pattern VALID = Pattern.compile("^[0-9]{81}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Sdk81() {}

    /** Thrown when an import string is not a valid SDK81 puzzle. */
    public static final class FormatException extends Exception {
        public FormatException(String message) { super(message); }
    }

    /** Removes all whitespace (spaces, tabs, newlines). */
    public static String normalize(String input) {
        return input == null ? "" : WHITESPACE.matcher(input).replaceAll("");
    }

    public static boolean isValidFormat(String s) {
        return s != null && VALID.matcher(s).matches();
    }

    /** Parses a single SDK81 string (whitespace allowed) into 81 ints. */
    public static int[] parse(String input) throws FormatException {
        String s = normalize(input);
        if (!isValidFormat(s)) {
            throw new FormatException(describeFormatError(s));
        }
        int[] grid = new int[SIZE];
        for (int i = 0; i < SIZE; i++) grid[i] = s.charAt(i) - '0';
        return grid;
    }

    /**
     * Parses text that may hold one or more puzzles. All whitespace is removed and the
     * remaining digits are split into consecutive 81-character puzzles.
     */
    public static List<String> parseMany(String input) throws FormatException {
        String s = normalize(input);
        if (s.isEmpty()) throw new FormatException("Nothing to import.");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new FormatException("Invalid character '" + c + "' at position " + (i + 1)
                        + ". Only digits 0-9 are allowed.");
            }
        }
        if (s.length() % SIZE != 0) {
            throw new FormatException("Expected a multiple of 81 digits but found " + s.length() + ".");
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i += SIZE) out.add(s.substring(i, i + SIZE));
        return out;
    }

    public static String serialize(int[] grid) {
        StringBuilder sb = new StringBuilder(SIZE);
        for (int i = 0; i < SIZE; i++) sb.append((char) ('0' + grid[i]));
        return sb.toString();
    }

    /** Human-readable form: 9 lines of three 3-digit groups. */
    public static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (c > 0 && c % 3 == 0) sb.append(' ');
                sb.append(s.charAt(r * 9 + c));
            }
            if (r < 8) sb.append('\n');
        }
        return sb.toString();
    }

    public static int countGivens(int[] grid) {
        int n = 0;
        for (int v : grid) if (v != 0) n++;
        return n;
    }

    /**
     * Returns the indices of cells whose value clashes with another cell in the same
     * row, column or 3x3 box.
     */
    public static boolean[] conflicts(int[] grid) {
        boolean[] bad = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            int v = grid[i];
            if (v == 0) continue;
            int r = i / 9, c = i % 9;
            for (int j = i + 1; j < SIZE; j++) {
                if (grid[j] != v) continue;
                int r2 = j / 9, c2 = j % 9;
                if (r == r2 || c == c2 || (r / 3 == r2 / 3 && c / 3 == c2 / 3)) {
                    bad[i] = true;
                    bad[j] = true;
                }
            }
        }
        return bad;
    }

    public static boolean hasConflicts(int[] grid) {
        for (boolean b : conflicts(grid)) if (b) return true;
        return false;
    }

    private static String describeFormatError(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return "Invalid character '" + c + "' at position " + (i + 1) + ". Only digits 0-9 are allowed.";
            }
        }
        return "Expected exactly 81 digits but found " + s.length() + ".";
    }
}
