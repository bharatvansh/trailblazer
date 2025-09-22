package com.trailblazer.plugin.listeners;

import com.trailblazer.plugin.PathRecordingManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listens for player movement to handle path recording.
 */
public class PlayerMoveListener implements Listener {

    private final PathRecordingManager recordingManager;

    public PlayerMoveListener(PathRecordingManager recordingManager) {
        this.recordingManager = recordingManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Optimization: If the player isn't recording, we do nothing.
        // The manager handles this check, but we can do it here to be extra efficient.
        if (!recordingManager.isRecording(player)) {
            return;
        }

        // Optimization: Check if the player has actually moved from one block to another.
        // This avoids running our logic for tiny head movements.
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Tell the manager to process this new location.
        recordingManager.recordPlayerLocation(player, player.getLocation());
    }
}