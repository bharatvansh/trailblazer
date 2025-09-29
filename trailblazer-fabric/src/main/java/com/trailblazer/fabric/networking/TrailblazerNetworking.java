package com.trailblazer.fabric.networking;

import com.trailblazer.fabric.networking.payload.c2s.DeletePathPayload;
import com.trailblazer.fabric.networking.payload.c2s.HandshakePayload;
import com.trailblazer.fabric.networking.payload.c2s.SharePathRequestPayload;
import com.trailblazer.fabric.networking.payload.c2s.UpdatePathMetadataPayload;
import com.trailblazer.fabric.networking.payload.s2c.HideAllPathsPayload;
import com.trailblazer.fabric.networking.payload.s2c.LivePathUpdatePayload;
import com.trailblazer.fabric.networking.payload.s2c.PathDataSyncPayload;
import com.trailblazer.fabric.networking.payload.s2c.PathDeletedPayload;
import com.trailblazer.fabric.networking.payload.s2c.SharedPathPayload;
import com.trailblazer.fabric.networking.payload.s2c.StopLivePathPayload;
import com.trailblazer.fabric.networking.payload.s2c.PathActionResultPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Contains identifiers for all custom network packets used by the mod.
 */
public class TrailblazerNetworking {

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(PathDataSyncPayload.ID, PathDataSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HideAllPathsPayload.ID, HideAllPathsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LivePathUpdatePayload.ID, LivePathUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StopLivePathPayload.ID, StopLivePathPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SharedPathPayload.ID, SharedPathPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PathDeletedPayload.ID, PathDeletedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PathActionResultPayload.ID, PathActionResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeletePathPayload.ID, DeletePathPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakePayload.ID, HandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SharePathRequestPayload.ID, SharePathRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdatePathMetadataPayload.ID, UpdatePathMetadataPayload.CODEC);
    }
}
