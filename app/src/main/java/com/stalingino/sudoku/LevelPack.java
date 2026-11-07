package com.stalingino.sudoku;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads the bundled level packs: assets/levels/&lt;difficulty&gt;.txt, one SDK81 string per line. */
public final class LevelPack {
    private static final Map<Difficulty, List<String>> CACHE = new EnumMap<>(Difficulty.class);

    private LevelPack() {}

    public static synchronized List<String> get(Context context, Difficulty d) {
        List<String> list = CACHE.get(d);
        if (list != null) return list;
        list = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open(d.assetPath()), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = in.readLine()) != null) {
                String s = Sdk81.normalize(line);
                if (s.isEmpty() || s.startsWith("#")) continue;
                if (Sdk81.isValidFormat(s)) list.add(s);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Missing level pack " + d.assetPath(), e);
        }
        list = Collections.unmodifiableList(list);
        CACHE.put(d, list);
        return list;
    }
}
