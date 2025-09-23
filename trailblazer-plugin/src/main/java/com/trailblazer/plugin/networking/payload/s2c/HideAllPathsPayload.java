package com.trailblazer.plugin.networking.payload.s2c;

import org.bukkit.NamespacedKey;

/**
 * Represents the S2C hide all paths payload.
 */
public class HideAllPathsPayload {
    public static final NamespacedKey ID = new NamespacedKey("trailblazer", "hide_all_paths");
    public static final String CHANNEL = ID.toString();

    public byte[] toBytes() {
        return new byte[1];
    }
}
