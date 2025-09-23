package com.trailblazer.fabric.rendering;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Represents the different ways a path can be visualized by the client.
 */
public enum RenderMode {
    /**
     * Renders the path as a continuous trail of particles.
     */
    PARTICLE_TRAIL("Particle Trail", Formatting.GREEN),

    /**
     * Renders the path as markers spaced at regular intervals.
     */
    SPACED_MARKERS("Spaced Markers", Formatting.YELLOW),

    /**
     * Renders the path as arrows indicating direction at regular intervals.
     */
    DIRECTIONAL_ARROWS("Directional Arrows", Formatting.AQUA);

    private final String displayName;
    private final Formatting displayColor;

    RenderMode(String displayName, Formatting displayColor) {
        this.displayName = displayName;
        this.displayColor = displayColor;
    }

    /**
     * Cycles to the next render mode in the enum's declaration order.
     *
     * @return The next RenderMode.
     */
    public RenderMode next() {
        RenderMode[] values = values();
        int nextOrdinal = (this.ordinal() + 1) % values.length;
        return values[nextOrdinal];
    }

    /**
     * Gets the formatted display text for this mode, to be shown in chat.
     *
     * @return A Text component for display.
     */
    public Text getDisplayText() {
        return Text.literal(this.displayName).formatted(this.displayColor);
    }
}