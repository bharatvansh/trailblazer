package com.trailblazer.plugin;

import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.networking.ServerPacketHandler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active path recording sessions.
 * This class is responsible for tracking which players are recording and storing
 * the points of their paths in memory as they are being created.
 */
public class PathRecordingManager {

    private static final double MIN_DISTANCE_SQUARED = 2.0 * 2.0;
    private static final double TRAIL_Y_OFFSET = 0.5; // Offset to raise trail above ground level to prevent it from being hidden inside blocks

    private final Map<UUID, List<Vector3d>> recordingSessions = new ConcurrentHashMap<>();
    private final ServerPacketHandler packetHandler;

    public PathRecordingManager(ServerPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    /**
     * Starts a new recording session for a player.
     * @param player The player to start recording for.
     * @return true if a new session was started, false if they were already recording.
     */
    public boolean startRecording(Player player) {
        TrailblazerPlugin.getPluginLogger().info("Starting recording for player " + player.getName());
        if (isRecording(player)) {
            return false;
        }
        List<Vector3d> points = new ArrayList<>();
        recordingSessions.put(player.getUniqueId(), points);
        recordPlayerLocation(player, player.getLocation());
        if (packetHandler.isModdedPlayer(player)) {
            packetHandler.sendLivePathUpdate(player, points);
        }
        return true;
    }

    /**
     * Stops a recording session for a player and returns the recorded path.
     * @param player The player to stop recording for.
     * @return The list of recorded points, or null if they were not recording.
     */
    public List<Vector3d> stopRecording(Player player) {
        TrailblazerPlugin.getPluginLogger().info("Stopping recording for player " + player.getName());
        if (!isRecording(player)) {
            return null;
        }
        packetHandler.sendStopLivePath(player);
        return recordingSessions.remove(player.getUniqueId());
    }

    /**
     * Silently cancels a recording session without saving. Used for clean-up when a player disconnects.
     * @param player The player whose session to cancel.
     */
    public void cancelRecording(Player player) {
        TrailblazerPlugin.getPluginLogger().info("Cancelling recording for player " + player.getName());
        recordingSessions.remove(player.getUniqueId());
    }

    /**
     * Checks if a player is currently recording a path.
     * @param player The player to check.
     * @return true if the player is recording, false otherwise.
     */
    public boolean isRecording(Player player) {
        return recordingSessions.containsKey(player.getUniqueId());
    }

    /**
     * Records the player's current location if they have moved far enough from the last point.
     * @param player The player whose location to record.
     * @param newLocation The player's new location.
     */
    public void recordPlayerLocation(Player player, Location newLocation) {
        if (!isRecording(player)) {
            return;
        }

        List<Vector3d> points = recordingSessions.get(player.getUniqueId());
        Vector3d newPoint = new Vector3d(newLocation.getX(), newLocation.getY() + TRAIL_Y_OFFSET, newLocation.getZ());

        if (points.isEmpty()) {
            points.add(newPoint);
            return;
        }

        Vector3d lastPoint = points.get(points.size() - 1);

        double deltaX = lastPoint.getX() - newPoint.getX();
        double deltaY = lastPoint.getY() - newPoint.getY();
        double deltaZ = lastPoint.getZ() - newPoint.getZ();
        double distanceSquared = (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);

        if (distanceSquared >= MIN_DISTANCE_SQUARED) {
            points.add(newPoint);
            if (packetHandler.isModdedPlayer(player)) {
                packetHandler.sendLivePathUpdate(player, points);
            }
        }
    }
}