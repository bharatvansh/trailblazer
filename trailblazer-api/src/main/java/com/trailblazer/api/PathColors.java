package com.trailblazer.api;

import java.util.*;

/**
 * Shared palette + helpers for assigning and parsing per-path colors.
 * Colors are stored as 0xAARRGGBB ints. Alpha is always forced to 0xFF.
 */
public final class PathColors {

    /** Predefined palette (9 colors). */
    private static final List<Integer> PALETTE = List.of(
            0xFFFF5555, // RED
            0xFFFFAA00, // ORANGE
            0xFFFFFF55, // YELLOW
            0xFF55FF55, // GREEN
            0xFF55FFFF, // CYAN
            0xFF5555FF, // BLUE
            0xFFAA55FF, // PURPLE
            0xFFFF55FF, // PINK
            0xFFFFFFFF  // WHITE
    );

    /** Name -> Color map */
    private static final Map<String, Integer> NAME_TO_COLOR = Map.ofEntries(
            Map.entry("red", PALETTE.get(0)),
            Map.entry("orange", PALETTE.get(1)),
            Map.entry("yellow", PALETTE.get(2)),
            Map.entry("green", PALETTE.get(3)),
            Map.entry("cyan", PALETTE.get(4)),
            Map.entry("blue", PALETTE.get(5)),
            Map.entry("purple", PALETTE.get(6)),
            Map.entry("pink", PALETTE.get(7)),
            Map.entry("white", PALETTE.get(8))
    );

    private static final Map<Integer, String> COLOR_TO_NAME;
    static {
        Map<Integer, String> reverse = new HashMap<>();
        NAME_TO_COLOR.forEach((k,v) -> reverse.put(v, k));
        COLOR_TO_NAME = Collections.unmodifiableMap(reverse);
    }

    private PathColors() {}

    /** Returns unmodifiable palette list */
    public static List<Integer> palette() {
        return PALETTE;
    }

    /** Deterministic assignment based on path UUID. */
    public static int assignColorFor(UUID pathId) {
        if (pathId == null) return PALETTE.get(0);
        int idx = Math.abs(pathId.hashCode()) % PALETTE.size();
        return PALETTE.get(idx);
    }

    /** Parse a user supplied color. Accepts predefined names or #RRGGBB / #AARRGGBB. Returns empty if invalid. */
    public static Optional<Integer> parse(String input) {
        if (input == null || input.isEmpty()) return Optional.empty();
        String lower = input.toLowerCase(Locale.ROOT);
        if (NAME_TO_COLOR.containsKey(lower)) {
            return Optional.of(NAME_TO_COLOR.get(lower));
        }
        if (lower.startsWith("#")) {
            String hex = lower.substring(1);
            if (hex.length() == 6 || hex.length() == 8) {
                try {
                    long val = Long.parseLong(hex, 16);
                    if (hex.length() == 6) {
                        val |= 0xFF000000L; // force opaque
                    }
                    return Optional.of((int) val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return Optional.empty();
    }

    /** Returns a friendly name if the color matches a predefined entry, else hex string #RRGGBB. */
    public static String nameOrHex(int argb) {
        if (COLOR_TO_NAME.containsKey(argb)) return COLOR_TO_NAME.get(argb);
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }
}
