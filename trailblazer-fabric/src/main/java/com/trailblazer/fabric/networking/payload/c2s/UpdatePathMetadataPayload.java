package com.trailblazer.fabric.networking.payload.c2s;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.trailblazer.fabric.TrailblazerFabricClient;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UpdatePathMetadataPayload(UUID pathId, String name, int colorArgb) implements CustomPayload {
    public static final CustomPayload.Id<UpdatePathMetadataPayload> ID =
            new CustomPayload.Id<>(Identifier.of(TrailblazerFabricClient.MOD_ID, "update_path_metadata"));

    public static final PacketCodec<RegistryByteBuf, UpdatePathMetadataPayload> CODEC = PacketCodec.of(
            UpdatePathMetadataPayload::write,
            UpdatePathMetadataPayload::read
    );

    private static void write(UpdatePathMetadataPayload value, RegistryByteBuf buf) {
        buf.writeUuid(value.pathId());
        buf.writeInt(value.colorArgb());
        byte[] nameBytes = value.name().getBytes(StandardCharsets.UTF_8);
        buf.writeVarInt(nameBytes.length);
        buf.writeBytes(nameBytes);
    }

    private static UpdatePathMetadataPayload read(RegistryByteBuf buf) {
        UUID id = buf.readUuid();
        int color = buf.readInt();
        int length = buf.readVarInt();
        byte[] nameBytes = new byte[length];
        buf.readBytes(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        return new UpdatePathMetadataPayload(id, name, color);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
