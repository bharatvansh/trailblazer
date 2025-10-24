package com.trailblazer.plugin.networking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
    private static final String SAVE_PATH_CHANNEL = "trailblazer:save_path";
    private static final String ACTION_ACK_CHANNEL = "trailblazer:path_action_ack";
    private static final long RESEND_INTERVAL_TICKS = 40L;
    private static final long RESEND_INTERVAL_MS = 2000L;
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final PathDataManager dataManager;
    private final Map<UUID, ReliableMessageState> reliableStates = new ConcurrentHashMap<>();

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
    plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SAVE_PATH_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "trailblazer:delete_path", this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, UPDATE_METADATA_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, SHARE_PATH_WITH_PLAYERS_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, ACTION_ACK_CHANNEL, this);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::resendPendingActionResults, RESEND_INTERVAL_TICKS, RESEND_INTERVAL_TICKS);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (channel.equalsIgnoreCase(ACTION_ACK_CHANNEL)) {
            handleActionAck(player, message);
            return;
        }

        plugin.getLogger().info("Received plugin message on channel: " + channel + " from player: " + player.getName());

        if (channel.equalsIgnoreCase(HandshakePayload.CHANNEL)) {
            moddedPlayers.add(player.getUniqueId());
            plugin.getLogger().info("Received HandshakePayload from " + player.getName());

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                java.util.UUID worldUid = player.getWorld().getUID();
                List<PathData> allPaths = dataManager.loadPaths(worldUid, player.getUniqueId());
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
                java.util.UUID worldUid = player.getWorld().getUID();
                List<PathData> paths = dataManager.loadPaths(worldUid, player.getUniqueId());
                boolean owned = paths.stream().anyMatch(p -> p.getPathId().equals(pathId) && p.getOwnerUUID().equals(player.getUniqueId()));
                if (owned) {
                    boolean removed = dataManager.deletePath(worldUid, player.getUniqueId(), pathId);
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

        if (channel.equalsIgnoreCase(SAVE_PATH_CHANNEL)) {
            handleSaveRequest(player, message);
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
        UUID playerId = event.getPlayer().getUniqueId();
        moddedPlayers.remove(playerId);
        reliableStates.remove(playerId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        moddedPlayers.remove(playerId);
        reliableStates.remove(playerId);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline() || !isModdedPlayer(player)) {
            return;
        }
        // Immediately hide any client-rendered paths to avoid cross-world visuals lingering
        sendHideAllPaths(player);

        // Load and sync paths scoped to the new world's UUID
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID worldUid = player.getWorld().getUID();
            List<PathData> allPaths = dataManager.loadPaths(worldUid, player.getUniqueId());
            if (!allPaths.isEmpty()) {
                // Duplicate lineage suppression: if player owns origin, drop shared copies referencing same lineage
                Set<UUID> ownedOrigins = new HashSet<>();
                for (PathData p : allPaths) {
                    if (p.getOwnerUUID().equals(player.getUniqueId())) {
                        UUID origin = p.getOriginPathId() != null ? p.getOriginPathId() : p.getPathId();
                        ownedOrigins.add(origin);
                    }
                }
                allPaths.removeIf(p -> !p.getOwnerUUID().equals(player.getUniqueId()) && ownedOrigins.contains(p.getPathId()));
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> sendAllPathData(player, allPaths));
        });
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
        if (player == null || !player.isOnline() || !isModdedPlayer(player)) {
            return;
        }

        ReliableMessageState state = reliableStates.computeIfAbsent(player.getUniqueId(), id -> new ReliableMessageState());
        long sequence = state.nextSequence.getAndIncrement();
        PendingActionResult pending = new PendingActionResult(sequence, action, pathId, success, message, updated);
        state.pending.put(sequence, pending);
        dispatchPendingResult(player, state, pending);
    }

    private void dispatchPendingResult(Player player, ReliableMessageState state, PendingActionResult pending) {
        long now = System.currentTimeMillis();
        byte[] bytes = pending.toBytes(state.lastAck);
        player.sendPluginMessage(plugin, PathActionResultPayload.CHANNEL, bytes);
        pending.markDispatched(now);
    }

    private void resendPendingActionResults() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ReliableMessageState> entry : reliableStates.entrySet()) {
            Player target = plugin.getServer().getPlayer(entry.getKey());
            if (target == null || !target.isOnline() || !isModdedPlayer(target)) {
                continue;
            }

            ReliableMessageState state = entry.getValue();
            if (state.pending.isEmpty()) {
                continue;
            }

            for (PendingActionResult pending : new ArrayList<>(state.pending.values())) {
                if (now - pending.getLastSentAtMs() < RESEND_INTERVAL_MS) {
                    continue;
                }

                if (pending.getAttempts() >= MAX_RETRY_ATTEMPTS) {
                    state.pending.remove(pending.getSequence());
                    plugin.getLogger().warning("Dropping path action result " + pending.getSequence() + " for " + target.getName() + " after " + pending.getAttempts() + " attempts without acknowledgment.");
                    continue;
                }

                byte[] bytes = pending.toBytes(state.lastAck);
                target.sendPluginMessage(plugin, PathActionResultPayload.CHANNEL, bytes);
                pending.markDispatched(now);
            }

            if (state.pending.isEmpty()) {
                reliableStates.remove(entry.getKey(), state);
            }
        }
    }

    private void handleActionAck(Player player, byte[] message) {
        if (message == null || message.length < Long.BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(message);
        long acknowledged = buffer.getLong();
        if (acknowledged <= 0) {
            return;
        }

        ReliableMessageState state = reliableStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        state.lastAck = Math.max(state.lastAck, acknowledged);
        state.pending.entrySet().removeIf(entry -> entry.getKey() <= acknowledged);
        if (state.pending.isEmpty()) {
            reliableStates.remove(player.getUniqueId(), state);
        }
    }

    private static final class ReliableMessageState {
        private final AtomicLong nextSequence = new AtomicLong(1L);
        private final ConcurrentSkipListMap<Long, PendingActionResult> pending = new ConcurrentSkipListMap<>();
        private volatile long lastAck = 0L;
    }

    private static final class PendingActionResult {
        private final long sequence;
        private final String action;
        private final UUID pathId;
        private final boolean success;
        private final String message;
        private final PathData updated;
        private volatile long lastSentAtMs;
        private volatile int attempts;

        private PendingActionResult(long sequence, String action, UUID pathId, boolean success, String message, PathData updated) {
            this.sequence = sequence;
            this.action = action;
            this.pathId = pathId;
            this.success = success;
            this.message = message;
            this.updated = updated;
            this.lastSentAtMs = 0L;
            this.attempts = 0;
        }

        private byte[] toBytes(long acknowledgedSequence) {
            Long ackField = acknowledgedSequence > 0 ? acknowledgedSequence : null;
            PathActionResultPayload payload = new PathActionResultPayload(action, pathId, success, message, updated, sequence, ackField);
            return payload.toBytes();
        }

        private void markDispatched(long timestampMs) {
            this.attempts++;
            this.lastSentAtMs = timestampMs;
        }

        private long getSequence() {
            return sequence;
        }

        private int getAttempts() {
            return attempts;
        }

        private long getLastSentAtMs() {
            return lastSentAtMs;
        }
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

            java.util.UUID senderWorldUid = sender.getWorld().getUID();
            List<PathData> paths = dataManager.loadPaths(senderWorldUid, sender.getUniqueId());
            paths.stream()
                .filter(p -> p.getPathId().equals(pathId) && p.getOwnerUUID().equals(sender.getUniqueId()))
                .findFirst()
                .ifPresentOrElse(path -> {
                    boolean updated = false;
                    List<String> newlyShared = new ArrayList<>();
                    List<String> alreadyHad = new ArrayList<>();

                    for (UUID targetPlayerId : playerIds) {
                        String targetName = resolvePlayerName(targetPlayerId);
                        java.util.UUID targetWorldUid = null;
                        Player targetOnline = plugin.getServer().getPlayer(targetPlayerId);
                        if (targetOnline != null && targetOnline.isOnline()) {
                            try { targetWorldUid = targetOnline.getWorld().getUID(); } catch (Exception ignored) {}
                        }
                        if (targetWorldUid == null) { targetWorldUid = senderWorldUid; }
                        PathDataManager.SharedCopyResult result = dataManager.ensureSharedCopy(path, targetPlayerId, targetName, targetWorldUid);
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
                        dataManager.savePath(senderWorldUid, path);
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

            java.util.UUID worldUid = player.getWorld().getUID();
            List<PathData> updatedPaths = dataManager.updateMetadata(worldUid, player.getUniqueId(), pathId, newName, color);
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
            if (path == null || !PathDataManager.isValidPathData(path)) {
                throw new IllegalArgumentException("Received invalid shared path data");
            }
            if (path.getOriginPathId() == null || path.getOriginOwnerUUID() == null || path.getOriginOwnerName() == null) {
                path.setOrigin(path.getPathId(), sender.getUniqueId(), sender.getName());
            }

            // For both modded and unmodded recipients: ensure a shared copy exists.
            // If the recipient is modded and online, also deliver over the custom channel.
            List<String> newlyShared = new ArrayList<>();
            List<String> alreadyHad = new ArrayList<>();
            int moddedDelivered = 0;

            for (UUID targetId : targets) {
                String targetName = resolvePlayerName(targetId);

                // Determine world context: prefer target's current world if online, else sender's world
                java.util.UUID targetWorldUid = sender.getWorld().getUID();
                Player targetOnline = plugin.getServer().getPlayer(targetId);
                if (targetOnline != null && targetOnline.isOnline()) {
                    try { targetWorldUid = targetOnline.getWorld().getUID(); } catch (Exception ignored) {}
                }

                PathDataManager.SharedCopyResult result = dataManager.ensureSharedCopy(path, targetId, targetName, targetWorldUid);
                PathData sharedCopy = result.getPath();
                if (!result.wasCreated()) {
                    alreadyHad.add(targetName);
                } else {
                    newlyShared.add(targetName);
                }

                if (targetOnline != null && targetOnline.isOnline()) {
                    boolean targetIsModded = isModdedPlayer(targetOnline);
                    if (targetIsModded) {
                        // Deliver to modded clients via payload
                        sendSharePath(targetOnline, sharedCopy);
                        moddedDelivered++;
                    } else {
                        // Fallback: start server-side particle rendering and notify
                        plugin.getPathRendererManager().startRendering(targetOnline, sharedCopy);
                        targetOnline.sendMessage(Component.text(sender.getName() + " shared the path '" + sharedCopy.getPathName() + "' with you.", NamedTextColor.AQUA));
                        targetOnline.sendMessage(Component.text("It is now being displayed. Use '/path hide' to hide it or '/path view " + sharedCopy.getPathName() + "' to see it again.", NamedTextColor.GRAY));
                    }
                }
            }

            boolean success = !newlyShared.isEmpty() || moddedDelivered > 0;
            StringBuilder response = new StringBuilder();
            if (!newlyShared.isEmpty()) {
                response.append("Shared path with ").append(String.join(", ", newlyShared)).append('.');
            }
            if (!alreadyHad.isEmpty()) {
                if (response.length() > 0) response.append(' ');
                response.append(String.join(", ", alreadyHad)).append(" already had their own copy.");
            }
            if (response.length() == 0) {
                // Nobody received a new copy, but if some were online+modded we still delivered
                response.append(moddedDelivered > 0 ? ("Shared path with " + moddedDelivered + " player(s).") : "Selected players already have this path.");
            }

            sendActionResult(sender, "share", path.getPathId(), success, response.toString(), null);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to process share request payload from " + sender.getName() + ": " + ex.getMessage());
            sendActionResult(sender, "share", null, false, "An error occurred while sharing the path.", null);
        }
    }

    private void handleSaveRequest(Player sender, byte[] message) {
        try {
            // The incoming message for a save request is the raw JSON string
            String json = new String(message, StandardCharsets.UTF_8);
            PathData clientPath = gson.fromJson(json, PathData.class);
            if (clientPath == null || !PathDataManager.isValidPathData(clientPath)) {
                sendActionResult(sender, "save", null, false, "Invalid path data.", null);
                return;
            }

            // Server is authoritative: generate a new UUID for the persistent copy.
            UUID serverPathId = UUID.randomUUID();
            // The owner is always the player who sent the request.
            UUID ownerId = sender.getUniqueId();
            String ownerName = sender.getName();

            // Create the new authoritative path data.
            PathData serverCopy = new PathData(
                serverPathId,
                clientPath.getPathName(),
                ownerId,
                ownerName,
                System.currentTimeMillis(),
                clientPath.getDimension(),
                new ArrayList<>(clientPath.getPoints()),
                clientPath.getColorArgb()
            );

            // Preserve the original client-side ID as the origin ID for lineage tracking.
            serverCopy.setOrigin(clientPath.getPathId(), clientPath.getOwnerUUID(), clientPath.getOwnerName());

            // Persist the new server-authoritative copy.
            java.util.UUID worldUid = sender.getWorld().getUID();
            dataManager.savePath(worldUid, serverCopy);

            // Send a success result back to the client with the new, authoritative path data.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sendActionResult(sender, "save", serverCopy.getPathId(), true, "Path saved successfully.", serverCopy);
            });

        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to process save path payload from " + sender.getName() + ": " + ex.getMessage());
            sendActionResult(sender, "save", null, false, "An error occurred while saving the path.", null);
        }
    }

    private static int readVarInt(ByteBuffer buffer) {
        if (buffer.remaining() < 1) {
            throw new IllegalStateException("Buffer underflow reading VarInt");
        }
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