package com.trailblazer.plugin.networking.payload.c2s;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class ToggleRecordingPayload implements PluginMessageListener {
    // This is the critical part that was missing.
    // It must exactly match the identifier used on the client: "trailblazer:toggle_recording"
    public static final String CHANNEL = "trailblazer:toggle_recording";

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        // This is a C2S payload, the logic is handled in ServerPacketHandler
    }
}
