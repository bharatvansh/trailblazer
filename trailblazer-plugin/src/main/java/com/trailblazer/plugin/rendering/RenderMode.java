package com.trailblazer.plugin.rendering;

import java.util.Optional;

/**
 * Represents the server-side rendering modes available as a fallback.
 * These now mirror the client-side modes.
 */
public enum RenderMode {
    /**
     * Renders the path as a continuous trail of particles.
     * NOTE: This is very performance-intensive on the server.
     */
    PARTICLE_TRAIL,

    /**
     * Renders particles at spaced intervals.
     */
    SPACED_MARKERS,

    /**
     * Renders arrows at spaced intervals to show direction.
     */
    DIRECTIONAL_ARROWS;

    public static Optional<RenderMode> fromString(String name) {
        for (RenderMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}