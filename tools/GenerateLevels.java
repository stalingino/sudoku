import com.stalingino.sudoku.Difficulty;
import com.stalingino.sudoku.Generator;
import com.stalingino.sudoku.Grader;
import com.stalingino.sudoku.Sdk81;
import com.stalingino.sudoku.Solver;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regenerates app/src/main/assets/levels/*.txt (one SDK81 string per line).
 *
 * Run from the repo root with tools/generate-levels.sh. Output is deterministic.
 */
public class GenerateLevels {
    static final int PER_PACK = 50;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "app/src/main/assets/levels");
        Files.createDirectories(out);
        Set<String> seen = new HashSet<>();
        for (Difficulty d : Difficulty.values()) {
            long t0 = System.currentTimeMillis();
            List<int[]> list = new ArrayList<>();
            long seed = 1_000_003L * (d.ordinal() + 1);
            while (list.size() < PER_PACK) {
                int[] p = Generator.generate(d, seed++);
                String s = Sdk81.serialize(p);
                if (!ok(d, p) || !seen.add(s)) continue;
                list.add(p);
            }
            // Gentle ramp inside a pack: more clues and fewer advanced steps first.
            list.sort(Comparator.<int[]>comparingInt(p -> -Sdk81.countGivens(p))
                    .thenComparingInt(Grader::intermediateSteps));
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(
                    out.resolve(d.name().toLowerCase() + ".txt"), StandardCharsets.US_ASCII))) {
                for (int[] p : list) w.println(Sdk81.serialize(p));
            }
            int min = 81, max = 0;
            for (int[] p : list) { min = Math.min(min, Sdk81.countGivens(p)); max = Math.max(max, Sdk81.countGivens(p)); }
            System.out.printf("%-6s %d puzzles, givens %d-%d, %d ms%n", d.label, list.size(), min, max,
                    System.currentTimeMillis() - t0);
        }
    }

    static boolean ok(Difficulty d, int[] p) {
        if (Solver.countSolutions(p, 2) != 1) return false;
        int g = Grader.grade(p);
        switch (d) {
            case EASY: case MEDIUM: return g == Grader.SINGLES;
            case HARD: return g == Grader.INTERMEDIATE;
            default: return g == Grader.ADVANCED;
        }
    }
}
