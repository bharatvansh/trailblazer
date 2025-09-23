package com.trailblazer.fabric.networking.payload.s2c;

import java.nio.charset.StandardCharsets;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload carrying JSON representing a list of PathData entries.
 * This adapts older identifier-based custom packet to the new Fabric payload API (1.20.5+/1.21).
 */
public record PathDataSyncPayload(String json) implements CustomPayload {

    public static final Id<PathDataSyncPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "sync_path_data"));
    public static final PacketCodec<RegistryByteBuf, PathDataSyncPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.json),
        (buf) -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new PathDataSyncPayload(new String(bytes, StandardCharsets.UTF_8));
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
