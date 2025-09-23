package com.trailblazer.plugin.networking.payload.s2c;

import org.bukkit.NamespacedKey;

import java.nio.charset.StandardCharsets;

/**
 * Represents the S2C path data sync payload.
 */
public class PathDataSyncPayload {
    public static final NamespacedKey ID = new NamespacedKey("trailblazer", "sync_path_data");
    public static final String CHANNEL = ID.toString();

    private final String json;

    public PathDataSyncPayload(String json) {
        this.json = json;
    }

    public byte[] toBytes() {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
