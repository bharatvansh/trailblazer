package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server payload requesting to stop recording a path on the server.
 */
public record StopRecordingPayload(boolean save) implements CustomPayload {
    public static final Id<StopRecordingPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "stop_recording_request"));
    
    public static final PacketCodec<RegistryByteBuf, StopRecordingPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBoolean(value.save),
        (buf) -> new StopRecordingPayload(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

