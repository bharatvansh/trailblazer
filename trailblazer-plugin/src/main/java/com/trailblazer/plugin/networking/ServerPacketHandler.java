package com.trailblazer.plugin.networking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import com.trailblazer.plugin.TrailblazerPlugin;
import com.trailblazer.plugin.networking.payload.c2s.HandshakePayload;
import com.trailblazer.plugin.networking.payload.s2c.HideAllPathsPayload;
import com.trailblazer.plugin.networking.payload.s2c.LivePathUpdatePayload;
import com.trailblazer.plugin.networking.payload.s2c.PathDataSyncPayload;
import com.trailblazer.plugin.networking.payload.s2c.PathDeletedPayload;
import com.trailblazer.plugin.networking.payload.s2c.SharePathPayload;
import com.trailblazer.plugin.networking.payload.s2c.StopLivePathPayload;
import com.trailblazer.plugin.networking.payload.s2c.PathActionResultPayload;

public class ServerPacketHandler implements Listener, PluginMessageListener {

    private final TrailblazerPlugin plugin;
    private final Gson gson = new Gson();
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private static final String UPDATE_METADATA_CHANNEL = "trailblazer:update_path_metadata";
    private static final String SHARE_PATH_WITH_PLAYERS_CHANNEL = "trailblazer:share_path_with_players";

    private final PathDataManager dataManager;

    public ServerPacketHandler(TrailblazerPlugin plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPathDataManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PathDataSyncPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, HideAllPathsPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, LivePathUpdatePayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, StopLivePathPayload.CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SharePathPayload.CHANNEL_NAME);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PathDeletedPayload.CHANNEL_NAME);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PathActionResultPayload.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HandshakePayload.CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "trailblazer:delete_path", this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, UPDATE_METADATA_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SHARE_PATH_WITH_PLAYERS_CHANNEL, this);
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

        if (channel.equalsIgnoreCase("trailblazer:delete_path")) {
            try {
                UUID pathId;
                if (message.length == 16) {
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(message);
                    long msb = bb.getLong();
                    long lsb = bb.getLong();
                    pathId = new UUID(msb, lsb);
                } else {
                    String raw = new String(message, java.nio.charset.StandardCharsets.UTF_8).trim();
                    pathId = UUID.fromString(raw);
                }
                List<PathData> paths = dataManager.loadPaths(player.getUniqueId());
                boolean owned = paths.stream().anyMatch(p -> p.getPathId().equals(pathId) && p.getOwnerUUID().equals(player.getUniqueId()));
                if (owned) {
                    dataManager.deletePath(player.getUniqueId(), pathId);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sendPathDeleted(player, pathId);
                        sendActionResult(player, "delete", pathId, true, "Path deleted successfully.", null);
                    });
                } else {
                    sendActionResult(player, "delete", pathId, false, "You do not own this path.", null);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to process delete path payload from " + player.getName() + ": " + e.getMessage());
                sendActionResult(player, "delete", null, false, "An error occurred while deleting the path.", null);
            }
            return;
        }

        if (channel.equalsIgnoreCase(UPDATE_METADATA_CHANNEL)) {
            handleMetadataUpdate(player, message);
            return;
        }

        if (channel.equalsIgnoreCase(SHARE_PATH_WITH_PLAYERS_CHANNEL)) {
            handleSharePathWithPlayers(player, message);
            return;
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

    public void sendPathDeleted(Player player, UUID pathId) {
        if (!isModdedPlayer(player)) {
            return;
        }
        PathDeletedPayload payload = new PathDeletedPayload(pathId);
        player.sendPluginMessage(plugin, PathDeletedPayload.CHANNEL_NAME, payload.toBytes());
    }

    public void sendSharePath(Player targetPlayer, PathData pathData) {
        // The check for whether the player is modded is now handled in PathCommand.
        // This method is now only responsible for creating and sending the packet to modded clients.
        SharePathPayload payload = new SharePathPayload(pathData);
        targetPlayer.sendPluginMessage(plugin, SharePathPayload.CHANNEL_NAME, payload.toBytes());
    }

    private void sendActionResult(Player player, String action, UUID pathId, boolean success, String message, PathData updated) {
        if (!isModdedPlayer(player)) return;
        PathActionResultPayload payload = new PathActionResultPayload(action, pathId, success, message, updated);
        player.sendPluginMessage(plugin, PathActionResultPayload.CHANNEL, payload.toBytes());
    }

    private void handleSharePathWithPlayers(Player sender, byte[] message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            UUID pathId = new UUID(buffer.getLong(), buffer.getLong());
            int playerCount = readVarInt(buffer);
            List<UUID> playerIds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                playerIds.add(new UUID(buffer.getLong(), buffer.getLong()));
            }

            List<PathData> paths = dataManager.loadPaths(sender.getUniqueId());
            paths.stream()
                .filter(p -> p.getPathId().equals(pathId) && p.getOwnerUUID().equals(sender.getUniqueId()))
                .findFirst()
                .ifPresentOrElse(path -> {
                    path.getSharedWith().addAll(playerIds);
                    dataManager.savePath(path);
                    for (UUID targetPlayerId : playerIds) {
                        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerId);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            sendSharePath(targetPlayer, path);
                        }
                    }
                    sendActionResult(sender, "share", pathId, true, "Path shared successfully.", null);
                }, () -> {
                    sendActionResult(sender, "share", pathId, false, "You do not own this path.", null);
                });
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to process share path with players payload from " + sender.getName() + ": " + e.getMessage());
            sendActionResult(sender, "share", null, false, "An error occurred while sharing the path.", null);
        }
    }

    private void handleMetadataUpdate(Player player, byte[] message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            UUID pathId = new UUID(buffer.getLong(), buffer.getLong());
            int color = buffer.getInt();
            int nameLength = readVarInt(buffer);
            byte[] nameBytes = new byte[nameLength];
            buffer.get(nameBytes);
            String newName = new String(nameBytes, StandardCharsets.UTF_8);

            List<PathData> updatedPaths = dataManager.updateMetadata(player.getUniqueId(), pathId, newName, color);
            if (updatedPaths != null) {
                PathData updatedPath = updatedPaths.stream().filter(p -> p.getPathId().equals(pathId)).findFirst().orElse(null);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sendAllPathData(player, updatedPaths);
                    sendActionResult(player, "update_metadata", pathId, true, "Path updated successfully.", updatedPath);
                });
            } else {
                sendActionResult(player, "update_metadata", pathId, false, "Failed to update path. You may not be the owner or the name is taken.", null);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to process update metadata payload from " + player.getName() + ": " + e.getMessage());
            sendActionResult(player, "update_metadata", null, false, "An error occurred while updating the path.", null);
        }
    }

    private static int readVarInt(ByteBuffer buffer) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = buffer.get();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IllegalStateException("VarInt too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }
}