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

    // Simple codec: length-prefixed UTF-8 string.
    public static final PacketCodec<RegistryByteBuf, PathDataSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public PathDataSyncPayload decode(RegistryByteBuf buf) {
            int len = buf.readableBytes();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new PathDataSyncPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(RegistryByteBuf buf, PathDataSyncPayload value) {
            byte[] bytes = value.json().getBytes(StandardCharsets.UTF_8);
            buf.writeVarInt(bytes.length);
            buf.writeBytes(bytes);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
