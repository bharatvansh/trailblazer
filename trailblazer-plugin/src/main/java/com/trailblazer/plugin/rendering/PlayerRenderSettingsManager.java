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
     * Clears a player's settings when they log out.
     * @param player The player who is leaving.
     */
    public void onPlayerQuit(Player player) {
        playerRenderModes.remove(player.getUniqueId());
    }
}