package com.trailblazer.fabric.networking.payload.c2s;

import java.util.UUID;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeletePathPayload(UUID pathId) implements CustomPayload {
    public static final Id<DeletePathPayload> ID = new Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "delete_path"));
    public static final PacketCodec<RegistryByteBuf, DeletePathPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeUuid(value.pathId()),
        buf -> new DeletePathPayload(buf.readUuid())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
