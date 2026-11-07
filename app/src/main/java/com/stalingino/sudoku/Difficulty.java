package com.stalingino.sudoku;

/** Level packs. Each pack is an asset file of SDK81 strings, one per line. */
public enum Difficulty {
    EASY("Easy", "E"),
    MEDIUM("Medium", "M"),
    HARD("Hard", "H"),
    EXPERT("Expert", "X");

    public final String label;
    public final String code;

    Difficulty(String label, String code) {
        this.label = label;
        this.code = code;
    }

    public String assetPath() {
        return "levels/" + name().toLowerCase(java.util.Locale.ROOT) + ".txt";
    }

    public static Difficulty fromCode(String code) {
        for (Difficulty d : values()) if (d.code.equals(code) || d.name().equals(code)) return d;
        return null;
    }
}
