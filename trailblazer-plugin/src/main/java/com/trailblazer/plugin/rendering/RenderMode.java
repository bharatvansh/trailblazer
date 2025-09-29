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
        if (name == null) return Optional.empty();
        String n = name.trim().toLowerCase();
        // Accept common aliases for backward compatibility and client-friendly inputs
        return switch (n) {
            case "trail", "particle", "particles", "particle_trail" -> Optional.of(PARTICLE_TRAIL);
            case "markers", "marker", "spaced_markers" -> Optional.of(SPACED_MARKERS);
            case "arrows", "arrow", "directional_arrows" -> Optional.of(DIRECTIONAL_ARROWS);
            default -> {
                // Fallback to matching enum name
                Optional<RenderMode> found = Optional.empty();
                for (RenderMode mode : values()) {
                    if (mode.name().equalsIgnoreCase(name)) {
                        found = Optional.of(mode);
                        break;
                    }
                }
                yield found;
            }
        };
    }
}