package com.trailblazer.plugin.networking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ServerPacketHandler implements Listener, PluginMessageListener {

    private final TrailblazerPlugin plugin;
    private final Gson gson = new Gson();
    private final Set<UUID> moddedPlayers = new HashSet<>();
    private static final String UPDATE_METADATA_CHANNEL = "trailblazer:update_path_metadata";
    private static final String SHARE_PATH_WITH_PLAYERS_CHANNEL = "trailblazer:share_path_with_players";
    private static final String SHARE_REQUEST_CHANNEL = "trailblazer:share_request";

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
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SHARE_REQUEST_CHANNEL, this);
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

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<PathData> allPaths = dataManager.loadPaths(player.getUniqueId());
                if (!allPaths.isEmpty()) {
                    java.util.Set<java.util.UUID> ownedOrigins = new java.util.HashSet<>();
                    for (PathData p : allPaths) {
                        if (p.getOwnerUUID().equals(player.getUniqueId())) {
                            java.util.UUID origin = p.getOriginPathId() != null ? p.getOriginPathId() : p.getPathId();
                            ownedOrigins.add(origin);
                        }
                    }
                    allPaths.removeIf(p -> !p.getOwnerUUID().equals(player.getUniqueId()) && ownedOrigins.contains(p.getPathId()));
                }
                if (!allPaths.isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sendAllPathData(player, allPaths);
                        TrailblazerPlugin.getPluginLogger().info("Synced " + allPaths.size() + " existing path(s) to " + player.getName());
                    });
                }
            });
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
                    boolean removed = dataManager.deletePath(player.getUniqueId(), pathId);
                    if (removed) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            sendPathDeleted(player, pathId);
                            sendActionResult(player, "delete", pathId, true, "Path deleted successfully.", null);
                        });
                    } else {
                        sendActionResult(player, "delete", pathId, false, "Failed to delete path.", null);
                    }
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

        if (channel.equalsIgnoreCase(SHARE_REQUEST_CHANNEL)) {
            handleShareRequest(player, message);
            return;
        }
    }

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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        moddedPlayers.remove(player.getUniqueId());
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
                    boolean updated = false;
                    List<String> newlyShared = new ArrayList<>();
                    List<String> alreadyHad = new ArrayList<>();

                    for (UUID targetPlayerId : playerIds) {
                        String targetName = resolvePlayerName(targetPlayerId);
                        PathDataManager.SharedCopyResult result = dataManager.ensureSharedCopy(path, targetPlayerId, targetName);
                        if (!result.wasCreated()) {
                            alreadyHad.add(targetName);
                            continue;
                        }

                        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerId);
                        boolean targetIsModded = targetPlayer != null && targetPlayer.isOnline() && isModdedPlayer(targetPlayer);
                        if (!targetIsModded) { 
                            if (!path.getSharedWith().contains(targetPlayerId)) {
                                path.getSharedWith().add(targetPlayerId);
                                updated = true;
                            }
                        }

                        PathData sharedCopy = result.getPath();
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            if (targetIsModded) {
                                sendSharePath(targetPlayer, sharedCopy);
                            } else {
                                plugin.getPathRendererManager().startRendering(targetPlayer, sharedCopy);
                                targetPlayer.sendMessage(Component.text(sender.getName() + " shared the path '" + sharedCopy.getPathName() + "' with you.", NamedTextColor.AQUA));
                                targetPlayer.sendMessage(Component.text("It is now being displayed. Use '/path hide' to hide it or '/path view " + sharedCopy.getPathName() + "' to see it again.", NamedTextColor.GRAY));
                            }
                        }
                        newlyShared.add(targetName);
                    }

                    if (updated) {
                        dataManager.savePath(path);
                    }

                    boolean success = !newlyShared.isEmpty();
                    StringBuilder response = new StringBuilder();
                    if (success) {
                        response.append("Shared path with ").append(String.join(", ", newlyShared)).append('.');
                    }
                    if (!alreadyHad.isEmpty()) {
                        if (response.length() > 0) {
                            response.append(' ');
                        }
                        response.append(String.join(", ", alreadyHad)).append(" already had their own copy.");
                    }
                    if (response.length() == 0) {
                        response.append("Selected players already have this path.");
                    }

                    sendActionResult(sender, "share", pathId, success, response.toString(), null);
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

    private void handleShareRequest(Player sender, byte[] message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            int targetCount = readVarInt(buffer);
            List<UUID> targets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                targets.add(new UUID(buffer.getLong(), buffer.getLong()));
            }
            String json = readString(buffer);
            PathData path = gson.fromJson(json, PathData.class);
            if (path == null) {
                throw new IllegalArgumentException("Received empty shared path data");
            }
            if (path.getOriginPathId() == null || path.getOriginOwnerUUID() == null || path.getOriginOwnerName() == null) {
                path.setOrigin(path.getPathId(), sender.getUniqueId(), sender.getName());
            }

            int delivered = 0;
            for (UUID targetId : targets) {
                Player target = plugin.getServer().getPlayer(targetId);
                if (target != null && target.isOnline() && isModdedPlayer(target)) {
                    SharePathPayload payload = new SharePathPayload(path);
                    target.sendPluginMessage(plugin, SharePathPayload.CHANNEL_NAME, payload.toBytes());
                    delivered++;
                }
            }

            if (delivered > 0) {
                sendActionResult(sender, "share", path.getPathId(), true,
                        "Shared path with " + delivered + " player(s).", null);
            } else {
                sendActionResult(sender, "share", path.getPathId(), false,
                        "No target players were online with Trailblazer.", null);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to process share request payload from " + sender.getName() + ": " + ex.getMessage());
            sendActionResult(sender, "share", null, false, "An error occurred while sharing the path.", null);
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

    private String readString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0 || length > 1_048_576) {
            throw new IllegalStateException("Invalid string length: " + length);
        }
        byte[] data = new byte[length];
        buffer.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private String resolvePlayerName(UUID playerId) {
        Player online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(playerId);
        if (offline != null && offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return "Player";
    }
}