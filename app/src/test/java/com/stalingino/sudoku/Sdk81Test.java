package com.stalingino.sudoku;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class Sdk81Test {
    private static final String SDU3 =
            "000013400080006950650000000960201000100070002000304016000000079025800040019760000";

    @Test
    public void parsesRowMajor() throws Exception {
        int[] g = Sdk81.parse(SDU3);
        assertEquals(81, g.length);
        assertEquals(1, g[4]);          // row 0, col 4
        assertEquals(8, g[1 * 9 + 1]);  // row 1, col 1
        assertEquals(0, g[80]);
        assertEquals(SDU3, Sdk81.serialize(g));
    }

    @Test
    public void ignoresWhitespace() throws Exception {
        String spaced = "000 013 400 080 006 950 650 000 000\n960 201 000 100 070 002 000 304 016\n"
                + "000 000 079 025 800 040 019 760 000";
        assertArrayEquals(Sdk81.parse(SDU3), Sdk81.parse(spaced));
    }

    @Test(expected = Sdk81.FormatException.class)
    public void rejectsWrongLength() throws Exception {
        Sdk81.parse(SDU3.substring(1));
    }

    @Test(expected = Sdk81.FormatException.class)
    public void rejectsNonDigits() throws Exception {
        Sdk81.parse(SDU3.substring(1) + ".");
    }

    @Test
    public void parsesMany() throws Exception {
        List<String> list = Sdk81.parseMany(SDU3 + "\n" + SDU3);
        assertEquals(2, list.size());
    }

    @Test
    public void detectsConflicts() throws Exception {
        int[] g = Sdk81.parse("11" + SDU3.substring(2));
        assertTrue(Sdk81.hasConflicts(g));
        assertFalse(Sdk81.hasConflicts(Sdk81.parse(SDU3)));
    }

    @Test
    public void solvesSdu3Uniquely() throws Exception {
        int[] g = Sdk81.parse(SDU3);
        assertEquals(1, Solver.countSolutions(g, 2));
        int[] s = Solver.solve(g);
        assertNotNull(s);
        assertFalse(Sdk81.hasConflicts(s));
        for (int i = 0; i < 81; i++) {
            assertTrue(s[i] >= 1 && s[i] <= 9);
            if (g[i] != 0) assertEquals(g[i], s[i]);
        }
    }

    @Test
    public void emptyGridHasManySolutions() {
        assertEquals(2, Solver.countSolutions(new int[81], 2));
    }

    @Test
    public void notesRoundTrip() {
        int[] masks = new int[81];
        masks[0] = (1 << 1) | (1 << 9);
        masks[80] = 1 << 5;
        assertArrayEquals(masks, PuzzleRecord.decodeNotes(PuzzleRecord.encodeNotes(masks)));
        assertEquals("", PuzzleRecord.encodeNotes(new int[81]));
    }
}
