package com.trailblazer.fabric.rendering;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Path visualization modes.
 */
public enum RenderMode {
    DASHED_LINE("Dashed Line", Formatting.GREEN),
    SPACED_MARKERS("Spaced Markers", Formatting.YELLOW),
    DIRECTIONAL_ARROWS("Directional Arrows", Formatting.AQUA);

    private final String displayName;
    private final Formatting displayColor;

    RenderMode(String displayName, Formatting displayColor) {
        this.displayName = displayName;
        this.displayColor = displayColor;
    }

    public RenderMode next() {
        RenderMode[] values = values();
        int nextOrdinal = (this.ordinal() + 1) % values.length;
        return values[nextOrdinal];
    }

    public Text getDisplayText() {
        return Text.literal(this.displayName).formatted(this.displayColor);
    }
}