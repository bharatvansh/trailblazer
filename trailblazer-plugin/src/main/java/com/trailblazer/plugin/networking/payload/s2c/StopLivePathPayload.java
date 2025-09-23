package com.trailblazer.plugin.networking.payload.s2c;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * A simple signal payload to tell the client to stop rendering the live path.
 */
public class StopLivePathPayload implements PluginMessageListener {
    public static final String CHANNEL = "trailblazer:stop_live_path";

    public byte[] toBytes() {
        return new byte[0]; // No data needed, the channel name is the signal.
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // This is a S2C payload, so we don't expect to receive it on the server.
    }
}