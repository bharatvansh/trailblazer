package com.trailblazer.plugin.rendering;

import java.util.Optional;

/**
 * Represents the server-side rendering modes available as a fallback.
 * These now mirror the client-side modes.
 */
public enum RenderMode {
    /**
     * Renders the path as a dashed line using particles.
     * Server-side fallback for the client's quad-based dashed line.
     */
    DASHED_LINE,

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
            case "trail", "dashed", "dash", "dashed_line" -> Optional.of(DASHED_LINE);
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