package com.trailblazer.plugin.networking;

import java.nio.charset.StandardCharsets;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ServerPacketHandler implements Listener, PluginMessageListener {

    private static final String S2C_SYNC_CHANNEL = "trailblazer:sync_path_data";
    private static final String S2C_HIDE_ALL_CHANNEL = "trailblazer:hide_all_paths";
    private static final String C2S_TOGGLE_CHANNEL = "trailblazer:toggle_recording";
    private static final String C2S_HANDSHAKE_CHANNEL = "trailblazer:handshake";

    private final TrailblazerPlugin plugin;
    private final Gson gson = new Gson();
    private final Set<UUID> moddedPlayers = new HashSet<>();

    private final PathRecordingManager recordingManager;
    private final PathDataManager dataManager;

    public ServerPacketHandler(TrailblazerPlugin plugin) {
        this.plugin = plugin;
        this.recordingManager = plugin.getPathRecordingManager();
        this.dataManager = plugin.getPathDataManager();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, S2C_SYNC_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, S2C_HIDE_ALL_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, C2S_TOGGLE_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, C2S_HANDSHAKE_CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (channel.equalsIgnoreCase(C2S_HANDSHAKE_CHANNEL)) {
            moddedPlayers.add(player.getUniqueId());
            TrailblazerPlugin.getPluginLogger().info("Successful handshake. Trailblazer client mod confirmed for player: " + player.getName());

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

        if (channel.equalsIgnoreCase(C2S_TOGGLE_CHANNEL)) {
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
                dataManager.savePath(player, newPath);
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
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] packetData = createVarIntPrefixedData(jsonBytes);
        player.sendPluginMessage(plugin, S2C_SYNC_CHANNEL, packetData);
    }

    private byte[] createVarIntPrefixedData(byte[] data) {
        int length = data.length;
        byte[] lengthBytes = encodeVarInt(length);
        byte[] result = new byte[lengthBytes.length + data.length];
        System.arraycopy(lengthBytes, 0, result, 0, lengthBytes.length);
        System.arraycopy(data, 0, result, lengthBytes.length, data.length);
        return result;
    }

    private byte[] encodeVarInt(int value) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((value & 0xFFFFFF80) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
        return out.toByteArray();
    }

    // The onPlayerJoin event is now only used for cleanup, not detection.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Optional: Ensure no lingering state if a player rejoins quickly.
        moddedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        moddedPlayers.remove(event.getPlayer().getUniqueId());
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
        // This packet has no data, so we send an empty byte array.
        player.sendPluginMessage(plugin, S2C_HIDE_ALL_CHANNEL, new byte[0]);
    }
}