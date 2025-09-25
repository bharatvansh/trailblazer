package com.trailblazer.plugin.networking;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import com.google.gson.Gson;
import com.trailblazer.api.PathData;
import com.trailblazer.api.Vector3d;
import com.trailblazer.plugin.PathDataManager;
import com.trailblazer.plugin.PathRecordingManager;
import com.trailblazer.plugin.TrailblazerPlugin;
import com.trailblazer.plugin.networking.payload.c2s.HandshakePayload;
import com.trailblazer.plugin.networking.payload.c2s.ToggleRecordingPayload;
import com.trailblazer.plugin.networking.payload.s2c.HideAllPathsPayload;
import com.trailblazer.plugin.networking.payload.s2c.LivePathUpdatePayload;
import com.trailblazer.plugin.networking.payload.s2c.PathDataSyncPayload;
import com.trailblazer.plugin.networking.payload.s2c.SharePathPayload;
import com.trailblazer.plugin.networking.payload.s2c.StopLivePathPayload;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ServerPacketHandler implements Listener, PluginMessageListener {

    private final TrailblazerPlugin plugin;
    private final Gson gson = new Gson();
    private final Set<UUID> moddedPlayers = new HashSet<>();

    private PathRecordingManager recordingManager;
    private final PathDataManager dataManager;

    public ServerPacketHandler(TrailblazerPlugin plugin) {
        this.plugin = plugin;
        this.recordingManager = plugin.getPathRecordingManager();
        this.dataManager = plugin.getPathDataManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PathDataSyncPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, HideAllPathsPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, LivePathUpdatePayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, StopLivePathPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SharePathPayload.CHANNEL_NAME);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, ToggleRecordingPayload.CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HandshakePayload.CHANNEL, this);
    }

    public void setRecordingManager(PathRecordingManager recordingManager) {
        this.recordingManager = recordingManager;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        plugin.getLogger().info("Received plugin message on channel: " + channel + " from player: " + player.getName());

        if (channel.equalsIgnoreCase(HandshakePayload.CHANNEL)) {
            moddedPlayers.add(player.getUniqueId());
            plugin.getLogger().info("Received HandshakePayload from " + player.getName());

            // --- NEW LOGIC: Proactive Data Sync ---
            // Immediately send all saved paths to the client upon successful handshake.
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<PathData> allPaths = dataManager.loadPaths(player.getUniqueId());
                if (!allPaths.isEmpty()) {
                    // We must send the packet back on the main server thread.
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sendAllPathData(player, allPaths);
                        TrailblazerPlugin.getPluginLogger().info("Synced " + allPaths.size() + " existing path(s) to " + player.getName());
                    });
                }
            });
            // --- END NEW LOGIC ---
            return;
        }

        if (channel.equalsIgnoreCase(ToggleRecordingPayload.CHANNEL)) {
            plugin.getLogger().info("Received ToggleRecordingPayload from " + player.getName());
            // ... (The toggle logic remains unchanged)
            if (recordingManager.isRecording(player)) {
                List<Vector3d> recordedPoints = recordingManager.stopRecording(player);
                if (recordedPoints == null) return;
                if (recordedPoints.size() < 2) {
                    player.sendMessage(Component.text("Path too short to save.", NamedTextColor.YELLOW));
                    return;
                }
                String pathName = "Path-" + (dataManager.loadPaths(player.getUniqueId()).size() + 1);
                PathData newPath = new PathData(UUID.randomUUID(), pathName, player.getUniqueId(), player.getName(), System.currentTimeMillis(), player.getWorld().getName(), recordedPoints);
                dataManager.savePath(player.getUniqueId(), newPath);
                player.sendMessage(Component.text("Path recording stopped and saved as '", NamedTextColor.GREEN)
                    .append(Component.text(pathName, NamedTextColor.AQUA))
                    .append(Component.text("'.", NamedTextColor.GREEN)));
            } else {
                recordingManager.startRecording(player);
                player.sendMessage(Component.text("Path recording started.", NamedTextColor.GREEN));
            }
        }
    }

    // ... (onPlayerJoin, onPlayerQuit, isModdedPlayer remain unchanged) ...

    /**
     * Convenience method to send a single path. Wraps it in a list.
     * Used by the /path show command.
     * @param player The player to send data to.
     * @param path The single path to send.
     */
    public void sendPathData(Player player, PathData path) {
        sendAllPathData(player, Collections.singletonList(path));
    }

    /**
     * The core sending method. Serializes a list of paths to JSON and sends it to the client.
     * @param player The player to send data to.
     * @param paths The list of paths to send.
     */
    public void sendAllPathData(Player player, List<PathData> paths) {
        if (!isModdedPlayer(player) || paths.isEmpty()) {
            return;
        }

        String json = gson.toJson(paths);
        PathDataSyncPayload payload = new PathDataSyncPayload(json);
        player.sendPluginMessage(plugin, PathDataSyncPayload.CHANNEL, payload.toBytes());
    }

    // The onPlayerJoin event is now only used for cleanup, not detection.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Optional: Ensure no lingering state if a player rejoins quickly.
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        moddedPlayers.remove(player.getUniqueId());
        // Clean up any active recording session to prevent "ghost" recordings.
        if (recordingManager.isRecording(player)) {
            recordingManager.cancelRecording(player);
            TrailblazerPlugin.getPluginLogger().info("Cancelled active recording session for disconnected player: " + player.getName());
        }
    }

    public boolean isModdedPlayer(Player player) {
        return moddedPlayers.contains(player.getUniqueId());
    }

    /**
     * Sends a signal to the client to hide all currently visible paths.
     * @param player The player to send the signal to.
     */
    public void sendHideAllPaths(Player player) {
        if (!isModdedPlayer(player)) return;
        // This packet has no data, but the client expects to read at least one byte.
        player.sendPluginMessage(plugin, HideAllPathsPayload.CHANNEL, new HideAllPathsPayload().toBytes());
    }

    /**
     * Sends the current list of points for a path being recorded.
     * @param player The player to send the update to.
     * @param points The list of points to send.
     */
    public void sendLivePathUpdate(Player player, List<Vector3d> points) {
        if (!isModdedPlayer(player) || points == null) {
            return;
        }
        LivePathUpdatePayload payload = new LivePathUpdatePayload(points);
        player.sendPluginMessage(plugin, LivePathUpdatePayload.CHANNEL, payload.toBytes());
    }

    /**
     * Sends a signal to the client to stop rendering the live path.
     * @param player The player to send the signal to.
     */
    public void sendStopLivePath(Player player) {
        if (!isModdedPlayer(player)) {
            return;
        }
        StopLivePathPayload payload = new StopLivePathPayload();
        player.sendPluginMessage(plugin, StopLivePathPayload.CHANNEL, payload.toBytes());
    }

    public void sendSharePath(Player targetPlayer, PathData pathData) {
        // The check for whether the player is modded is now handled in PathCommand.
        // This method is now only responsible for creating and sending the packet to modded clients.
        SharePathPayload payload = new SharePathPayload(pathData);
        targetPlayer.sendPluginMessage(plugin, SharePathPayload.CHANNEL_NAME, payload.toBytes());
    }
}