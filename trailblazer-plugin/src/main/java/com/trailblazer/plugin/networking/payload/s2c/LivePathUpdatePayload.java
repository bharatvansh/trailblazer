package com.trailblazer.plugin.networking.payload.s2c;

import com.google.gson.Gson;
import com.trailblazer.api.Vector3d;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Payload to send a list of points for a live-updating path to the client.
 */
public class LivePathUpdatePayload implements PluginMessageListener {
    public static final String CHANNEL = "trailblazer:live_path_update";
    private static final Gson GSON = new Gson();

    private final List<Vector3d> points;

    public LivePathUpdatePayload(List<Vector3d> points) {
        this.points = points;
    }

    public byte[] toBytes() {
        String json = GSON.toJson(points);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // This is a S2C payload, so we don't expect to receive it on the server.
    }
}