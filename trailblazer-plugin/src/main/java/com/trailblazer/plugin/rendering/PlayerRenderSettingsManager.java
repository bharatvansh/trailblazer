package com.trailblazer.plugin.rendering;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Manages the selected server-side render mode for each player.
 */
public class PlayerRenderSettingsManager {

    private final Map<UUID, RenderMode> playerRenderModes = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerMarkerSpacing = new ConcurrentHashMap<>();

    /** Default spacing used when a player hasn't chosen one. Matches server fallback default (3.0). */
    private static final double DEFAULT_MARKER_SPACING = 3.0;

    /**
     * Gets the current render mode for a given player.
     *
     * @param player The player.
     * @return The player's selected RenderMode, or CONTINUOUS if they haven't chosen one.
     */
    public RenderMode getRenderMode(Player player) {
        return playerRenderModes.getOrDefault(player.getUniqueId(), RenderMode.DASHED_LINE);
    }

    /**
     * Sets the render mode for a given player.
     *
     * @param player The player.
     * @param mode   The RenderMode to set.
     */
    public void setRenderMode(Player player, RenderMode mode) {
        playerRenderModes.put(player.getUniqueId(), mode);
    }

    /**
     * Gets the configured marker spacing for a player (in blocks).
     * Falls back to a sensible default when not set.
     */
    public double getMarkerSpacing(Player player) {
        return playerMarkerSpacing.getOrDefault(player.getUniqueId(), DEFAULT_MARKER_SPACING);
    }

    /**
     * Sets the marker spacing for a player.
     */
    public void setMarkerSpacing(Player player, double spacing) {
        if (spacing <= 0) return; // ignore invalid values
        playerMarkerSpacing.put(player.getUniqueId(), spacing);
    }

    /**
     * Clears a player's settings when they log out.
     * @param player The player who is leaving.
     */
    public void onPlayerQuit(Player player) {
        playerRenderModes.remove(player.getUniqueId());
        playerMarkerSpacing.remove(player.getUniqueId());
    }
}