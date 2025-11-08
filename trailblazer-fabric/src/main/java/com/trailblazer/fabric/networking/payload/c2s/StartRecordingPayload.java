package com.trailblazer.fabric.networking.payload.c2s;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server payload requesting to start recording a path on the server.
 */
public record StartRecordingPayload(String pathName) implements CustomPayload {
    public static final Id<StartRecordingPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "start_recording_request"));
    
    public static final PacketCodec<RegistryByteBuf, StartRecordingPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            boolean hasName = value.pathName != null && !value.pathName.isEmpty();
            buf.writeBoolean(hasName);
            if (hasName) {
                buf.writeString(value.pathName);
            }
        },
        (buf) -> {
            boolean hasName = buf.readBoolean();
            String pathName = hasName ? buf.readString() : null;
            return new StartRecordingPayload(pathName);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

