package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A signal payload sent from the client to the server to request a toggle
 * of the path recording state. This payload carries no data.
 */
public record ToggleRecordingPayload() implements CustomPayload {

    public static final Id<ToggleRecordingPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "toggle_recording"));

    // A codec for a payload with no data. It does nothing on encode and returns a new instance on decode.
    public static final PacketCodec<RegistryByteBuf, ToggleRecordingPayload> CODEC = PacketCodec.unit(new ToggleRecordingPayload());


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


