package com.trailblazer.fabric.networking;

import com.trailblazer.fabric.networking.payload.c2s.HandshakePayload;
import com.trailblazer.fabric.networking.payload.c2s.ToggleRecordingPayload;
import com.trailblazer.fabric.networking.payload.s2c.HideAllPathsPayload;
import com.trailblazer.fabric.networking.payload.s2c.PathDataSyncPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Contains identifiers for all custom network packets used by the mod.
 */
public class TrailblazerNetworking {

    // Register all custom payload codecs (call from client + server init as needed)
    public static void registerPayloadTypes() {
        // Server-to-Client
        PayloadTypeRegistry.playS2C().register(PathDataSyncPayload.ID, PathDataSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HideAllPathsPayload.ID, HideAllPathsPayload.CODEC);
        // Client-to-Server
        PayloadTypeRegistry.playC2S().register(ToggleRecordingPayload.ID, ToggleRecordingPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakePayload.ID, HandshakePayload.CODEC);
    }
}
