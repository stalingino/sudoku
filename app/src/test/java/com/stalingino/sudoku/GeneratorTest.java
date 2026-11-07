package com.stalingino.sudoku;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class GeneratorTest {

    @Test
    public void fullGridIsValid() {
        int[] g = Generator.fullGrid(new Random(1));
        for (int v : g) assertTrue(v >= 1 && v <= 9);
        assertFalse(Sdk81.hasConflicts(g));
    }

    @Test
    public void generatedPuzzlesAreUniqueAndGraded() {
        for (Difficulty d : Difficulty.values()) {
            int[] p = Generator.generate(d, 42);
            assertEquals(d.label, 1, Solver.countSolutions(p, 2));
            int grade = Grader.grade(p);
            switch (d) {
                case EASY: case MEDIUM: assertEquals(d.label, Grader.SINGLES, grade); break;
                case HARD: assertEquals(d.label, Grader.INTERMEDIATE, grade); break;
                case EXPERT: assertEquals(d.label, Grader.ADVANCED, grade); break;
            }
        }
    }

    @Test
    public void sameSeedSamePuzzle() {
        assertArrayEquals(Generator.generate(Difficulty.MEDIUM, 2026), Generator.generate(Difficulty.MEDIUM, 2026));
    }

    @Test
    public void graderSolvesSdu3WithBasicTechniques() throws Exception {
        assertNotEquals(Grader.ADVANCED, Grader.grade(Sdk81.parse(PuzzleStore.DEFAULT_PUZZLE)));
    }

    @Test
    public void bundledLevelPacksAreValid() throws Exception {
        Set<String> all = new HashSet<>();
        for (Difficulty d : Difficulty.values()) {
            List<String> lines = Files.readAllLines(Path.of("src/main/assets", d.assetPath()));
            assertEquals(d.label, 50, lines.size());
            for (String s : lines) {
                assertTrue(Sdk81.isValidFormat(s));
                assertTrue("duplicate " + s, all.add(s));
                int[] p = Sdk81.parse(s);
                assertEquals(1, Solver.countSolutions(p, 2));
                int grade = Grader.grade(p);
                if (d == Difficulty.HARD) assertEquals(Grader.INTERMEDIATE, grade);
                else if (d == Difficulty.EXPERT) assertEquals(Grader.ADVANCED, grade);
                else assertEquals(Grader.SINGLES, grade);
            }
        }
    }

}
