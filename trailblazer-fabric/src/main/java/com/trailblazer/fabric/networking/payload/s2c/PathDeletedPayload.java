package com.trailblazer.fabric.networking.payload.s2c;

import com.trailblazer.fabric.TrailblazerFabricClient;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record PathDeletedPayload(UUID pathId) implements CustomPayload {
    public static final CustomPayload.Id<PathDeletedPayload> ID = new CustomPayload.Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "path_deleted"));
    public static final PacketCodec<RegistryByteBuf, PathDeletedPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeUuid(value.pathId),
            buf -> new PathDeletedPayload(buf.readUuid())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
