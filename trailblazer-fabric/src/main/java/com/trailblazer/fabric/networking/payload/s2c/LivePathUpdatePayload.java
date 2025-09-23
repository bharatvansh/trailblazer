package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

public record LivePathUpdatePayload(String json) implements CustomPayload {
    public static final Id<LivePathUpdatePayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "live_path_update"));
    public static final PacketCodec<RegistryByteBuf, LivePathUpdatePayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.json),
        (buf) -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new LivePathUpdatePayload(new String(bytes, StandardCharsets.UTF_8));
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}